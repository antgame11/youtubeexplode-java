package nowplaying.web;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SyncServerTest {
    @TempDir Path tmp;

    final ObjectMapper mapper = new ObjectMapper();
    final HttpClient http = HttpClient.newHttpClient();
    Fakes.Mutable source;
    TrackLibrary library;
    Poller poller;
    SyncServer server;
    Path audio;
    String base;

    @BeforeEach
    void start() throws Exception {
        byte[] bytes = new byte[1000];
        for (int i = 0; i < bytes.length; i++) bytes[i] = (byte) i;
        audio = Files.write(tmp.resolve("a.m4a"), bytes);

        source = new Fakes.Mutable();
        library = new TrackLibrary(Fakes.files(Map.of("A", audio)), 1);
        poller = new Poller(source, library, 50);
        server = new SyncServer(poller, library, 0, "127.0.0.1", 0);
        poller.start();
        server.start();
        base = "http://127.0.0.1:" + server.port();
    }

    @AfterEach
    void stop() {
        server.close();
        poller.close();
        library.shutdown();
    }

    private JsonNode now() throws Exception {
        return mapper.readTree(http.send(HttpRequest.newBuilder(URI.create(base + "/api/now")).build(),
                HttpResponse.BodyHandlers.ofString()).body());
    }

    private JsonNode awaitAudio(String status) throws Exception {
        for (int i = 0; i < 100; i++) {
            JsonNode n = now();
            if (n.has("audio") && status.equals(n.get("audio").get("status").asText())) return n;
            Thread.sleep(50);
        }
        fail("audio never became " + status);
        return null;
    }

    @Test
    void nothingPlaying() throws Exception {
        Thread.sleep(150);
        JsonNode n = now();
        assertFalse(n.get("playing").asBoolean());
        assertTrue(n.get("track").isNull());
    }

    @Test
    void describesTrack() throws Exception {
        source.now = Fakes.track("A", "Song A", 200_000, true, 10_000);
        JsonNode n = awaitAudio("ready");
        assertEquals("Song A", n.get("track").get("title").asText());
        assertEquals("Artist A", n.get("track").get("artists").get(0).asText());
        assertEquals("/audio/A", n.get("audio").get("url").asText());
        assertEquals("Song A (YT)", n.get("audio").get("match").asText());
        assertTrue(n.get("playing").asBoolean());
    }

    @Test
    void extrapolatesProgressBetweenPolls() throws Exception {
        // A slow poller: after the first poll the server must extrapolate on its own
        Fakes.Mutable slowSource = new Fakes.Mutable();
        slowSource.now = Fakes.track("A", "Song A", 200_000, true, 10_000);
        try (Poller slow = new Poller(slowSource, library, 60_000);
             SyncServer slowServer = new SyncServer(slow, library, 0, "127.0.0.1", 0)) {
            slow.start();
            slowServer.start();
            Thread.sleep(300);

            long p1 = slowServer.describeNow(System.currentTimeMillis()).get("progressMs").asLong();
            assertTrue(p1 >= 10_000 && p1 < 11_500, "progress " + p1);
            Thread.sleep(600);
            long p2 = slowServer.describeNow(System.currentTimeMillis()).get("progressMs").asLong();
            assertTrue(p2 - p1 >= 550 && p2 - p1 < 900, "advanced " + (p2 - p1));

            // The configured offset compensates for Spotify's reporting lag
            try (SyncServer shifted = new SyncServer(slow, library, 750, "127.0.0.1", 0)) {
                long p3 = shifted.describeNow(System.currentTimeMillis()).get("progressMs").asLong();
                assertTrue(p3 - p2 >= 750, "offset applied: " + (p3 - p2));
            }
        }
    }

    @Test
    void pausedDoesNotAdvanceAndProgressIsClamped() throws Exception {
        source.now = Fakes.track("A", "Song A", 200_000, false, 42_000);
        JsonNode n = awaitAudio("ready");
        Thread.sleep(300);
        assertEquals(42_000, now().get("progressMs").asLong());
        assertFalse(n.get("playing").asBoolean());

        source.now = Fakes.track("A", "Song A", 5_000, true, 4_990);
        Thread.sleep(400);
        assertEquals(5_000, now().get("progressMs").asLong());
    }

    @Test
    void failedResolutionIsReported() throws Exception {
        source.now = Fakes.track("ZZZ", "No audio", 100_000, true, 0);
        JsonNode n = awaitAudio("failed");
        assertTrue(n.get("audio").get("error").asText().contains("no audio"));
    }

    @Test
    void prefetchesNextTrack() throws Exception {
        source.now = Fakes.track("A", "Song A", 100_000, true, 0);
        source.next = java.util.List.of(Fakes.track("B", "Song B", 100_000, false, 0));
        awaitAudio("ready");
        for (int i = 0; i < 100 && library.peek("B") == null; i++) Thread.sleep(20);
        assertNotNull(library.peek("B"), "next track should have been requested");
    }

    @Test
    void serveAudioWithRanges() throws Exception {
        source.now = Fakes.track("A", "Song A", 100_000, true, 0);
        awaitAudio("ready");
        byte[] all = Files.readAllBytes(audio);

        var full = http.send(HttpRequest.newBuilder(URI.create(base + "/audio/A")).build(), HttpResponse.BodyHandlers.ofByteArray());
        assertEquals(200, full.statusCode());
        assertEquals("audio/mp4", full.headers().firstValue("Content-Type").orElse(""));
        assertEquals("bytes", full.headers().firstValue("Accept-Ranges").orElse(""));
        assertArrayEquals(all, full.body());

        var part = http.send(HttpRequest.newBuilder(URI.create(base + "/audio/A")).header("Range", "bytes=100-199").build(),
                HttpResponse.BodyHandlers.ofByteArray());
        assertEquals(206, part.statusCode());
        assertEquals("bytes 100-199/" + all.length, part.headers().firstValue("Content-Range").orElse(""));
        assertArrayEquals(java.util.Arrays.copyOfRange(all, 100, 200), part.body());

        var tail = http.send(HttpRequest.newBuilder(URI.create(base + "/audio/A")).header("Range", "bytes=-10").build(),
                HttpResponse.BodyHandlers.ofByteArray());
        assertEquals(206, tail.statusCode());
        assertArrayEquals(java.util.Arrays.copyOfRange(all, all.length - 10, all.length), tail.body());

        var bad = http.send(HttpRequest.newBuilder(URI.create(base + "/audio/A")).header("Range", "bytes=99999-").build(),
                HttpResponse.BodyHandlers.ofByteArray());
        assertEquals(416, bad.statusCode());

        assertEquals(404, http.send(HttpRequest.newBuilder(URI.create(base + "/audio/nope")).build(),
                HttpResponse.BodyHandlers.discarding()).statusCode());
        assertEquals(404, http.send(HttpRequest.newBuilder(URI.create(base + "/audio/../etc/passwd")).build(),
                HttpResponse.BodyHandlers.discarding()).statusCode());
    }

    @Test
    void servesThePage() throws Exception {
        var res = http.send(HttpRequest.newBuilder(URI.create(base + "/")).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, res.statusCode());
        assertTrue(res.body().contains("Listen along"));
    }

    @Test
    void rangeParsing() {
        assertNull(SyncServer.parseRange(null, 100));
        assertNull(SyncServer.parseRange("items=0-5", 100));
        assertNull(SyncServer.parseRange("bytes=0-5,10-20", 100));
        assertArrayEquals(new long[] {0, 99}, SyncServer.parseRange("bytes=0-", 100));
        assertArrayEquals(new long[] {10, 99}, SyncServer.parseRange("bytes=10-5000", 100));
        assertArrayEquals(new long[] {90, 99}, SyncServer.parseRange("bytes=-10", 100));
        assertArrayEquals(new long[] {0, 99}, SyncServer.parseRange("bytes=-500", 100));
        assertSame(SyncServer.INVALID, SyncServer.parseRange("bytes=100-", 100));
        assertSame(SyncServer.INVALID, SyncServer.parseRange("bytes=50-10", 100));
    }

    @Test
    void serves_svg_card_uncached() throws Exception {
        source.now = Fakes.track("A", "Song A", 200_000, true, 30_000);
        awaitAudio("ready");

        var res = http.send(HttpRequest.newBuilder(URI.create(base + "/now.svg?theme=dark")).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, res.statusCode());
        assertEquals("image/svg+xml; charset=utf-8", res.headers().firstValue("Content-Type").orElse(""));
        assertTrue(res.headers().firstValue("Cache-Control").orElse("").contains("no-cache"));
        assertTrue(res.body().contains("Song A"));
        assertTrue(res.body().contains("NOW PLAYING"));
        assertFalse(res.body().contains("prefers-color-scheme"));
    }

    @Test
    void serves_embed_widget() throws Exception {
        var res = http.send(HttpRequest.newBuilder(URI.create(base + "/embed")).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, res.statusCode());
        assertTrue(res.body().contains("/api/now"));
        assertFalse(res.body().contains("Listen along"));
    }

    @Test
    void cardOnlyModeServesNoAudio() throws Exception {
        Fakes.Mutable src = new Fakes.Mutable();
        src.now = Fakes.track("A", "Song A", 200_000, true, 1_000);
        try (Poller cardPoller = new Poller(src, null, 50);
             SyncServer card = new SyncServer(cardPoller, null, 0, "127.0.0.1", 0)) {
            cardPoller.start();
            card.start();
            String cardBase = "http://127.0.0.1:" + card.port();
            Thread.sleep(300);

            JsonNode n = mapper.readTree(http.send(HttpRequest.newBuilder(URI.create(cardBase + "/api/now")).build(),
                    HttpResponse.BodyHandlers.ofString()).body());
            assertEquals("Song A", n.get("track").get("title").asText());
            assertFalse(n.has("audio"));

            assertEquals(404, http.send(HttpRequest.newBuilder(URI.create(cardBase + "/audio/A")).build(),
                    HttpResponse.BodyHandlers.discarding()).statusCode());
            assertTrue(http.send(HttpRequest.newBuilder(URI.create(cardBase + "/now.svg")).build(),
                    HttpResponse.BodyHandlers.ofString()).body().contains("Song A"));
            // The root is the embeddable card, not the listen-along player
            assertFalse(http.send(HttpRequest.newBuilder(URI.create(cardBase + "/")).build(),
                    HttpResponse.BodyHandlers.ofString()).body().contains("Listen along"));
        }
    }
}

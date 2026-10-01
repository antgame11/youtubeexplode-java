package nowplaying;

import static org.junit.jupiter.api.Assertions.*;

import com.sun.net.httpserver.HttpServer;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Runs SpotifyAuth/SpotifyClient against a local fake of the Spotify endpoints. */
class SpotifyClientTest {
    @TempDir Path tmp;

    HttpServer server;
    String base;
    final List<String> tokenRequests = new ArrayList<>();
    final List<String> authHeaders = new ArrayList<>();
    final Deque<int[]> playerStatuses = new ArrayDeque<>(); // {status}
    String playerBody = "";
    int tokenCounter;

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0);
        server.createContext("/api/token", ex -> {
            tokenRequests.add(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] body = ("{\"access_token\":\"AT" + (++tokenCounter) + "\",\"expires_in\":3600,\"token_type\":\"Bearer\"}")
                    .getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(200, body.length);
            ex.getResponseBody().write(body);
            ex.close();
        });
        server.createContext("/v1/me/player/currently-playing", ex -> {
            authHeaders.add(ex.getRequestHeaders().getFirst("Authorization"));
            int status = playerStatuses.isEmpty() ? 200 : playerStatuses.poll()[0];
            byte[] body = status == 200 ? playerBody.getBytes(StandardCharsets.UTF_8) : new byte[0];
            ex.sendResponseHeaders(status, status == 204 ? -1 : body.length);
            if (status != 204) ex.getResponseBody().write(body);
            ex.close();
        });
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private SpotifyClient client() throws Exception {
        Path tokenFile = tmp.resolve("spotify.json");
        Files.writeString(tokenFile, "{\"client_id\":\"cid\",\"refresh_token\":\"RT\"}");
        HttpClient http = HttpClient.newHttpClient();
        SpotifyAuth auth = new SpotifyAuth("cid", 8888, tokenFile, base + "/authorize", base + "/api/token", http);
        return new SpotifyClient(auth, base + "/v1", http);
    }

    private static final String TRACK = """
            {"is_playing": true, "currently_playing_type": "track",
             "item": {"id": "abc", "name": "Runaway", "duration_ms": 548000,
                      "artists": [{"name": "Kanye West"}, {"name": "Pusha T"}],
                      "album": {"name": "MBDTF"}}}""";

    @Test
    void readsCurrentlyPlayingUsingRefreshToken() throws Exception {
        playerBody = TRACK;
        NowPlaying np = client().currentlyPlaying().orElseThrow();

        assertEquals("Runaway", np.title());
        assertEquals(List.of("Kanye West", "Pusha T"), np.artists());
        assertEquals("MBDTF", np.album());
        assertEquals(548_000, np.durationMs());
        assertEquals("abc", np.trackId());
        assertEquals(List.of("Bearer AT1"), authHeaders);
        assertTrue(tokenRequests.get(0).contains("grant_type=refresh_token"));
        assertTrue(tokenRequests.get(0).contains("refresh_token=RT"));
        assertTrue(tokenRequests.get(0).contains("client_id=cid"));
    }

    @Test
    void reusesAccessTokenAcrossCalls() throws Exception {
        playerBody = TRACK;
        SpotifyClient c = client();
        c.currentlyPlaying();
        c.currentlyPlaying();
        assertEquals(1, tokenRequests.size());
    }

    @Test
    void nothingPlayingIs204() throws Exception {
        playerStatuses.add(new int[] {204});
        assertTrue(client().currentlyPlaying().isEmpty());
    }

    @Test
    void refreshesTokenAndRetriesOn401() throws Exception {
        playerStatuses.add(new int[] {401});
        playerBody = TRACK;
        assertTrue(client().currentlyPlaying().isPresent());
        assertEquals(List.of("Bearer AT1", "Bearer AT2"), authHeaders);
        assertEquals(2, tokenRequests.size());
    }

    @Test
    void podcastsAndAdsAreIgnored() throws Exception {
        assertTrue(SpotifyClient.parse("{\"currently_playing_type\":\"episode\",\"item\":{\"id\":\"x\",\"name\":\"Ep\"}}").isEmpty());
        assertTrue(SpotifyClient.parse("{\"currently_playing_type\":\"ad\",\"item\":null}").isEmpty());
    }

    @Test
    void persistsRotatedRefreshToken() throws Exception {
        server.removeContext("/api/token");
        server.createContext("/api/token", ex -> {
            byte[] body = "{\"access_token\":\"A\",\"expires_in\":3600,\"refresh_token\":\"NEW\"}".getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(200, body.length);
            ex.getResponseBody().write(body);
            ex.close();
        });
        playerBody = TRACK;
        client().currentlyPlaying();
        assertTrue(Files.readString(tmp.resolve("spotify.json")).contains("NEW"));
    }
}

package nowplaying.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.Executors;
import nowplaying.NowPlaying;

/**
 * Serves the web page, a JSON endpoint describing the synced playback state, and the audio files
 * (with HTTP Range support so browsers can seek). Also serves a compact embeddable widget
 * ({@code /embed}) and a GitHub-profile-friendly SVG card ({@code /now.svg}).
 *
 * <p>With a null library the server is in "card-only" mode: no audio is resolved or served, which is
 * what you want when exposing it publicly just to show what you are listening to.
 */
public final class SyncServer implements AutoCloseable {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Poller poller;
    private final TrackLibrary library;
    private final long offsetMs;
    private final HttpServer server;
    private final ImageCache images = new ImageCache();

    public SyncServer(Poller poller, TrackLibrary library, long offsetMs, String host, int port) throws IOException {
        this.poller = poller;
        this.library = library;
        this.offsetMs = offsetMs;
        this.server = HttpServer.create(new InetSocketAddress(host, port), 0);
        this.server.setExecutor(Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "http");
            t.setDaemon(true);
            return t;
        }));
        server.createContext("/", this::handleStatic);
        server.createContext("/api/now", this::handleNow);
        server.createContext("/now.svg", this::handleSvg);
        if (library != null) server.createContext("/audio/", this::handleAudio);
    }

    public void start() {
        server.start();
    }

    public int port() {
        return server.getAddress().getPort();
    }

    @Override
    public void close() {
        server.stop(0);
    }

    // ---- Static page ----

    private void handleStatic(HttpExchange ex) throws IOException {
        try (ex) {
            String path = ex.getRequestURI().getPath();
            if (path.equals("/favicon.ico")) {
                ex.sendResponseHeaders(204, -1);
                return;
            }
            String page;
            if (path.equals("/embed") || path.equals("/embed.html")) page = "embed.html";
            else if (path.equals("/") || path.equals("/index.html")) page = library == null ? "embed.html" : "index.html";
            else {
                ex.sendResponseHeaders(404, -1);
                return;
            }
            try (InputStream in = SyncServer.class.getResourceAsStream("/web/" + page)) {
                if (in == null) {
                    ex.sendResponseHeaders(500, -1);
                    return;
                }
                byte[] body = in.readAllBytes();
                ex.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
                ex.getResponseHeaders().add("Cache-Control", "no-cache");
                ex.sendResponseHeaders(200, body.length);
                ex.getResponseBody().write(body);
            }
        }
    }

    // ---- /api/now ----

    /** Position within the track at {@code now}: Spotify's last report, extrapolated, plus the sync offset. */
    private long progressAt(Poller.Snapshot snap, long now) {
        NowPlaying t = snap.track();
        long progress = t.progressMs();
        if (t.isPlaying()) progress += (now - snap.fetchedAtMs()) + offsetMs;
        return Math.max(0, Math.min(progress, t.durationMs()));
    }

    ObjectNode describeNow(long now) {
        Poller.Snapshot snap = poller.snapshot();
        ObjectNode root = MAPPER.createObjectNode();
        root.put("serverTime", now);
        if (snap.error() != null) root.put("error", snap.error());

        NowPlaying t = snap.track();
        if (t == null) {
            root.put("playing", false);
            root.putNull("track");
            return root;
        }

        root.put("playing", t.isPlaying());

        // Spotify only tells us the position as of the last poll, so extrapolate to "now"
        root.put("progressMs", progressAt(snap, now));

        ObjectNode track = root.putObject("track");
        track.put("id", t.trackId());
        track.put("title", t.title());
        track.putPOJO("artists", t.artists());
        track.put("album", t.album());
        track.put("durationMs", t.durationMs());
        track.put("imageUrl", t.imageUrl());

        if (library == null) return root; // card-only: no audio information

        ObjectNode audio = root.putObject("audio");
        TrackLibrary.Entry entry = t.trackId() == null ? null : library.peek(t.trackId());
        if (entry == null) {
            audio.put("status", "pending");
        } else {
            audio.put("status", entry.status().name().toLowerCase());
            if (entry.status() == TrackLibrary.Status.READY) {
                audio.put("url", "/audio/" + t.trackId());
                audio.put("match", entry.resolved().matchedTitle());
            } else if (entry.status() == TrackLibrary.Status.FAILED) {
                audio.put("error", entry.error());
            }
        }
        return root;
    }

    private void handleNow(HttpExchange ex) throws IOException {
        try (ex) {
            byte[] body = MAPPER.writeValueAsBytes(describeNow(System.currentTimeMillis()));
            ex.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
            ex.getResponseHeaders().add("Cache-Control", "no-store");
            ex.sendResponseHeaders(200, body.length);
            ex.getResponseBody().write(body);
        }
    }

    // ---- /now.svg ----

    String renderSvg(long now, NowPlayingSvg.Theme theme) {
        Poller.Snapshot snap = poller.snapshot();
        NowPlaying t = snap.track();
        if (t == null) return NowPlayingSvg.render(null, 0, null, theme);
        return NowPlayingSvg.render(t, progressAt(snap, now), images.dataUri(t.thumbUrl()), theme);
    }

    private void handleSvg(HttpExchange ex) throws IOException {
        try (ex) {
            String query = ex.getRequestURI().getRawQuery();
            String theme = null;
            if (query != null) {
                for (String pair : query.split("&")) {
                    if (pair.startsWith("theme=")) theme = pair.substring("theme=".length());
                }
            }
            byte[] body = renderSvg(System.currentTimeMillis(), NowPlayingSvg.Theme.parse(theme)).getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "image/svg+xml; charset=utf-8");
            // The animation starts from "now" when the image loads, so a stale copy would be wrong:
            // tell every cache (including GitHub's image proxy) to revalidate on each view.
            ex.getResponseHeaders().add("Cache-Control", "no-cache, no-store, max-age=0, must-revalidate");
            ex.getResponseHeaders().add("Pragma", "no-cache");
            ex.getResponseHeaders().add("Expires", "0");
            ex.sendResponseHeaders(200, body.length);
            ex.getResponseBody().write(body);
        }
    }

    // ---- /audio/{spotifyTrackId} ----

    private void handleAudio(HttpExchange ex) throws IOException {
        try (ex) {
            String id = ex.getRequestURI().getPath().substring("/audio/".length());
            TrackLibrary.Entry entry = library.peek(id);
            if (entry == null || entry.status() != TrackLibrary.Status.READY) {
                ex.sendResponseHeaders(404, -1);
                return;
            }

            Path file = entry.resolved().file();
            long size = Files.size(file);
            ex.getResponseHeaders().add("Content-Type", entry.resolved().mimeType());
            ex.getResponseHeaders().add("Accept-Ranges", "bytes");
            ex.getResponseHeaders().add("Cache-Control", "no-cache");

            long[] range = parseRange(ex.getRequestHeaders().getFirst("Range"), size);
            boolean head = ex.getRequestMethod().equalsIgnoreCase("HEAD");

            if (range == INVALID) {
                ex.getResponseHeaders().add("Content-Range", "bytes */" + size);
                ex.sendResponseHeaders(416, -1);
                return;
            }

            long start = range == null ? 0 : range[0];
            long end = range == null ? size - 1 : range[1];
            long length = end - start + 1;

            if (range != null) ex.getResponseHeaders().add("Content-Range", "bytes " + start + "-" + end + "/" + size);
            ex.sendResponseHeaders(range == null ? 200 : 206, head ? -1 : length);
            if (head) return;

            try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ);
                 OutputStream out = ex.getResponseBody()) {
                channel.position(start);
                java.io.InputStream in = Channels.newInputStream(channel);
                byte[] buffer = new byte[64 * 1024];
                long remaining = length;
                while (remaining > 0) {
                    int n = in.read(buffer, 0, (int) Math.min(buffer.length, remaining));
                    if (n < 0) break;
                    out.write(buffer, 0, n);
                    remaining -= n;
                }
            } catch (IOException clientDisconnected) {
                // Browsers abort range requests all the time when seeking
            }
        }
    }

    static final long[] INVALID = new long[0];

    /**
     * Parses a single-range {@code Range} header. Returns null if there is no (usable) header, which
     * means "send everything", or {@link #INVALID} if the range is not satisfiable.
     */
    static long[] parseRange(String header, long size) {
        if (header == null || !header.startsWith("bytes=") || header.contains(",")) return null;
        String spec = header.substring("bytes=".length()).strip();
        int dash = spec.indexOf('-');
        if (dash < 0) return null;

        try {
            String a = spec.substring(0, dash);
            String b = spec.substring(dash + 1);
            long start;
            long end;
            if (a.isEmpty()) {
                // Suffix range: the last N bytes
                long n = Long.parseLong(b);
                if (n <= 0) return INVALID;
                start = Math.max(0, size - n);
                end = size - 1;
            } else {
                start = Long.parseLong(a);
                end = b.isEmpty() ? size - 1 : Math.min(Long.parseLong(b), size - 1);
            }
            if (start >= size || start > end) return INVALID;
            return new long[] {start, end};
        } catch (NumberFormatException e) {
            return null;
        }
    }
}

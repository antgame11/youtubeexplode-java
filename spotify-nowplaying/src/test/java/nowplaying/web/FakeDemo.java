package nowplaying.web;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import nowplaying.NowPlaying;

/**
 * Runs the real web server against a scripted fake Spotify, for trying the page in a browser:
 * alternating 15s tracks (a low tone and a high tone) that start at known times.
 * Usage: FakeDemo [port]
 */
public final class FakeDemo {
    public static void main(String[] args) throws Exception {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 8099;
        Path dir = Files.createTempDirectory("fakedemo");
        Path low = Fakes.tone(dir, "low", 330, 60);
        Path high = Fakes.tone(dir, "high", 880, 60);
        long t0 = System.currentTimeMillis() - 4_000; // already 4s into the first track

        long length = 15_000;
        NowPlayingSource source = new NowPlayingSource() {
            private NowPlaying at(long elapsed) {
                long index = elapsed / length;
                String id = index % 2 == 0 ? "LOW" + index : "HIGH" + index;
                return new NowPlaying(id, (index % 2 == 0 ? "Low tone #" : "High tone #") + index, List.of("Test Artist"),
                        "Test Album", length, true, elapsed % length, null);
            }

            @Override
            public Optional<NowPlaying> current() {
                return Optional.of(at(System.currentTimeMillis() - t0));
            }

            @Override
            public List<NowPlaying> upNext() {
                return List.of(at(System.currentTimeMillis() - t0 + length));
            }
        };

        AudioResolver resolver = track -> new AudioResolver.Resolved(
                track.trackId().startsWith("LOW") ? low : high, "audio/mp4", track.title(), "x");

        TrackLibrary library = new TrackLibrary(resolver, 1);
        Poller poller = new Poller(source, library, 1000);
        SyncServer server = new SyncServer(poller, library, 0, "127.0.0.1", port);
        poller.start();
        server.start();
        System.out.println("FakeDemo on http://127.0.0.1:" + server.port() + "/");
        Thread.currentThread().join();
    }
}

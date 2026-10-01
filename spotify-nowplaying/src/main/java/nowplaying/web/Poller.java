package nowplaying.web;

import java.util.List;
import java.util.Optional;
import nowplaying.NowPlaying;

/** Polls the source in the background and keeps the latest snapshot; kicks off audio resolution. */
public final class Poller implements AutoCloseable {
    /** What the source said, and when we asked. */
    public record Snapshot(NowPlaying track, long fetchedAtMs, String error) {}

    private final NowPlayingSource source;
    private final TrackLibrary library;
    private final long intervalMs;
    private final Thread thread;
    private volatile Snapshot snapshot = new Snapshot(null, 0, null);
    private volatile boolean running = true;
    private String lastPrefetchedFor;

    public Poller(NowPlayingSource source, TrackLibrary library, long intervalMs) {
        this.source = source;
        this.library = library;
        this.intervalMs = intervalMs;
        this.thread = new Thread(this::loop, "spotify-poller");
        this.thread.setDaemon(true);
    }

    public void start() {
        thread.start();
    }

    public Snapshot snapshot() {
        return snapshot;
    }

    private void loop() {
        while (running) {
            try {
                Optional<NowPlaying> current = source.current();
                long now = System.currentTimeMillis();
                snapshot = new Snapshot(current.orElse(null), now, null);

                if (current.isPresent() && current.get().trackId() != null) {
                    NowPlaying track = current.get();
                    library.request(track);
                    prefetchNext(track);
                }
            } catch (InterruptedException e) {
                return;
            } catch (Exception e) {
                Snapshot old = snapshot;
                snapshot = new Snapshot(old.track(), old.fetchedAtMs(), e.getMessage() != null ? e.getMessage() : e.toString());
                System.err.println("[spotify] poll failed: " + snapshot.error());
            }

            try {
                Thread.sleep(intervalMs);
            } catch (InterruptedException e) {
                return;
            }
        }
    }

    /** Once per track, start resolving the next queued track so it is ready when Spotify moves on. */
    private void prefetchNext(NowPlaying current) {
        if (current.trackId().equals(lastPrefetchedFor)) return;
        lastPrefetchedFor = current.trackId();
        try {
            List<NowPlaying> next = source.upNext();
            if (!next.isEmpty() && next.get(0).trackId() != null) {
                System.out.println("[audio] prefetching next: " + next.get(0));
                library.request(next.get(0));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            System.err.println("[spotify] queue lookup failed: " + e.getMessage());
        }
    }

    @Override
    public void close() {
        running = false;
        thread.interrupt();
    }
}

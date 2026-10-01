package nowplaying.web;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import nowplaying.NowPlaying;

/** Resolves each Spotify track to an audio file once, in the background, and remembers the outcome. */
public final class TrackLibrary {
    public enum Status { PENDING, READY, FAILED }

    /** Outcome for one track. Fields are written by the worker thread and read by request threads. */
    public static final class Entry {
        private volatile Status status = Status.PENDING;
        private volatile AudioResolver.Resolved resolved;
        private volatile String error;
        private volatile long failedAt;

        public Status status() { return status; }
        public AudioResolver.Resolved resolved() { return resolved; }
        public String error() { return error; }
    }

    private static final long RETRY_AFTER_MS = 30_000;

    private final AudioResolver resolver;
    private final ExecutorService executor;
    private final Map<String, Entry> entries = new ConcurrentHashMap<>();

    public TrackLibrary(AudioResolver resolver, int threads) {
        this.resolver = resolver;
        this.executor = Executors.newFixedThreadPool(threads, r -> {
            Thread t = new Thread(r, "resolver");
            t.setDaemon(true);
            return t;
        });
    }

    /** Returns the entry for the track, starting (or retrying a failed) resolution if needed. */
    public Entry request(NowPlaying track) {
        Entry entry = entries.compute(track.trackId(), (id, existing) -> {
            if (existing == null) return start(track);
            if (existing.status == Status.FAILED && System.currentTimeMillis() - existing.failedAt > RETRY_AFTER_MS) {
                return start(track);
            }
            return existing;
        });
        return entry;
    }

    public Entry peek(String trackId) {
        return entries.get(trackId);
    }

    private Entry start(NowPlaying track) {
        Entry entry = new Entry();
        executor.submit(() -> {
            try {
                System.out.println("[audio] resolving " + track);
                entry.resolved = resolver.resolve(track);
                entry.status = Status.READY;
                System.out.println("[audio] ready: " + track + " -> " + entry.resolved.file().getFileName());
            } catch (Exception e) {
                entry.error = e.getMessage() != null ? e.getMessage() : e.toString();
                entry.failedAt = System.currentTimeMillis();
                entry.status = Status.FAILED;
                System.err.println("[audio] failed: " + track + ": " + entry.error);
            }
        });
        return entry;
    }

    public void shutdown() {
        executor.shutdownNow();
    }
}

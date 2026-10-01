package nowplaying.web;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import nowplaying.NowPlaying;

/** Test doubles: a scripted "Spotify" and a resolver that serves pre-made audio files. */
final class Fakes {
    private Fakes() {}

    /** Makes a sine-wave m4a with ffmpeg, so there is real audio for a browser to play. */
    static Path tone(Path dir, String name, int hz, int seconds) throws IOException, InterruptedException {
        Path file = dir.resolve(name + ".m4a");
        Process p = new ProcessBuilder("ffmpeg", "-y", "-loglevel", "error", "-f", "lavfi", "-i",
                "sine=frequency=" + hz + ":duration=" + seconds, "-c:a", "aac", "-movflags", "+faststart", file.toString())
                .redirectErrorStream(true).start();
        p.getInputStream().readAllBytes();
        if (p.waitFor() != 0) throw new IOException("ffmpeg failed");
        return file;
    }

    static NowPlaying track(String id, String title, long durationMs, boolean playing, long progressMs) {
        return new NowPlaying(id, title, List.of("Artist " + id), "Album", durationMs, playing, progressMs, null);
    }

    /** Returns whatever {@link #now} is set to. */
    static final class Mutable implements NowPlayingSource {
        volatile NowPlaying now;
        volatile List<NowPlaying> next = List.of();

        @Override
        public Optional<NowPlaying> current() {
            return Optional.ofNullable(now);
        }

        @Override
        public List<NowPlaying> upNext() {
            return next;
        }
    }

    /** Maps Spotify track IDs to ready-made files. */
    static AudioResolver files(java.util.Map<String, Path> byId) {
        return track -> {
            Path file = byId.get(track.trackId());
            if (file == null) throw new IOException("no audio for " + track.trackId());
            return new AudioResolver.Resolved(file, "audio/mp4", track.title() + " (YT)", "vid" + track.trackId());
        };
    }
}

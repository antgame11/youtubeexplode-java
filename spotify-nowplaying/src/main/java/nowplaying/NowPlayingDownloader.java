package nowplaying;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import youtubeexplode.YoutubeClient;
import youtubeexplode.exceptions.YoutubeExplodeException;
import youtubeexplode.music.MusicSearchResult.Song;
import youtubeexplode.videos.streams.AudioOnlyStreamInfo;
import youtubeexplode.videos.streams.StreamInfo;

/**
 * Looks up what is playing on Spotify, finds the same song on YouTube Music and downloads its audio.
 *
 * <pre>
 * Usage: NowPlayingDownloader --client-id ID [--out DIR] [--watch] [--port 8888]
 *   (the client ID can also come from the SPOTIFY_CLIENT_ID environment variable)
 * </pre>
 */
public final class NowPlayingDownloader {
    private final SpotifyClient spotify;
    private final YoutubeClient youtube;
    private final Path outDir;

    NowPlayingDownloader(SpotifyClient spotify, YoutubeClient youtube, Path outDir) {
        this.spotify = spotify;
        this.youtube = youtube;
        this.outDir = outDir;
    }

    /** Finds the best YouTube Music match for the track, or empty if there are no results. */
    public Optional<TrackMatcher.Match> findMatch(NowPlaying track) {
        String query = track.artists().isEmpty() ? track.title() : track.artists().get(0) + " " + track.title();
        List<Song> candidates = youtube.music().getSongs(query).limit(10).toList();
        return TrackMatcher.best(track, candidates);
    }

    /** Downloads the track; returns the file path, or empty if skipped. */
    Optional<Path> download(NowPlaying track) throws IOException {
        Optional<TrackMatcher.Match> match = findMatch(track);
        if (match.isEmpty()) {
            System.err.println("  No YouTube Music results for: " + track);
            return Optional.empty();
        }

        Song song = match.get().song();
        System.out.printf("  Match: %s - %s [%s] (score %.1f%s)%n",
                song.artists().stream().map(Object::toString).reduce((a, b) -> a + ", " + b).orElse("?"),
                song.title(), song.id(), match.get().score(),
                match.get().isConfident() ? "" : ", LOW CONFIDENCE");
        if (!match.get().isConfident()) {
            System.err.println("  Skipping: no confident match (use a different track or lower the bar in TrackMatcher).");
            return Optional.empty();
        }

        String baseName = sanitize(track.artistLine() + " - " + track.title());
        Files.createDirectories(outDir);

        // Already downloaded (in whatever format)?
        for (String ext : List.of("m4a", "webm", "mp4", "opus", "mp3")) {
            Path existing = outDir.resolve(baseName + "." + ext);
            if (Files.exists(existing)) {
                System.out.println("  Already downloaded: " + existing);
                return Optional.empty();
            }
        }

        try {
            return Optional.of(downloadWithLibrary(song, baseName));
        } catch (YoutubeExplodeException | UncheckedIOException e) {
            System.err.println("\n  The built-in downloader failed: " + e.getMessage());
            return Optional.of(downloadWithYtDlp(song, baseName, e));
        }
    }

    private Path downloadWithLibrary(Song song, String baseName) throws IOException {
        var manifest = youtube.videos().streams().getManifest(song.id());
        AudioOnlyStreamInfo audio = StreamInfo.getWithHighestBitrate(manifest.getAudioOnlyStreams());

        Path file = outDir.resolve(baseName + "." + audio.getContainer());
        Path partial = file.resolveSibling(file.getFileName() + ".part");
        int[] last = {-1};
        youtube.videos().streams().download(audio, partial, p -> {
            int pct = (int) (p * 100);
            if (pct / 10 != last[0] / 10) {
                last[0] = pct;
                System.out.print("\r  Downloading... " + pct + "%");
            }
        });
        Files.move(partial, file);
        System.out.println("\r  Saved: " + file + " (" + audio.getSize() + ", " + audio.getBitrate() + ")");
        return file;
    }

    /** Backup: let yt-dlp do it, with the saved Google login if there is one. */
    private Path downloadWithYtDlp(Song song, String baseName, RuntimeException libraryFailure) throws IOException {
        Optional<YtDlp> ytDlp = YtDlp.find();
        if (ytDlp.isEmpty()) {
            System.err.println("  No backup downloader found. Install yt-dlp (plus ffmpeg, and Node or Deno) to enable one:");
            System.err.println("  https://github.com/yt-dlp/yt-dlp");
            throw libraryFailure;
        }

        var cookies = YoutubeSession.loadCookies();
        System.out.println("  Trying yt-dlp" + (cookies.isEmpty() ? "" : " with your saved YouTube login") + "...");

        Path tmp = Files.createTempDirectory(outDir, ".ytdlp-");
        try {
            Path got = ytDlp.get().downloadAudio(song.id().getValue(), tmp, cookies);
            String name = got.getFileName().toString();
            Path target = outDir.resolve(baseName + name.substring(name.lastIndexOf('.')));
            Files.move(got, target);
            System.out.println("  Saved: " + target + " (yt-dlp)");
            return target;
        } catch (IOException e) {
            throw new YoutubeExplodeException("Could not download '" + song.title() + "'. Built-in downloader: "
                    + libraryFailure.getMessage() + " | yt-dlp: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted", e);
        } finally {
            deleteTree(tmp);
        }
    }

    public static void deleteTree(Path dir) {
        try (var walk = Files.walk(dir)) {
            walk.sorted(java.util.Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        } catch (IOException ignored) {
            // Temporary files: best effort
        }
    }

    static String sanitize(String name) {
        String s = name.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").strip();
        return s.length() > 150 ? s.substring(0, 150) : s;
    }

    void runOnce() throws IOException, InterruptedException {
        Optional<NowPlaying> now = spotify.currentlyPlaying();
        if (now.isEmpty()) {
            System.err.println("Nothing is playing on Spotify right now.");
            System.exit(2);
        }
        System.out.println("Now playing: " + now.get());
        download(now.get());
    }

    void runWatch() throws IOException, InterruptedException {
        System.out.println("Watching Spotify; downloading each new track. Ctrl+C to stop.");
        Set<String> handled = new HashSet<>();
        while (true) {
            try {
                Optional<NowPlaying> now = spotify.currentlyPlaying();
                if (now.isPresent() && now.get().trackId() != null && handled.add(now.get().trackId())) {
                    System.out.println("Now playing: " + now.get());
                    try {
                        download(now.get());
                    } catch (RuntimeException | IOException e) {
                        System.err.println("  Download failed: " + e);
                    }
                }
            } catch (IOException e) {
                System.err.println("Spotify poll failed: " + e.getMessage());
            }
            Thread.sleep(5000);
        }
    }

    public static void main(String[] args) throws Exception {
        String clientId = nowplaying.Config.get("SPOTIFY_CLIENT_ID");
        Path out = Path.of(".");
        boolean watch = false;
        int port = 8888;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--client-id" -> clientId = args[++i];
                case "--out" -> out = Path.of(args[++i]);
                case "--port" -> port = Integer.parseInt(args[++i]);
                case "--watch" -> watch = true;
                default -> {
                    System.err.println("Unknown argument: " + args[i]);
                    System.exit(1);
                }
            }
        }

        if (clientId == null || clientId.isBlank()) {
            System.err.println("""
                    Missing Spotify client ID.
                    1. Create an app at https://developer.spotify.com/dashboard
                    2. Add the redirect URI  http://127.0.0.1:%d/callback  to its settings
                    3. Run with --client-id <ID> (or set SPOTIFY_CLIENT_ID)""".formatted(port));
            System.exit(1);
        }

        Path tokenFile = Path.of(System.getProperty("user.home"), ".config", "youtubeexplode-nowplaying", "spotify.json");
        SpotifyAuth auth = new SpotifyAuth(clientId, port, tokenFile);
        System.err.println("Spotify redirect URI (must be registered in your app): " + auth.redirectUri());

        try (YoutubeClient youtube = nowplaying.YoutubeSession.open()) {
            NowPlayingDownloader app = new NowPlayingDownloader(new SpotifyClient(auth), youtube, out);
            if (watch) app.runWatch();
            else app.runOnce();
        }
    }
}

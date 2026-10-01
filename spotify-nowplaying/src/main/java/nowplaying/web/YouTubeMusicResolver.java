package nowplaying.web;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import nowplaying.NowPlaying;
import nowplaying.TrackMatcher;
import youtubeexplode.YoutubeClient;
import youtubeexplode.music.MusicSearchResult.Song;
import youtubeexplode.videos.streams.AudioOnlyStreamInfo;
import youtubeexplode.videos.streams.StreamInfo;

/**
 * Finds the song on YouTube Music and downloads its audio into a cache folder. MP4/AAC is preferred
 * because every browser plays it; when ffmpeg is available the file is remuxed (no re-encoding) into
 * a regular progressive .m4a so seeking is reliable.
 */
public final class YouTubeMusicResolver implements AudioResolver {
    private final YoutubeClient youtube;
    private final Path cacheDir;
    private final boolean ffmpeg;

    public YouTubeMusicResolver(YoutubeClient youtube, Path cacheDir) throws IOException {
        this.youtube = youtube;
        this.cacheDir = cacheDir;
        this.ffmpeg = hasFfmpeg();
        Files.createDirectories(cacheDir);
    }

    private static boolean hasFfmpeg() {
        try {
            Process p = new ProcessBuilder("ffmpeg", "-version").redirectErrorStream(true).start();
            p.getInputStream().readAllBytes();
            return p.waitFor() == 0;
        } catch (IOException | InterruptedException e) {
            return false;
        }
    }

    @Override
    public Resolved resolve(NowPlaying track) throws Exception {
        String query = track.artists().isEmpty() ? track.title() : track.artists().get(0) + " " + track.title();
        List<Song> candidates = youtube.music().getSongs(query).limit(10).toList();

        TrackMatcher.Match match = TrackMatcher.best(track, candidates)
                .orElseThrow(() -> new IOException("No YouTube Music results for " + track));
        if (!match.isConfident()) {
            throw new IOException("No confident match for " + track + " (best: " + match.song().title()
                    + ", score " + String.format("%.1f", match.score()) + ")");
        }
        Song song = match.song();
        String videoId = song.id().getValue();

        // Already cached?
        for (String ext : List.of("m4a", "webm")) {
            Path cached = cacheDir.resolve(videoId + "." + ext);
            if (Files.exists(cached)) return new Resolved(cached, mime(ext), song.title(), videoId);
        }

        var manifest = youtube.videos().streams().getManifest(song.id());
        AudioOnlyStreamInfo audio = manifest.getAudioOnlyStreams().stream()
                .filter(s -> s.getContainer().getName().equalsIgnoreCase("mp4"))
                .max(java.util.Comparator.comparing(StreamInfo::getBitrate))
                .orElseGet(() -> StreamInfo.getWithHighestBitrate(manifest.getAudioOnlyStreams()));

        boolean mp4 = audio.getContainer().getName().equalsIgnoreCase("mp4");
        String ext = mp4 ? "m4a" : audio.getContainer().getName();
        Path target = cacheDir.resolve(videoId + "." + ext);
        Path raw = Files.createTempFile(cacheDir, videoId + "-", ".part");
        try {
            youtube.videos().streams().download(audio, raw);

            if (mp4 && ffmpeg) {
                Path remuxed = Files.createTempFile(cacheDir, videoId + "-", ".m4a.part");
                try {
                    remux(raw, remuxed);
                    Files.move(remuxed, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                } finally {
                    Files.deleteIfExists(remuxed);
                }
            } else {
                Files.move(raw, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(raw);
        }

        return new Resolved(target, mime(ext), song.title(), videoId);
    }

    private static void remux(Path in, Path out) throws IOException, InterruptedException {
        Process p = new ProcessBuilder(
                        "ffmpeg", "-y", "-loglevel", "error", "-i", in.toString(),
                        "-c", "copy", "-movflags", "+faststart", "-f", "mp4", out.toString())
                .redirectErrorStream(true)
                .start();
        String output = new String(p.getInputStream().readAllBytes());
        if (!p.waitFor(2, TimeUnit.MINUTES) || p.exitValue() != 0) throw new IOException("ffmpeg remux failed: " + output);
    }

    static String mime(String ext) {
        return switch (ext) {
            case "m4a", "mp4" -> "audio/mp4";
            case "webm" -> "audio/webm";
            default -> "application/octet-stream";
        };
    }
}

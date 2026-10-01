import java.nio.file.Path;
import youtubeexplode.YoutubeClient;
import youtubeexplode.music.MusicSearchResult;
import youtubeexplode.videos.streams.AudioOnlyStreamInfo;
import youtubeexplode.videos.streams.StreamInfo;

/** Usage: MusicSearch <query> [--download N] [--limit N] */
public class MusicSearch {
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: MusicSearch <query> [--download N] [--limit N]");
            System.exit(1);
        }
        String query = args[0];
        int limit = 10, download = 0;
        for (int i = 1; i + 1 < args.length; i += 2) {
            if (args[i].equals("--limit")) limit = Integer.parseInt(args[i + 1]);
            if (args[i].equals("--download")) download = Integer.parseInt(args[i + 1]);
        }

        try (var youtube = new YoutubeClient()) {
            var songs = youtube.music().getSongs(query).limit(limit).toList();
            for (int i = 0; i < songs.size(); i++) {
                MusicSearchResult.Song s = songs.get(i);
                System.out.printf("%2d. %s - %s  [%s]  %s%n", i + 1, s.artists().stream().map(Object::toString).reduce((a, b) -> a + ", " + b).orElse("?"),
                        s.title(), s.album() != null ? s.album() : "single", s.id());
            }

            if (download > 0) {
                var song = songs.get(download - 1);
                var manifest = youtube.videos().streams().getManifest(song.id());
                AudioOnlyStreamInfo audio = StreamInfo.getWithHighestBitrate(manifest.getAudioOnlyStreams());
                Path file = Path.of(song.id() + "." + audio.getContainer());
                youtube.videos().streams().download(audio, file);
                System.out.println("Downloaded #" + download + " -> " + file.toAbsolutePath());
            }
        }
    }
}

import java.nio.file.Path;
import youtubeexplode.YoutubeClient;
import youtubeexplode.videos.Video;
import youtubeexplode.videos.streams.AudioOnlyStreamInfo;
import youtubeexplode.videos.streams.StreamInfo;
import youtubeexplode.videos.streams.StreamManifest;

/** Usage: DownloadAudio <video id or url> [output dir] */
public class DownloadAudio {
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: DownloadAudio <video id or url> [output dir]");
            System.exit(1);
        }
        Path dir = Path.of(args.length > 1 ? args[1] : ".");

        try (var youtube = new YoutubeClient()) {
            Video video = youtube.videos().get(args[0]);
            System.out.println("Video: " + video.getTitle() + " (" + video.getDuration() + ")");

            StreamManifest manifest = youtube.videos().streams().getManifest(video.getId());
            AudioOnlyStreamInfo audio = StreamInfo.getWithHighestBitrate(manifest.getAudioOnlyStreams());
            System.out.println("Stream: " + audio + ", " + audio.getBitrate() + ", " + audio.getSize());

            Path file = dir.resolve(video.getId() + "." + audio.getContainer());
            int[] last = {-1};
            youtube.videos().streams().download(audio, file, p -> {
                int pct = (int) (p * 100);
                if (pct != last[0]) { last[0] = pct; System.out.print("\rDownloading... " + pct + "%"); }
            });
            System.out.println("\nSaved: " + file.toAbsolutePath());
        }
    }
}

package nowplaying;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import youtubeexplode.YoutubeClient;

/** Real YouTube Music lookup and download for a fake "now playing" track (no Spotify needed). */
@Tag("live")
class PipelineLiveTest {
    @TempDir Path tmp;

    @Test
    void matchesAndDownloads() throws Exception {
        NowPlaying np = new NowPlaying("sp", "Runaway", List.of("Kanye West", "Pusha T"),
                "My Beautiful Dark Twisted Fantasy", 548_000, true);

        try (YoutubeClient youtube = new YoutubeClient()) {
            var app = new NowPlayingDownloader(null, youtube, tmp);

            var match = app.findMatch(np).orElseThrow();
            assertTrue(match.isConfident(), "score " + match.score());
            assertTrue(match.song().artists().stream().anyMatch(a -> a.name().contains("Kanye")));

            Path file = app.download(np).orElseThrow();
            assertTrue(Files.size(file) > 1_000_000);
            assertEquals(match.song().id().getValue().length(), 11);
        }
    }
}

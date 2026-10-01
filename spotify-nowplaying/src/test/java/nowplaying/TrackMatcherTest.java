package nowplaying;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import youtubeexplode.music.MusicRef;
import youtubeexplode.music.MusicSearchResult.Song;
import youtubeexplode.videos.VideoId;

class TrackMatcherTest {
    private static Song song(String id, String title, String artist, String album, int seconds) {
        return new Song(VideoId.parse(id), title, List.of(new MusicRef("UC", artist)),
                album == null ? null : new MusicRef("MP", album), Duration.ofSeconds(seconds), false, List.of());
    }

    private final NowPlaying runaway =
            new NowPlaying("sp1", "Runaway", List.of("Kanye West", "Pusha T"), "My Beautiful Dark Twisted Fantasy", 548_000, true);

    @Test
    void prefersTheRightVersion() {
        Song right = song("VhEoCOWUtcU", "Runaway (feat. Pusha T)", "Kanye West", "My Beautiful Dark Twisted Fantasy", 548);
        Song karaoke = song("aaaaaaaaaaa", "Runaway (Karaoke Version)", "Singer's Edge Karaoke", null, 548);
        Song cover = song("bbbbbbbbbbb", "Runaway", "Some Cover Band", "Covers", 300);
        Song live = song("ccccccccccc", "Runaway (Live)", "Kanye West", "Live", 600);

        var best = TrackMatcher.best(runaway, List.of(karaoke, cover, live, right)).orElseThrow();
        assertSame(right, best.song());
        assertTrue(best.isConfident());
    }

    @Test
    void unrelatedResultsAreNotConfident() {
        Song other = song("ddddddddddd", "Totally Different", "Nobody", null, 200);
        assertFalse(TrackMatcher.best(runaway, List.of(other)).orElseThrow().isConfident());
    }

    @Test
    void emptyCandidates() {
        assertTrue(TrackMatcher.best(runaway, List.of()).isEmpty());
    }

    @Test
    void normalizationIgnoresAccentsPunctuationAndFeatSuffixes() {
        assertEquals("beyonce", TrackMatcher.normalize("Beyoncé"));
        assertEquals("runaway", TrackMatcher.baseTitle("Runaway (feat. Pusha T)"));
        assertEquals("song", TrackMatcher.baseTitle("Song - 2011 Remaster"));
        assertEquals("a b", TrackMatcher.normalize("A.B!"));
    }

    @Test
    void filenamesAreSanitized() {
        assertEquals("AC_DC - Back in Black_", NowPlayingDownloader.sanitize("AC/DC - Back in Black?"));
    }
}

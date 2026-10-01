package youtubeexplode;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;
import youtubeexplode.common.Resolution;
import youtubeexplode.common.Thumbnail;
import youtubeexplode.videos.streams.Bitrate;
import youtubeexplode.videos.streams.Container;
import youtubeexplode.videos.streams.FileSize;
import youtubeexplode.videos.streams.VideoQuality;

class ValueTypeTests {
    @Test
    void videoQualityLabels() {
        VideoQuality q = VideoQuality.fromLabel("2160p60 HDR", 30);
        assertEquals(2160, q.getMaxHeight());
        assertEquals(60, q.getFramerate());
        assertTrue(q.isHighDefinition());

        assertEquals(30, VideoQuality.fromLabel("360p", 30).getFramerate());
        assertEquals(24, VideoQuality.fromLabel("1080s", 24).getFramerate());
        assertEquals("720p50", new VideoQuality(720, 50).getLabel());
        assertEquals("720p60", new VideoQuality(720, 59).getLabel());
        assertEquals("720p", new VideoQuality(720, 30).getLabel());
    }

    @Test
    void videoQualityFromItagAndOrdering() {
        assertEquals(360, VideoQuality.fromItag(18, 30).getMaxHeight());
        assertEquals(1080, VideoQuality.fromItag(137, 30).getMaxHeight());
        assertThrows(IllegalArgumentException.class, () -> VideoQuality.fromItag(99999, 30));
        assertTrue(new VideoQuality(1080, 30).compareTo(new VideoQuality(720, 60)) > 0);
        assertEquals(new Resolution(1920, 1080), new VideoQuality(1080, 30).getDefaultVideoResolution());
    }

    @Test
    void formatting() {
        assertEquals("1.5 KB", new FileSize(1536).toString());
        assertEquals("12 B", new FileSize(12).toString());
        assertEquals("1 MB", new FileSize(1024 * 1024).toString());
        assertEquals("2.5 Mbit/s", new Bitrate(2_621_440).toString());
        assertEquals("512 Bit/s", new Bitrate(512).toString());
    }

    @Test
    void containers() {
        assertEquals(new Container("MP4"), Container.MP4);
        assertTrue(new Container("m4a").isAudioOnly());
        assertFalse(Container.WEBM.isAudioOnly());
    }

    @Test
    void thumbnails() {
        List<Thumbnail> t = List.of(
                new Thumbnail("a", new Resolution(10, 10)), new Thumbnail("b", new Resolution(100, 50)));
        assertEquals("b", Thumbnail.getWithHighestResolution(t).getUrl());
        assertThrows(java.util.NoSuchElementException.class, () -> Thumbnail.getWithHighestResolution(List.of()));
    }
}

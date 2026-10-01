package youtubeexplode;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import youtubeexplode.channels.Channel;
import youtubeexplode.exceptions.VideoUnavailableException;
import youtubeexplode.playlists.Playlist;
import youtubeexplode.playlists.PlaylistVideo;
import youtubeexplode.search.VideoSearchResult;
import youtubeexplode.videos.Video;
import youtubeexplode.videos.closedcaptions.ClosedCaptionManifest;
import youtubeexplode.videos.streams.AudioOnlyStreamInfo;
import youtubeexplode.videos.streams.MediaStream;
import youtubeexplode.videos.streams.StreamInfo;
import youtubeexplode.videos.streams.StreamManifest;

/** Hits the real YouTube. Run with: mvn test -DexcludedGroups= -Dgroups=live */
@Tag("live")
class LiveTests {
    private final YoutubeClient youtube = new YoutubeClient();

    @Test
    void video() {
        Video video = youtube.videos().get("https://www.youtube.com/watch?v=jNQXAC9IVRw");
        assertEquals("Me at the zoo", video.getTitle());
        assertEquals("jawed", video.getAuthor().getChannelTitle().toLowerCase());
        assertTrue(video.getEngagement().getViewCount() > 0);
    }

    @Test
    void missingVideo() {
        assertThrows(VideoUnavailableException.class, () -> youtube.videos().get("qldieuTNZ"+"_Y"));
    }

    @Test
    void streamsAndDownload() throws Exception {
        StreamManifest manifest = youtube.videos().streams().getManifest("jNQXAC9IVRw");
        assertFalse(manifest.getStreams().isEmpty());

        AudioOnlyStreamInfo audio = StreamInfo.getWithHighestBitrate(manifest.getAudioOnlyStreams());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        youtube.videos().streams().copyTo(audio, out);
        assertEquals(audio.getSize().getBytes(), out.size());

        try (MediaStream s = youtube.videos().streams().get(audio)) {
            s.seek(100);
            byte[] b = new byte[50];
            assertEquals(50, s.readNBytes(b, 0, 50));
            assertArrayEquals(java.util.Arrays.copyOfRange(out.toByteArray(), 100, 150), b);
        }
    }

    @Test
    void closedCaptions() {
        ClosedCaptionManifest manifest = youtube.videos().closedCaptions().getManifest("_QdPW8JrYzQ");
        assertFalse(manifest.getTracks().isEmpty());
    }

    @Test
    void playlist() {
        Playlist playlist = youtube.playlists().get("PLI5YfMzCfRtZ8eV576YoY3vIYrHjyVm_e");
        assertNotNull(playlist.getTitle());
        List<PlaylistVideo> videos = youtube.playlists().getVideos(playlist.getId()).limit(5).toList();
        assertFalse(videos.isEmpty());
    }

    @Test
    void channel() {
        Channel channel = youtube.channels().get("UCEnBXANsKmyj2r9xVyKoDiQ");
        assertNotNull(channel.getTitle());
        assertFalse(youtube.channels().getUploads(channel.getId()).limit(3).toList().isEmpty());
    }

    @Test
    void search() {
        List<VideoSearchResult> results = youtube.search().getVideos("undefined behavior").limit(25).toList();
        assertFalse(results.isEmpty());
    }

    @Test
    void musicSongsPaging() {
        var songs = youtube.music().getSongs("runaway").limit(45).toList();
        assertEquals(45, songs.size()); // spans 3 pages of 20
        assertEquals(45, songs.stream().map(youtubeexplode.music.MusicSearchResult.Song::id).distinct().count());
        var first = songs.get(0);
        assertFalse(first.artists().isEmpty());
        assertNotNull(first.duration());
    }

    @Test
    void musicMixedSearch() {
        var results = youtube.music().getResults("kanye west runaway").toList();
        assertTrue(results.stream().anyMatch(r -> r instanceof youtubeexplode.music.MusicSearchResult.Song));
        assertTrue(results.stream().anyMatch(r -> r instanceof youtubeexplode.music.MusicSearchResult.Artist));
        assertTrue(results.stream().anyMatch(r -> r instanceof youtubeexplode.music.MusicSearchResult.Album));
    }
}

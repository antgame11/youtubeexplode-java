package youtubeexplode.music;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class MusicSearchResponseTest {
    private static String run(String text, String pageType, String id) {
        return "{\"text\":\"" + text + "\",\"navigationEndpoint\":{\"browseEndpoint\":{\"browseId\":\"" + id
                + "\",\"browseEndpointContextSupportedConfigs\":{\"browseEndpointContextMusicConfig\":{\"pageType\":\""
                + pageType + "\"}}}}}";
    }

    private static final String SEP = "{\"text\":\" • \"}";

    private static String col(String... runs) {
        return "{\"musicResponsiveListItemFlexColumnRenderer\":{\"text\":{\"runs\":[" + String.join(",", runs) + "]}}}";
    }

    @Test
    void parsesSongVideoAlbumArtistAndContinuation() {
        String song = "{\"musicResponsiveListItemRenderer\":{\"flexColumns\":["
                + col("{\"text\":\"Runaway\",\"navigationEndpoint\":{\"watchEndpoint\":{\"videoId\":\"VhEoCOWUtcU\","
                        + "\"watchEndpointMusicSupportedConfigs\":{\"watchEndpointMusicConfig\":{\"musicVideoType\":\"MUSIC_VIDEO_TYPE_ATV\"}}}}}")
                + ","
                + col(run("Kanye West", "MUSIC_PAGE_TYPE_ARTIST", "UCa"), SEP, run("MBDTF", "MUSIC_PAGE_TYPE_ALBUM", "MPREb_x"), SEP,
                        "{\"text\":\"9:08\"}")
                + "],\"badges\":[{\"musicInlineBadgeRenderer\":{\"icon\":{\"iconType\":\"MUSIC_EXPLICIT_BADGE\"}}}],"
                + "\"playlistItemData\":{\"videoId\":\"VhEoCOWUtcU\"}}}";

        String video = "{\"musicResponsiveListItemRenderer\":{\"flexColumns\":["
                + col("{\"text\":\"A Video\",\"navigationEndpoint\":{\"watchEndpoint\":{\"videoId\":\"pZUzfyWQkEI\","
                        + "\"watchEndpointMusicSupportedConfigs\":{\"watchEndpointMusicConfig\":{\"musicVideoType\":\"MUSIC_VIDEO_TYPE_UGC\"}}}}}")
                + ","
                + col(run("Some Channel", "MUSIC_PAGE_TYPE_USER_CHANNEL", "UCb"), SEP, "{\"text\":\"1.4M views\"}", SEP,
                        "{\"text\":\"1:05:29\"}")
                + "],\"playlistItemData\":{\"videoId\":\"pZUzfyWQkEI\"}}}";

        String album = "{\"musicResponsiveListItemRenderer\":{\"flexColumns\":[" + col("{\"text\":\"Graduation\"}") + ","
                + col("{\"text\":\"Album\"}", SEP, run("Kanye West", "MUSIC_PAGE_TYPE_ARTIST", "UCa"), SEP, "{\"text\":\"2007\"}")
                + "],\"navigationEndpoint\":{\"browseEndpoint\":{\"browseId\":\"MPREb_g\",\"browseEndpointContextSupportedConfigs\":"
                + "{\"browseEndpointContextMusicConfig\":{\"pageType\":\"MUSIC_PAGE_TYPE_ALBUM\"}}}}}}";

        String artist = "{\"musicResponsiveListItemRenderer\":{\"flexColumns\":[" + col("{\"text\":\"Kanye West\"}") + ","
                + col("{\"text\":\"Artist\"}", SEP, "{\"text\":\"304M monthly audience\"}")
                + "],\"navigationEndpoint\":{\"browseEndpoint\":{\"browseId\":\"UCa\",\"browseEndpointContextSupportedConfigs\":"
                + "{\"browseEndpointContextMusicConfig\":{\"pageType\":\"MUSIC_PAGE_TYPE_ARTIST\"}}}}}}";

        String playlist = "{\"musicResponsiveListItemRenderer\":{\"flexColumns\":[" + col("{\"text\":\"A playlist\"}")
                + "],\"navigationEndpoint\":{\"browseEndpoint\":{\"browseId\":\"VLPL1\",\"browseEndpointContextSupportedConfigs\":"
                + "{\"browseEndpointContextMusicConfig\":{\"pageType\":\"MUSIC_PAGE_TYPE_PLAYLIST\"}}}}}}";

        String json = "{\"continuationContents\":{\"musicShelfContinuation\":{\"contents\":[" + String.join(",", song, video, album, artist, playlist)
                + "],\"continuations\":[{\"nextContinuationData\":{\"continuation\":\"TOK\"}}]}}}";

        MusicSearchResponse response = MusicSearchResponse.parse(json);
        assertEquals("TOK", response.continuationToken());

        List<MusicSearchResult> results = response.results();
        assertEquals(4, results.size()); // the playlist is skipped

        var s = assertInstanceOf(MusicSearchResult.Song.class, results.get(0));
        assertEquals("VhEoCOWUtcU", s.id().getValue());
        assertEquals("Kanye West", s.artists().get(0).name());
        assertEquals("MBDTF", s.album().name());
        assertEquals(Duration.ofSeconds(548), s.duration());
        assertTrue(s.isExplicit());

        var v = assertInstanceOf(MusicSearchResult.Video.class, results.get(1));
        assertEquals("Some Channel", v.channel().name());
        assertEquals("1.4M views", v.viewsText());
        assertEquals(Duration.ofSeconds(3929), v.duration());

        var a = assertInstanceOf(MusicSearchResult.Album.class, results.get(2));
        assertEquals("Album", a.type());
        assertEquals("2007", a.year());
        assertEquals("Kanye West", a.artists().get(0).name());

        var r = assertInstanceOf(MusicSearchResult.Artist.class, results.get(3));
        assertEquals("304M monthly audience", r.subtitle());
    }

    @Test
    void lastPageHasNoContinuation() {
        assertNull(MusicSearchResponse.parse("{\"contents\":{}}").continuationToken());
    }
}

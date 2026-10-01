package youtubeexplode;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import youtubeexplode.channels.ChannelHandle;
import youtubeexplode.channels.ChannelId;
import youtubeexplode.channels.ChannelSlug;
import youtubeexplode.channels.UserName;
import youtubeexplode.playlists.PlaylistId;
import youtubeexplode.videos.VideoId;

class IdTests {
    // ---- Video ----
    @ParameterizedTest
    @ValueSource(strings = {"9bZkp7q19f0", "_kmeFXjjGfk", "AI7ULzgf8RU"})
    void videoIdFromId(String id) {
        assertEquals(id, VideoId.parse(id).getValue());
    }

    @ParameterizedTest
    @CsvSource({
        "youtube.com/watch?v=yIVRs6YSbOM, yIVRs6YSbOM",
        "youtu.be/watch?v=Fcds0_MrgNU, Fcds0_MrgNU",
        "youtu.be/yIVRs6YSbOM, yIVRs6YSbOM",
        "youtube.com/embed/yIVRs6YSbOM, yIVRs6YSbOM",
        "youtube.com/shorts/sKL1vjP0tIo, sKL1vjP0tIo",
        "youtube.com/live/jfKfPfyJRdk, jfKfPfyJRdk",
    })
    void videoIdFromUrl(String url, String expected) {
        assertEquals(expected, VideoId.parse(url).getValue());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "", "pI2I2zqzeK", "pI2I2z zeKg", "youtube.com/xxx?v=pI2I2zqzeKg", "youtu.be/watch?v=xxx",
        "youtube.com/embed/", "youtube.com/live/"
    })
    void videoIdInvalid(String input) {
        assertThrows(IllegalArgumentException.class, () -> VideoId.parse(input));
        assertTrue(VideoId.tryParse(input).isEmpty());
    }

    // ---- Playlist ----
    @ParameterizedTest
    @ValueSource(strings = {
        "WL", "LL", "RDMM", "PL601B2E69B03FAB9D", "PLI5YfMzCfRtZ8eV576YoY3vIYrHjyVm_e",
        "OLAK5uy_mtOdjCW76nDvf5yOzgcAVMYpJ5gcW5uKU", "RD1hu8-y6fKg0", "UUTMt7iMWa7jy0fNXIktwyLA",
        "FLEnBXANsKmyj2r9xVyKoDiQ"
    })
    void playlistIdFromId(String id) {
        assertEquals(id, PlaylistId.parse(id).getValue());
    }

    @ParameterizedTest
    @CsvSource({
        "youtube.com/playlist?list=PLOU2XLYxmsIJGErt5rrCqaSGTMyyqNt2H, PLOU2XLYxmsIJGErt5rrCqaSGTMyyqNt2H",
        "youtube.com/watch?v=b8m9zhNAgKs&list=PL9tY0BWXOZFuFEG_GtOBZ8-8wbkH-NVAr, PL9tY0BWXOZFuFEG_GtOBZ8-8wbkH-NVAr",
        "youtu.be/b8m9zhNAgKs/?list=PL9tY0BWXOZFuFEG_GtOBZ8-8wbkH-NVAr, PL9tY0BWXOZFuFEG_GtOBZ8-8wbkH-NVAr",
        "youtube.com/embed/b8m9zhNAgKs/?list=PL9tY0BWXOZFuFEG_GtOBZ8-8wbkH-NVAr, PL9tY0BWXOZFuFEG_GtOBZ8-8wbkH-NVAr",
    })
    void playlistIdFromUrl(String url, String expected) {
        assertEquals(expected, PlaylistId.parse(url).getValue());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "", "PLm_3vnTS-pvmZFuF L1Pyhqf8kTTYVKjW", "PLm_3vnTS-pvmZFuF3L=Pyhqf8kTTYVKjW",
        "youtube.com/playlist?lisp=PLOU2XLYxmsIJGErt5rrCqaSGTMyyqNt2H", "youtube.com/"
    })
    void playlistIdInvalid(String input) {
        assertThrows(IllegalArgumentException.class, () -> PlaylistId.parse(input));
    }

    // ---- Channel ----
    @ParameterizedTest
    @ValueSource(strings = {"UCEnBXANsKmyj2r9xVyKoDiQ", "UC46807r_RiRjH8IU-h_DrDQ"})
    void channelIdFromId(String id) {
        assertEquals(id, ChannelId.parse(id).getValue());
    }

    @ParameterizedTest
    @CsvSource({
        "youtube.com/channel/UC3xnGqlcL3y-GXz5N3wiTJQ, UC3xnGqlcL3y-GXz5N3wiTJQ",
        "youtube.com/channel/UCkQO3QsgTpNTsOw6ujimT5Q, UCkQO3QsgTpNTsOw6ujimT5Q",
    })
    void channelIdFromUrl(String url, String expected) {
        assertEquals(expected, ChannelId.parse(url).getValue());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "", "UC3xnGqlcL3y-GXz5N3wiTJ", "UC3xnGqlcL y-GXz5N3wiTJQ",
        "youtube.com/?channel=UCUC3xnGqlcL3y-GXz5N3wiTJQ", "youtube.com/channel/asd", "youtube.com/"
    })
    void channelIdInvalid(String input) {
        assertThrows(IllegalArgumentException.class, () -> ChannelId.parse(input));
    }

    @ParameterizedTest
    @CsvSource({"youtube.com/user/ProZD, ProZD", "youtube.com/user/TheTyrrr, TheTyrrr", "TheTyrrr, TheTyrrr"})
    void userName(String input, String expected) {
        assertEquals(expected, UserName.parse(input).getValue());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "", "The_Tyrrr", "0123456789ABCDEFGHIJK", "A1B2C3-", "=0123456789ABCDEF",
        "youtube.com/user/P_roZD", "example.com/user/ProZD"
    })
    void userNameInvalid(String input) {
        assertThrows(IllegalArgumentException.class, () -> UserName.parse(input));
    }

    @ParameterizedTest
    @CsvSource({"youtube.com/c/Tyrrrz, Tyrrrz", "Tyrrrz, Tyrrrz", "youtube.com/c/BlenderFoundation, BlenderFoundation"})
    void channelSlug(String input, String expected) {
        assertEquals(expected, ChannelSlug.parse(input).getValue());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "foo bar", "youtube.com/?c=Tyrrrz", "youtube.com/channel/Tyrrrz", "youtube.com/"})
    void channelSlugInvalid(String input) {
        assertThrows(IllegalArgumentException.class, () -> ChannelSlug.parse(input));
    }

    @ParameterizedTest
    @CsvSource({"BeauMiles, BeauMiles", "a-z.0_9, a-z.0_9", "youtube.com/@BeauMiles, BeauMiles", "youtube.com/@a-z.0_9, a-z.0_9"})
    void channelHandle(String input, String expected) {
        assertEquals(expected, ChannelHandle.parse(input).getValue());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "", "foo bar", "youtube.com/", "youtube.com@BeauMiles", "youtube.com/@=BeauMiles",
        "youtube.com/@BeauMile$", "youtube.com/@Beau+Miles", "youtube.com/?@BeauMiles"
    })
    void channelHandleInvalid(String input) {
        assertThrows(IllegalArgumentException.class, () -> ChannelHandle.parse(input));
    }
}

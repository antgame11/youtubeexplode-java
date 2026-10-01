package youtubeexplode.bridge;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import youtubeexplode.utils.Dates;
import youtubeexplode.utils.Json;
import youtubeexplode.utils.Strings;

public final class PlaylistVideoData {
    private final JsonNode content;

    public PlaylistVideoData(JsonNode content) {
        this.content = content;
    }

    public Integer index() {
        return Json.integer(content, "navigationEndpoint", "watchEndpoint", "index");
    }

    public String id() {
        return Json.str(content, "videoId");
    }

    public String title() {
        String simple = Json.str(content, "title", "simpleText");
        return simple != null ? simple : Json.runsText(content, "title", "runs");
    }

    private JsonNode authorDetails() {
        JsonNode first = Json.at(content, "longBylineText", "runs", 0);
        return first != null ? first : Json.at(content, "shortBylineText", "runs", 0);
    }

    public String author() {
        return Json.str(authorDetails(), "text");
    }

    public String channelId() {
        JsonNode author = authorDetails();
        String id = Json.str(author, "navigationEndpoint", "browseEndpoint", "browseId");
        if (id != null) return id;

        // Some videos have multiple authors. Our current data model does not support that, so we only
        // extract the first one, since it's the channel that actually uploaded the video.
        return Json.str(
                author,
                "navigationEndpoint",
                "showDialogCommand",
                "panelLoadingStrategy",
                "inlineContent",
                "dialogViewModel",
                "customContent",
                "listViewModel",
                "listItems",
                0,
                "listItemViewModel",
                "rendererContext",
                "commandContext",
                "onTap",
                "innertubeCommand",
                "browseEndpoint",
                "browseId");
    }

    public Duration duration() {
        Double seconds = Strings.parseDouble(Json.str(content, "lengthSeconds"));
        if (seconds != null) return Dates.ofSecondsDouble(seconds);

        Duration simple = Dates.tryParseClock(Json.str(content, "lengthText", "simpleText"));
        if (simple != null) return simple;

        return Dates.tryParseClock(Json.runsText(content, "lengthText", "runs"));
    }

    public List<ThumbnailData> thumbnails() {
        List<ThumbnailData> result = new ArrayList<>();
        for (JsonNode j : Json.arrayOrEmpty(content, "thumbnail", "thumbnails")) result.add(new ThumbnailData(j));
        return result;
    }
}

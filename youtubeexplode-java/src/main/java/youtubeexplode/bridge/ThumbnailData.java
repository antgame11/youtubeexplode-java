package youtubeexplode.bridge;

import com.fasterxml.jackson.databind.JsonNode;
import youtubeexplode.utils.Json;

public final class ThumbnailData {
    private final JsonNode content;

    public ThumbnailData(JsonNode content) {
        this.content = content;
    }

    public String url() {
        return Json.str(content, "url");
    }

    public Integer width() {
        return Json.integer(content, "width");
    }

    public Integer height() {
        return Json.integer(content, "height");
    }
}

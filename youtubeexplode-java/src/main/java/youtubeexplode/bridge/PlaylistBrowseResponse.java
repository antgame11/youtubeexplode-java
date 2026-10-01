package youtubeexplode.bridge;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import youtubeexplode.utils.Json;
import youtubeexplode.utils.Strings;

public final class PlaylistBrowseResponse implements PlaylistData {
    private final JsonNode content;

    public PlaylistBrowseResponse(JsonNode content) {
        this.content = content;
    }

    public static PlaylistBrowseResponse parse(String raw) {
        return new PlaylistBrowseResponse(Json.parse(raw));
    }

    private JsonNode sidebar() {
        return Json.at(content, "sidebar", "playlistSidebarRenderer", "items");
    }

    private JsonNode sidebarPrimary() {
        return Json.at(sidebar(), 0, "playlistSidebarPrimaryInfoRenderer");
    }

    private JsonNode sidebarSecondary() {
        return Json.at(sidebar(), 1, "playlistSidebarSecondaryInfoRenderer");
    }

    public boolean isAvailable() {
        return sidebar() != null;
    }

    /** Simple text, then concatenated runs, then the editable form field value. */
    private String textOrRunsOrForm(JsonNode node, String property, String formProperty) {
        String simple = Json.str(node, property, "simpleText");
        if (simple != null) return simple;

        String runs = Json.runsText(node, property, "runs");
        if (runs != null) return runs;

        if (formProperty == null) return null;
        return Json.str(node, formProperty, "inlineFormRenderer", "formField", "textInputFormFieldRenderer", "value");
    }

    @Override
    public String title() {
        return textOrRunsOrForm(sidebarPrimary(), "title", "titleForm");
    }

    private JsonNode authorDetails() {
        return Json.at(sidebarSecondary(), "videoOwner", "videoOwnerRenderer");
    }

    @Override
    public String author() {
        return textOrRunsOrForm(authorDetails(), "title", null);
    }

    @Override
    public String channelId() {
        return Json.str(authorDetails(), "navigationEndpoint", "browseEndpoint", "browseId");
    }

    @Override
    public String description() {
        return textOrRunsOrForm(sidebarPrimary(), "description", "descriptionForm");
    }

    @Override
    public Integer count() {
        Integer fromRuns = Strings.parseInt(Json.str(sidebarPrimary(), "stats", 0, "runs", 0, "text"));
        if (fromRuns != null) return fromRuns;

        String simple = Json.str(sidebarPrimary(), "stats", 0, "simpleText");
        return simple == null ? null : Strings.parseInt(simple.split(" ")[0]);
    }

    @Override
    public List<ThumbnailData> thumbnails() {
        JsonNode renderer = Json.at(sidebarPrimary(), "thumbnailRenderer");
        List<JsonNode> thumbs = Json.array(renderer, "playlistVideoThumbnailRenderer", "thumbnail", "thumbnails");
        if (thumbs == null) thumbs = Json.array(renderer, "playlistCustomThumbnailRenderer", "thumbnail", "thumbnails");

        List<ThumbnailData> result = new ArrayList<>();
        if (thumbs != null) for (JsonNode j : thumbs) result.add(new ThumbnailData(j));
        return result;
    }
}

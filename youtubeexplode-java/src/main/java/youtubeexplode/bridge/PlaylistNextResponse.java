package youtubeexplode.bridge;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import youtubeexplode.utils.Json;
import youtubeexplode.utils.Strings;

public final class PlaylistNextResponse implements PlaylistData {
    private final JsonNode content;

    public PlaylistNextResponse(JsonNode content) {
        this.content = content;
    }

    public static PlaylistNextResponse parse(String raw) {
        return new PlaylistNextResponse(Json.parse(raw));
    }

    private JsonNode contentRoot() {
        return Json.at(content, "contents", "twoColumnWatchNextResults", "playlist", "playlist");
    }

    public boolean isAvailable() {
        return contentRoot() != null;
    }

    @Override
    public String title() {
        return Json.str(contentRoot(), "title");
    }

    @Override
    public String author() {
        return Json.str(contentRoot(), "ownerName", "simpleText");
    }

    @Override
    public String channelId() {
        return null;
    }

    @Override
    public String description() {
        return null;
    }

    @Override
    public Integer count() {
        Integer total = Strings.parseInt(Json.str(contentRoot(), "totalVideosText", "runs", 0, "text"));
        if (total != null) return total;
        return Strings.parseInt(Json.str(contentRoot(), "videoCountText", "runs", 2, "text"));
    }

    @Override
    public List<ThumbnailData> thumbnails() {
        List<PlaylistVideoData> videos = videos();
        return videos.isEmpty() ? List.of() : videos.get(0).thumbnails();
    }

    public List<PlaylistVideoData> videos() {
        List<PlaylistVideoData> result = new ArrayList<>();
        for (JsonNode j : Json.arrayOrEmpty(contentRoot(), "contents")) {
            JsonNode renderer = Json.at(j, "playlistPanelVideoRenderer");
            if (renderer != null) result.add(new PlaylistVideoData(renderer));
        }
        return result;
    }

    public String visitorData() {
        return Json.str(content, "responseContext", "visitorData");
    }
}

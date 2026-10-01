package youtubeexplode.bridge;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import youtubeexplode.utils.Dates;
import youtubeexplode.utils.Json;

public final class SearchResponse {
    // Search response is incredibly inconsistent (with at least 5 variations),
    // so we employ descendant searching, which is inefficient but resilient.

    private final JsonNode content;

    public SearchResponse(JsonNode content) {
        this.content = content;
    }

    public static SearchResponse parse(String raw) {
        return new SearchResponse(Json.parse(raw));
    }

    private JsonNode contentRoot() {
        JsonNode contents = Json.at(content, "contents");
        return contents != null ? contents : Json.at(content, "onResponseReceivedCommands");
    }

    public List<VideoData> videos() {
        List<VideoData> result = new ArrayList<>();
        JsonNode root = contentRoot();
        if (root != null) {
            for (JsonNode j : Json.descendantProperties(root, "videoRenderer")) result.add(new VideoData(j));
        }
        return result;
    }

    public List<PlaylistItemData> playlists() {
        List<PlaylistItemData> result = new ArrayList<>();
        JsonNode root = contentRoot();
        if (root == null) return result;

        for (JsonNode j : Json.descendantProperties(root, "lockupViewModel")) result.add(new PlaylistItemData(j));
        if (result.isEmpty()) {
            for (JsonNode j : Json.descendantProperties(root, "playlistRenderer")) {
                result.add(new PlaylistItemData(j));
            }
        }
        return result;
    }

    public List<ChannelData> channels() {
        List<ChannelData> result = new ArrayList<>();
        JsonNode root = contentRoot();
        if (root != null) {
            for (JsonNode j : Json.descendantProperties(root, "channelRenderer")) result.add(new ChannelData(j));
        }
        return result;
    }

    public String continuationToken() {
        JsonNode root = contentRoot();
        if (root == null) return null;
        List<JsonNode> commands = Json.descendantProperties(root, "continuationCommand");
        return commands.isEmpty() ? null : Json.str(commands.get(0), "token");
    }

    private static String titleOf(JsonNode content) {
        String simple = Json.str(content, "title", "simpleText");
        return simple != null ? simple : Json.runsText(content, "title", "runs");
    }

    private static List<ThumbnailData> thumbnailsOf(List<JsonNode> nodes) {
        List<ThumbnailData> result = new ArrayList<>();
        for (JsonNode j : nodes) result.add(new ThumbnailData(j));
        return result;
    }

    public static final class VideoData {
        private final JsonNode content;

        VideoData(JsonNode content) {
            this.content = content;
        }

        public String id() {
            return Json.str(content, "videoId");
        }

        public String title() {
            return titleOf(content);
        }

        private JsonNode authorDetails() {
            JsonNode first = Json.at(content, "longBylineText", "runs", 0);
            return first != null ? first : Json.at(content, "shortBylineText", "runs", 0);
        }

        public String author() {
            return Json.str(authorDetails(), "text");
        }

        public String channelId() {
            String id = Json.str(authorDetails(), "navigationEndpoint", "browseEndpoint", "browseId");
            if (id != null) return id;
            return Json.str(
                    content,
                    "channelThumbnailSupportedRenderers",
                    "channelThumbnailWithLinkRenderer",
                    "navigationEndpoint",
                    "browseEndpoint",
                    "browseId");
        }

        public Duration duration() {
            Duration simple = Dates.tryParseClock(Json.str(content, "lengthText", "simpleText"));
            if (simple != null) return simple;
            return Dates.tryParseClock(Json.runsText(content, "lengthText", "runs"));
        }

        public List<ThumbnailData> thumbnails() {
            return thumbnailsOf(Json.arrayOrEmpty(content, "thumbnail", "thumbnails"));
        }
    }

    public static final class PlaylistItemData {
        private final JsonNode content;

        PlaylistItemData(JsonNode content) {
            this.content = content;
        }

        public String id() {
            String contentId = Json.str(content, "contentId");
            return contentId != null ? contentId : Json.str(content, "playlistId");
        }

        private JsonNode metadata() {
            return Json.at(content, "metadata", "lockupMetadataViewModel");
        }

        public String title() {
            String fromMetadata = Json.str(metadata(), "title", "content");
            return fromMetadata != null ? fromMetadata : titleOf(content);
        }

        private JsonNode authorDetails() {
            JsonNode meta = metadata();
            if (meta != null) {
                List<JsonNode> parts = Json.descendantProperties(meta, "metadataParts");
                if (!parts.isEmpty()) {
                    JsonNode text = Json.at(parts.get(0), 0, "text");
                    if (text != null) return text;
                }
            }
            return Json.at(content, "longBylineText", "runs", 0);
        }

        public String author() {
            JsonNode details = authorDetails();
            String fromContent = Json.str(details, "content");
            return fromContent != null ? fromContent : Json.str(details, "text");
        }

        public String channelId() {
            JsonNode details = authorDetails();
            String id = Json.str(details, "commandRuns", 0, "onTap", "innertubeCommand", "browseEndpoint", "browseId");
            if (id != null) return id;
            return Json.str(details, "navigationEndpoint", "browseEndpoint", "browseId");
        }

        public List<ThumbnailData> thumbnails() {
            List<JsonNode> sources = Json.array(
                    content,
                    "contentImage",
                    "collectionThumbnailViewModel",
                    "primaryThumbnail",
                    "thumbnailViewModel",
                    "image",
                    "sources");
            if (sources != null) return thumbnailsOf(sources);

            JsonNode thumbs = Json.at(content, "thumbnails");
            if (thumbs != null) {
                List<JsonNode> flattened = new ArrayList<>();
                for (JsonNode group : Json.descendantProperties(thumbs, "thumbnails")) {
                    flattened.addAll(Json.arrayOrEmpty(group));
                }
                return thumbnailsOf(flattened);
            }
            return List.of();
        }
    }

    public static final class ChannelData {
        private final JsonNode content;

        ChannelData(JsonNode content) {
            this.content = content;
        }

        public String id() {
            return Json.str(content, "channelId");
        }

        public String title() {
            return titleOf(content);
        }

        public List<ThumbnailData> thumbnails() {
            return thumbnailsOf(Json.arrayOrEmpty(content, "thumbnail", "thumbnails"));
        }
    }
}

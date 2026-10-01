package youtubeexplode.music;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import youtubeexplode.common.Resolution;
import youtubeexplode.common.Thumbnail;
import youtubeexplode.utils.Dates;
import youtubeexplode.utils.Json;
import youtubeexplode.videos.VideoId;

/** Parser for YouTube Music search responses (first pages and continuations alike). */
final class MusicSearchResponse {
    private final JsonNode content;

    private MusicSearchResponse(JsonNode content) {
        this.content = content;
    }

    static MusicSearchResponse parse(String raw) {
        return new MusicSearchResponse(Json.parse(raw));
    }

    /** Token for the next page, or null if this is the last page. */
    String continuationToken() {
        List<JsonNode> nodes = Json.descendantProperties(content, "nextContinuationData");
        return nodes.isEmpty() ? null : Json.str(nodes.get(0), "continuation");
    }

    /** Results in page order. Items that are not songs, videos, albums or artists are skipped. */
    List<MusicSearchResult> results() {
        List<MusicSearchResult> results = new ArrayList<>();
        for (JsonNode item : Json.descendantProperties(content, "musicResponsiveListItemRenderer")) {
            MusicSearchResult result = parseItem(item);
            if (result != null) results.add(result);
        }
        return results;
    }

    // ---- Item parsing ----

    private static MusicSearchResult parseItem(JsonNode item) {
        List<JsonNode> columns = Json.arrayOrEmpty(item, "flexColumns");
        if (columns.isEmpty()) return null;

        List<JsonNode> titleRuns = runs(columns.get(0));
        List<JsonNode> detailRuns = columns.size() > 1 ? runs(columns.get(1)) : List.of();
        String title = joinText(titleRuns);
        if (title.isEmpty()) return null;

        List<Thumbnail> thumbnails = thumbnails(item);

        // Playable items: songs and videos
        String videoId = Json.str(item, "playlistItemData", "videoId");
        if (videoId != null) {
            VideoId id = VideoId.tryParse(videoId).orElse(null);
            if (id == null) return null;

            String videoType = Json.str(
                    titleRuns.isEmpty() ? null : titleRuns.get(0),
                    "navigationEndpoint",
                    "watchEndpoint",
                    "watchEndpointMusicSupportedConfigs",
                    "watchEndpointMusicConfig",
                    "musicVideoType");

            Duration duration = durationIn(detailRuns);

            if ("MUSIC_VIDEO_TYPE_ATV".equals(videoType)) {
                boolean explicit = Json.arrayOrEmpty(item, "badges").stream()
                        .anyMatch(b -> "MUSIC_EXPLICIT_BADGE".equals(Json.str(b, "musicInlineBadgeRenderer", "icon", "iconType")));
                return new MusicSearchResult.Song(
                        id, title, refs(detailRuns, "ARTIST"), firstRef(detailRuns, "ALBUM"), duration, explicit, thumbnails);
            }

            MusicRef channel = firstRef(detailRuns, "USER_CHANNEL");
            if (channel == null) channel = firstRef(detailRuns, "ARTIST");
            return new MusicSearchResult.Video(id, title, channel, viewsIn(detailRuns), duration, thumbnails);
        }

        // Browsable items: albums and artists
        String browseId = Json.str(item, "navigationEndpoint", "browseEndpoint", "browseId");
        String pageType = Json.str(
                item,
                "navigationEndpoint",
                "browseEndpoint",
                "browseEndpointContextSupportedConfigs",
                "browseEndpointContextMusicConfig",
                "pageType");
        if (browseId == null || pageType == null) return null;

        List<String> detail = segments(detailRuns);
        switch (pageType) {
            case "MUSIC_PAGE_TYPE_ALBUM" -> {
                // "Album • Artist • 2010", "Single • Artist • 2013"
                String type = detail.isEmpty() ? "Album" : detail.get(0);
                String year = detail.size() > 1 && detail.get(detail.size() - 1).matches("\\d{4}")
                        ? detail.get(detail.size() - 1)
                        : null;
                return new MusicSearchResult.Album(browseId, title, type, refs(detailRuns, "ARTIST"), year, thumbnails);
            }
            case "MUSIC_PAGE_TYPE_ARTIST" -> {
                // "Artist • 304M monthly audience"
                String subtitle = detail.size() > 1 ? String.join(" • ", detail.subList(1, detail.size())) : null;
                return new MusicSearchResult.Artist(browseId, title, subtitle, thumbnails);
            }
            default -> {
                return null;
            }
        }
    }

    private static List<JsonNode> runs(JsonNode column) {
        return Json.arrayOrEmpty(column, "musicResponsiveListItemFlexColumnRenderer", "text", "runs");
    }

    private static String joinText(List<JsonNode> runs) {
        StringBuilder sb = new StringBuilder();
        for (JsonNode run : runs) {
            String text = Json.str(run, "text");
            if (text != null) sb.append(text);
        }
        return sb.toString();
    }

    /** Splits runs on the " • " separator into text segments. */
    private static List<String> segments(List<JsonNode> runs) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (JsonNode run : runs) {
            String text = Json.str(run, "text");
            if (text == null) continue;
            if (text.strip().equals("•")) {
                result.add(current.toString().strip());
                current.setLength(0);
            } else {
                current.append(text);
            }
        }
        result.add(current.toString().strip());
        return result;
    }

    private static String pageTypeOf(JsonNode run) {
        return Json.str(
                run,
                "navigationEndpoint",
                "browseEndpoint",
                "browseEndpointContextSupportedConfigs",
                "browseEndpointContextMusicConfig",
                "pageType");
    }

    /** Linked runs with the page type "MUSIC_PAGE_TYPE_{kind}". */
    private static List<MusicRef> refs(List<JsonNode> runs, String kind) {
        List<MusicRef> result = new ArrayList<>();
        for (JsonNode run : runs) {
            if (("MUSIC_PAGE_TYPE_" + kind).equals(pageTypeOf(run))) {
                String id = Json.str(run, "navigationEndpoint", "browseEndpoint", "browseId");
                String name = Json.str(run, "text");
                if (id != null && name != null) result.add(new MusicRef(id, name));
            }
        }
        return result;
    }

    private static MusicRef firstRef(List<JsonNode> runs, String kind) {
        List<MusicRef> all = refs(runs, kind);
        return all.isEmpty() ? null : all.get(0);
    }

    private static Duration durationIn(List<JsonNode> runs) {
        for (String segment : segments(runs)) {
            Duration d = Dates.tryParseClock(segment);
            if (d != null) return d;
        }
        return null;
    }

    private static String viewsIn(List<JsonNode> runs) {
        for (String segment : segments(runs)) {
            if (segment.endsWith(" views") || segment.endsWith(" view")) return segment;
        }
        return null;
    }

    private static List<Thumbnail> thumbnails(JsonNode item) {
        List<Thumbnail> result = new ArrayList<>();
        for (JsonNode t : Json.arrayOrEmpty(item, "thumbnail", "musicThumbnailRenderer", "thumbnail", "thumbnails")) {
            String url = Json.str(t, "url");
            Integer width = Json.integer(t, "width");
            Integer height = Json.integer(t, "height");
            if (url != null && width != null && height != null) {
                result.add(new Thumbnail(url, new Resolution(width, height)));
            }
        }
        return result;
    }
}

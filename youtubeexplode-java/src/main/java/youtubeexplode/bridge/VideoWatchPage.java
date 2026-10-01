package youtubeexplode.bridge;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.OffsetDateTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import youtubeexplode.utils.Dates;
import youtubeexplode.utils.Json;
import youtubeexplode.utils.Strings;

public final class VideoWatchPage {
    private static final Pattern LIKES = Pattern.compile("\"\\s*:\\s*\"([\\d,\\.]+) likes\"");
    private static final Pattern LIKES_ALT = Pattern.compile("along with ([\\d,\\.]+) other people\"");
    private static final Pattern DISLIKES = Pattern.compile("\"\\s*:\\s*\"([\\d,\\.]+) dislikes\"");
    private static final Pattern PLAYER_CONFIG = Pattern.compile("ytplayer\\.config\\s*=\\s*(\\{.*\\})");
    private static final Pattern INITIAL_PLAYER_RESPONSE =
            Pattern.compile("var\\s+ytInitialPlayerResponse\\s*=\\s*(\\{.*\\})");

    private final Document content;
    private final String source;

    private VideoWatchPage(Document content, String source) {
        this.content = content;
        this.source = source;
    }

    /** Returns null if the page doesn't look like a video watch page. */
    public static VideoWatchPage tryParse(String raw) {
        // A fresh parser per call keeps this thread-safe
        Document content = Jsoup.parse(raw);
        if (content.body().selectFirst("#player") == null) return null;
        return new VideoWatchPage(content, raw);
    }

    public boolean isAvailable() {
        return content.selectFirst("meta[property=og:url]") != null;
    }

    private String meta(String selector) {
        Element e = content.selectFirst(selector);
        return e != null ? Strings.nullIfBlank(e.attr("content")) : null;
    }

    public OffsetDateTime uploadDate() {
        OffsetDateTime date = Dates.tryParseOffsetDateTime(meta("meta[itemprop=uploadDate]"));
        return date != null ? date : Dates.tryParseOffsetDateTime(meta("meta[itemprop=datePublished]"));
    }

    private static Long count(Pattern pattern, String text) {
        Matcher m = pattern.matcher(text);
        if (!m.find()) return null;
        String digits = Strings.stripNonDigit(m.group(1));
        return Strings.parseLong(digits);
    }

    public Long likeCount() {
        Long likes = count(LIKES, source);
        return likes != null ? likes : count(LIKES_ALT, source);
    }

    public Long dislikeCount() {
        return count(DISLIKES, source);
    }

    private String scriptMatch(Pattern pattern) {
        for (Element script : content.getElementsByTag("script")) {
            Matcher m = pattern.matcher(script.data());
            if (m.find() && !m.group(1).isBlank()) return m.group(1);
        }
        return null;
    }

    private JsonNode playerConfig() {
        String match = scriptMatch(PLAYER_CONFIG);
        return match == null ? null : Json.tryParse(Json.extract(match));
    }

    public PlayerResponse playerResponse() {
        String match = scriptMatch(INITIAL_PLAYER_RESPONSE);
        if (match != null) {
            JsonNode json = Json.tryParse(Json.extract(match));
            if (json != null) return new PlayerResponse(json);
        }

        String legacy = Json.str(playerConfig(), "args", "player_response");
        if (legacy != null) {
            JsonNode json = Json.tryParse(legacy);
            if (json != null) return new PlayerResponse(json);
        }
        return null;
    }
}

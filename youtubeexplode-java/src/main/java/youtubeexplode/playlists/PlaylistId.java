package youtubeexplode.playlists;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import youtubeexplode.utils.Strings;
import youtubeexplode.utils.Url;

/** A syntactically valid YouTube playlist ID. */
public final class PlaylistId {
    private static final Pattern[] URL_PATTERNS = {
        // https://www.youtube.com/playlist?list=PLOU2XLYxmsIJGErt5rrCqaSGTMyyqNt2H
        Pattern.compile("youtube\\..+?/playlist.*?list=(.*?)(?:&|/|$)"),
        // https://www.youtube.com/watch?v=b8m9zhNAgKs&list=PL9tY0BWXOZFuFEG_GtOBZ8-8wbkH-NVAr
        Pattern.compile("youtube\\..+?/watch.*?list=(.*?)(?:&|/|$)"),
        // https://youtu.be/b8m9zhNAgKs/?list=PL9tY0BWXOZFuFEG_GtOBZ8-8wbkH-NVAr
        Pattern.compile("youtu\\.be/.*?/.*?list=(.*?)(?:&|/|$)"),
        // https://www.youtube.com/embed/b8m9zhNAgKs/?list=PL9tY0BWXOZFuFEG_GtOBZ8-8wbkH-NVAr
        Pattern.compile("youtube\\..+?/embed/.*?/.*?list=(.*?)(?:&|/|$)"),
    };

    private final String value;

    private PlaylistId(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    private static boolean isValid(String id) {
        // Playlist IDs vary greatly in length, but they are at least 2 characters long
        return id.length() >= 2 && id.chars().allMatch(c -> Strings.isIdChar((char) c));
    }

    private static String tryExtractId(String url, Pattern pattern) {
        Matcher m = pattern.matcher(url);
        if (!m.find()) return null;
        String id = Url.decode(m.group(1));
        return !id.isBlank() && isValid(id) ? id : null;
    }

    private static String tryNormalize(String playlistIdOrUrl) {
        if (playlistIdOrUrl == null || playlistIdOrUrl.isBlank()) return null;
        if (isValid(playlistIdOrUrl)) return playlistIdOrUrl;

        for (Pattern pattern : URL_PATTERNS) {
            String id = tryExtractId(playlistIdOrUrl, pattern);
            if (id != null) return id;
        }
        return null;
    }

    public static Optional<PlaylistId> tryParse(String playlistIdOrUrl) {
        return Optional.ofNullable(tryNormalize(playlistIdOrUrl)).map(PlaylistId::new);
    }

    public static PlaylistId parse(String playlistIdOrUrl) {
        return tryParse(playlistIdOrUrl)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Invalid YouTube playlist ID or URL '" + playlistIdOrUrl + "'."));
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof PlaylistId other && value.equals(other.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return value;
    }
}

package youtubeexplode.videos;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import youtubeexplode.utils.Strings;
import youtubeexplode.utils.Url;

/** A syntactically valid YouTube video ID. */
public final class VideoId {
    private static final Pattern[] URL_PATTERNS = {
        // https://www.youtube.com/watch?v=yIVRs6YSbOM
        Pattern.compile("youtube\\..+?/watch.*?v=(.*?)(?:&|/|$)"),
        // https://youtu.be/watch?v=Fcds0_MrgNU
        Pattern.compile("youtu\\.be/watch.*?v=(.*?)(?:\\?|&|/|$)"),
        // https://youtu.be/yIVRs6YSbOM
        Pattern.compile("youtu\\.be/(.*?)(?:\\?|&|/|$)"),
        // https://www.youtube.com/embed/yIVRs6YSbOM
        Pattern.compile("youtube\\..+?/embed/(.*?)(?:\\?|&|/|$)"),
        // https://www.youtube.com/shorts/sKL1vjP0tIo
        Pattern.compile("youtube\\..+?/shorts/(.*?)(?:\\?|&|/|$)"),
        // https://www.youtube.com/live/jfKfPfyJRdk
        Pattern.compile("youtube\\..+?/live/(.*?)(?:\\?|&|/|$)"),
    };

    private final String value;

    private VideoId(String value) {
        this.value = value;
    }

    /** Raw ID value. */
    public String getValue() {
        return value;
    }

    private static boolean isValid(String id) {
        return id.length() == 11 && id.chars().allMatch(c -> Strings.isIdChar((char) c));
    }

    private static String tryExtractId(String url, Pattern pattern) {
        Matcher m = pattern.matcher(url);
        if (!m.find()) return null;
        String id = Url.decode(m.group(1));
        return !id.isBlank() && isValid(id) ? id : null;
    }

    private static String tryNormalize(String videoIdOrUrl) {
        if (videoIdOrUrl == null || videoIdOrUrl.isBlank()) return null;
        if (isValid(videoIdOrUrl)) return videoIdOrUrl;

        for (Pattern pattern : URL_PATTERNS) {
            String id = tryExtractId(videoIdOrUrl, pattern);
            if (id != null) return id;
        }
        return null;
    }

    /** Parses a video ID or URL, returning empty on failure. */
    public static Optional<VideoId> tryParse(String videoIdOrUrl) {
        return Optional.ofNullable(tryNormalize(videoIdOrUrl)).map(VideoId::new);
    }

    /** Parses a video ID or URL, throwing {@link IllegalArgumentException} on failure. */
    public static VideoId parse(String videoIdOrUrl) {
        return tryParse(videoIdOrUrl)
                .orElseThrow(() -> new IllegalArgumentException("Invalid YouTube video ID or URL '" + videoIdOrUrl + "'."));
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof VideoId other && value.equals(other.value);
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

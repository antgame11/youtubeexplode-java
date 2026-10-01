package youtubeexplode.channels;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import youtubeexplode.utils.Strings;
import youtubeexplode.utils.Url;

/** A syntactically valid YouTube channel ID. */
public final class ChannelId {
    private static final Pattern URL_PATTERN = Pattern.compile("youtube\\..+?/channel/(.*?)(?:\\?|&|/|$)");

    private final String value;

    private ChannelId(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    private static boolean isValid(String v) {
        return v.startsWith("UC") && v.length() == 24 && v.chars().allMatch(c -> Strings.isIdChar((char) c));
    }

    private static String tryNormalize(String input) {
        if (input == null || input.isBlank()) return null;
        if (isValid(input)) return input;

        Matcher m = URL_PATTERN.matcher(input);
        if (!m.find()) return null;
        String extracted = Url.decode(m.group(1));
        return !extracted.isBlank() && isValid(extracted) ? extracted : null;
    }

    public static Optional<ChannelId> tryParse(String input) {
        return Optional.ofNullable(tryNormalize(input)).map(ChannelId::new);
    }

    public static ChannelId parse(String input) {
        return tryParse(input)
                .orElseThrow(() -> new IllegalArgumentException("Invalid YouTube channel ID or URL '" + input + "'."));
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof ChannelId other && value.equals(other.value);
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

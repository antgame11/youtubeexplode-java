package youtubeexplode.channels;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import youtubeexplode.utils.Strings;
import youtubeexplode.utils.Url;

/** A syntactically valid YouTube channel slug (legacy custom URL, e.g. youtube.com/c/Name). */
public final class ChannelSlug {
    private static final Pattern URL_PATTERN = Pattern.compile("youtube\\..+?/c/(.*?)(?:\\?|&|/|$)");

    private final String value;

    private ChannelSlug(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    private static boolean isValid(String v) {
        return v.chars().allMatch(Character::isLetterOrDigit);
    }

    private static String tryNormalize(String input) {
        if (input == null || input.isBlank()) return null;
        if (isValid(input)) return input;

        Matcher m = URL_PATTERN.matcher(input);
        if (!m.find()) return null;
        String extracted = Url.decode(m.group(1));
        return !extracted.isBlank() && isValid(extracted) ? extracted : null;
    }

    public static Optional<ChannelSlug> tryParse(String input) {
        return Optional.ofNullable(tryNormalize(input)).map(ChannelSlug::new);
    }

    public static ChannelSlug parse(String input) {
        return tryParse(input)
                .orElseThrow(() -> new IllegalArgumentException("Invalid YouTube channel slug or legacy custom URL '" + input + "'."));
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof ChannelSlug other && value.equals(other.value);
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

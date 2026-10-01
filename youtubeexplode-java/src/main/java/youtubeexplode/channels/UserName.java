package youtubeexplode.channels;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import youtubeexplode.utils.Strings;
import youtubeexplode.utils.Url;

/** A syntactically valid YouTube user name (legacy, e.g. youtube.com/user/Name). */
public final class UserName {
    private static final Pattern URL_PATTERN = Pattern.compile("youtube\\..+?/user/(.*?)(?:\\?|&|/|$)");

    private final String value;

    private UserName(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    private static boolean isValid(String v) {
        return v.length() <= 20 && v.chars().allMatch(Character::isLetterOrDigit);
    }

    private static String tryNormalize(String input) {
        if (input == null || input.isBlank()) return null;
        if (isValid(input)) return input;

        Matcher m = URL_PATTERN.matcher(input);
        if (!m.find()) return null;
        String extracted = Url.decode(m.group(1));
        return !extracted.isBlank() && isValid(extracted) ? extracted : null;
    }

    public static Optional<UserName> tryParse(String input) {
        return Optional.ofNullable(tryNormalize(input)).map(UserName::new);
    }

    public static UserName parse(String input) {
        return tryParse(input)
                .orElseThrow(() -> new IllegalArgumentException("Invalid YouTube user name or profile URL '" + input + "'."));
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof UserName other && value.equals(other.value);
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

package youtubeexplode.utils;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Date and duration parsing helpers. */
public final class Dates {
    private static final Pattern CLOCK = Pattern.compile("^(?:(\\d{1,2}):)?(\\d{1,2}):(\\d{2})$");

    private Dates() {}

    /** Parses ISO-8601 date-times (with or without offset) and plain dates. Returns null on failure. */
    public static OffsetDateTime tryParseOffsetDateTime(String s) {
        if (s == null || s.isBlank()) return null;
        String v = s.trim();
        try {
            return OffsetDateTime.parse(v);
        } catch (DateTimeParseException ignored) {
        }
        try {
            return LocalDateTime.parse(v).atOffset(ZoneOffset.UTC);
        } catch (DateTimeParseException ignored) {
        }
        try {
            return LocalDate.parse(v).atStartOfDay().atOffset(ZoneOffset.UTC);
        } catch (DateTimeParseException ignored) {
        }
        return null;
    }

    /**
     * Parses clock-style durations: m:ss, mm:ss, h:mm:ss, hh:mm:ss. Components must be in range
     * (minutes and seconds below 60), matching exact-parse semantics.
     */
    public static Duration tryParseClock(String s) {
        if (s == null) return null;
        Matcher m = CLOCK.matcher(s);
        if (!m.matches()) return null;

        int hours = m.group(1) != null ? Integer.parseInt(m.group(1)) : 0;
        int minutes = Integer.parseInt(m.group(2));
        int seconds = Integer.parseInt(m.group(3));
        if (hours > 23 || minutes > 59 || seconds > 59) return null;

        return Duration.ofHours(hours).plusMinutes(minutes).plusSeconds(seconds);
    }

    public static Duration ofSecondsDouble(double seconds) {
        return Duration.ofNanos((long) (seconds * 1_000_000_000L));
    }

    public static Duration ofMillisDouble(double millis) {
        return Duration.ofNanos((long) (millis * 1_000_000L));
    }
}

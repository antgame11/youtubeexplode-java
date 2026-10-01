package youtubeexplode.videos.closedcaptions;

import java.util.Locale;

/** Language information. Equality is determined by the (case-insensitive) code. */
public final class Language {
    private final String code;
    private final String name;

    public Language(String code, String name) {
        this.code = code;
        this.name = name;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Language other && code.equalsIgnoreCase(other.code);
    }

    @Override
    public int hashCode() {
        return code.toLowerCase(Locale.ROOT).hashCode();
    }

    @Override
    public String toString() {
        return code + " (" + name + ")";
    }
}

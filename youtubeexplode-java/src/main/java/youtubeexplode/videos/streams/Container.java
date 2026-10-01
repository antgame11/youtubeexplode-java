package youtubeexplode.videos.streams;

import java.util.Locale;
import java.util.Set;

/** Stream container type. Equality is case-insensitive. */
public final class Container {
    public static final Container MP3 = new Container("mp3");
    public static final Container MP4 = new Container("mp4");
    public static final Container WEBM = new Container("webm");
    public static final Container TGPP = new Container("3gpp");

    private static final Set<String> AUDIO_ONLY = Set.of("mp3", "m4a", "wav", "wma", "ogg", "aac", "opus");

    private final String name;

    public Container(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    /** Whether this container is known to be audio-only. */
    public boolean isAudioOnly() {
        return AUDIO_ONLY.contains(name.toLowerCase(Locale.ROOT));
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Container other && name.equalsIgnoreCase(other.name);
    }

    @Override
    public int hashCode() {
        return name.toLowerCase(Locale.ROOT).hashCode();
    }

    @Override
    public String toString() {
        return name;
    }
}

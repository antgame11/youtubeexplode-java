package youtubeexplode.music;

/** Reference to a YouTube Music entity (artist, album or channel) by ID and display name. */
public record MusicRef(String id, String name) {
    @Override
    public String toString() {
        return name;
    }
}

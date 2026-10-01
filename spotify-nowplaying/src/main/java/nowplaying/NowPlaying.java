package nowplaying;

import java.util.List;

/** The track currently playing on Spotify. */
public record NowPlaying(
        String trackId,
        String title,
        List<String> artists,
        String album,
        long durationMs,
        boolean isPlaying,
        long progressMs,
        String imageUrl) {
    public NowPlaying(String trackId, String title, List<String> artists, String album, long durationMs, boolean isPlaying) {
        this(trackId, title, artists, album, durationMs, isPlaying, 0, null);
    }

    public String artistLine() {
        return String.join(", ", artists);
    }

    @Override
    public String toString() {
        return artistLine() + " - " + title;
    }
}

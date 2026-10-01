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
        String imageUrl,
        String thumbUrl) {
    public NowPlaying(String trackId, String title, List<String> artists, String album, long durationMs, boolean isPlaying) {
        this(trackId, title, artists, album, durationMs, isPlaying, 0, null, null);
    }

    public NowPlaying(
            String trackId,
            String title,
            List<String> artists,
            String album,
            long durationMs,
            boolean isPlaying,
            long progressMs,
            String imageUrl) {
        this(trackId, title, artists, album, durationMs, isPlaying, progressMs, imageUrl, imageUrl);
    }

    public String artistLine() {
        return String.join(", ", artists);
    }

    @Override
    public String toString() {
        return artistLine() + " - " + title;
    }
}

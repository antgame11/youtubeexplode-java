package youtubeexplode.common;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import youtubeexplode.videos.VideoId;

/** Thumbnail image. */
public final class Thumbnail {
    private final String url;
    private final Resolution resolution;

    public Thumbnail(String url, Resolution resolution) {
        this.url = url;
        this.resolution = resolution;
    }

    public String getUrl() {
        return url;
    }

    public Resolution getResolution() {
        return resolution;
    }

    @Override
    public String toString() {
        return "Thumbnail (" + resolution + ")";
    }

    /** Default thumbnail set available for every video. */
    public static List<Thumbnail> defaultSet(VideoId videoId) {
        return List.of(
                new Thumbnail("https://img.youtube.com/vi/" + videoId + "/default.jpg", new Resolution(120, 90)),
                new Thumbnail("https://img.youtube.com/vi/" + videoId + "/mqdefault.jpg", new Resolution(320, 180)),
                new Thumbnail("https://img.youtube.com/vi/" + videoId + "/hqdefault.jpg", new Resolution(480, 360)));
    }

    public static Optional<Thumbnail> tryGetWithHighestResolution(Collection<Thumbnail> thumbnails) {
        return thumbnails.stream().max(Comparator.comparingInt(t -> t.getResolution().area()));
    }

    public static Thumbnail getWithHighestResolution(Collection<Thumbnail> thumbnails) {
        return tryGetWithHighestResolution(thumbnails)
                .orElseThrow(() -> new NoSuchElementException("Input thumbnail collection is empty."));
    }
}

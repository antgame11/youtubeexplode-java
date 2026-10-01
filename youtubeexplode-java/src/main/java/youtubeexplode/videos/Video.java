package youtubeexplode.videos;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import youtubeexplode.common.Author;
import youtubeexplode.common.Thumbnail;

/** Metadata associated with a YouTube video. */
public final class Video implements VideoInfo {
    private final VideoId id;
    private final String title;
    private final Author author;
    private final OffsetDateTime uploadDate;
    private final String description;
    private final Duration duration;
    private final List<Thumbnail> thumbnails;
    private final List<String> keywords;
    private final Engagement engagement;

    public Video(
            VideoId id,
            String title,
            Author author,
            OffsetDateTime uploadDate,
            String description,
            Duration duration,
            List<Thumbnail> thumbnails,
            List<String> keywords,
            Engagement engagement) {
        this.id = id;
        this.title = title;
        this.author = author;
        this.uploadDate = uploadDate;
        this.description = description;
        this.duration = duration;
        this.thumbnails = List.copyOf(thumbnails);
        this.keywords = List.copyOf(keywords);
        this.engagement = engagement;
    }

    @Override
    public VideoId getId() {
        return id;
    }

    @Override
    public String getUrl() {
        return "https://www.youtube.com/watch?v=" + id;
    }

    @Override
    public String getTitle() {
        return title;
    }

    @Override
    public Author getAuthor() {
        return author;
    }

    public OffsetDateTime getUploadDate() {
        return uploadDate;
    }

    public String getDescription() {
        return description;
    }

    /** May be null if the video is a currently ongoing live stream. */
    @Override
    public Duration getDuration() {
        return duration;
    }

    @Override
    public List<Thumbnail> getThumbnails() {
        return thumbnails;
    }

    /** Available search keywords for the video. */
    public List<String> getKeywords() {
        return keywords;
    }

    public Engagement getEngagement() {
        return engagement;
    }

    @Override
    public String toString() {
        return "Video (" + title + ")";
    }
}

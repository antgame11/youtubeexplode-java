package youtubeexplode.playlists;

import java.time.Duration;
import java.util.List;
import youtubeexplode.common.Author;
import youtubeexplode.common.Thumbnail;
import youtubeexplode.videos.VideoId;
import youtubeexplode.videos.VideoInfo;

/** Metadata associated with a video included in a playlist. */
public final class PlaylistVideo implements VideoInfo {
    private final PlaylistId playlistId;
    private final VideoId id;
    private final String title;
    private final Author author;
    private final Duration duration;
    private final List<Thumbnail> thumbnails;

    public PlaylistVideo(
            PlaylistId playlistId,
            VideoId id,
            String title,
            Author author,
            Duration duration,
            List<Thumbnail> thumbnails) {
        this.playlistId = playlistId;
        this.id = id;
        this.title = title;
        this.author = author;
        this.duration = duration;
        this.thumbnails = List.copyOf(thumbnails);
    }

    public PlaylistId getPlaylistId() {
        return playlistId;
    }

    @Override
    public VideoId getId() {
        return id;
    }

    @Override
    public String getUrl() {
        return "https://www.youtube.com/watch?v=" + id + "&list=" + playlistId;
    }

    @Override
    public String getTitle() {
        return title;
    }

    @Override
    public Author getAuthor() {
        return author;
    }

    @Override
    public Duration getDuration() {
        return duration;
    }

    @Override
    public List<Thumbnail> getThumbnails() {
        return thumbnails;
    }

    @Override
    public String toString() {
        return "Video (" + title + ")";
    }
}

package youtubeexplode.playlists;

import java.util.List;
import youtubeexplode.common.Author;
import youtubeexplode.common.Thumbnail;

/** Metadata associated with a YouTube playlist. */
public final class Playlist implements PlaylistInfo {
    private final PlaylistId id;
    private final String title;
    private final Author author;
    private final String description;
    private final Integer count;
    private final List<Thumbnail> thumbnails;

    public Playlist(
            PlaylistId id, String title, Author author, String description, Integer count, List<Thumbnail> thumbnails) {
        this.id = id;
        this.title = title;
        this.author = author;
        this.description = description;
        this.count = count;
        this.thumbnails = List.copyOf(thumbnails);
    }

    @Override
    public PlaylistId getId() {
        return id;
    }

    @Override
    public String getUrl() {
        return "https://www.youtube.com/playlist?list=" + id;
    }

    @Override
    public String getTitle() {
        return title;
    }

    @Override
    public Author getAuthor() {
        return author;
    }

    public String getDescription() {
        return description;
    }

    /** Number of videos in the playlist, or null if unknown. */
    public Integer getCount() {
        return count;
    }

    @Override
    public List<Thumbnail> getThumbnails() {
        return thumbnails;
    }

    @Override
    public String toString() {
        return "Playlist (" + title + ")";
    }
}

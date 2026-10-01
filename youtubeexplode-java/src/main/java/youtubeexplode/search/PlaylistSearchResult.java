package youtubeexplode.search;

import java.util.List;
import youtubeexplode.common.Author;
import youtubeexplode.common.Thumbnail;
import youtubeexplode.playlists.PlaylistId;
import youtubeexplode.playlists.PlaylistInfo;

/** Metadata associated with a playlist returned by a search query. */
public final class PlaylistSearchResult implements SearchResult, PlaylistInfo {
    private final PlaylistId id;
    private final String title;
    private final Author author;
    private final List<Thumbnail> thumbnails;

    public PlaylistSearchResult(PlaylistId id, String title, Author author, List<Thumbnail> thumbnails) {
        this.id = id;
        this.title = title;
        this.author = author;
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

    @Override
    public List<Thumbnail> getThumbnails() {
        return thumbnails;
    }

    @Override
    public String toString() {
        return "Playlist (" + title + ")";
    }
}

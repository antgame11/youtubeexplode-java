package youtubeexplode.playlists;

import java.util.List;
import youtubeexplode.common.Author;
import youtubeexplode.common.Thumbnail;

/** Properties shared by playlist metadata resolved from different sources. */
public interface PlaylistInfo {
    PlaylistId getId();

    String getUrl();

    String getTitle();

    /** Playlist author. May be null for system playlists. */
    Author getAuthor();

    List<Thumbnail> getThumbnails();
}

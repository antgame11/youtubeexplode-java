package youtubeexplode.bridge;

import java.util.List;

/** Playlist metadata shared between the browse and next responses. */
public interface PlaylistData {
    String title();

    String author();

    String channelId();

    String description();

    Integer count();

    List<ThumbnailData> thumbnails();
}

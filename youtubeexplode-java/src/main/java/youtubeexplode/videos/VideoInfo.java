package youtubeexplode.videos;

import java.time.Duration;
import java.util.List;
import youtubeexplode.common.Author;
import youtubeexplode.common.Thumbnail;

/** Properties shared by video metadata resolved from different sources. */
public interface VideoInfo {
    VideoId getId();

    String getUrl();

    String getTitle();

    Author getAuthor();

    /** May be null if the video is a currently ongoing live stream. */
    Duration getDuration();

    List<Thumbnail> getThumbnails();
}

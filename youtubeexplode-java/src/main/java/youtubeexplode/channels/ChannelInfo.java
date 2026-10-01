package youtubeexplode.channels;

import java.util.List;
import youtubeexplode.common.Thumbnail;

/** Properties shared by channel metadata resolved from different sources. */
public interface ChannelInfo {
    ChannelId getId();

    String getUrl();

    String getTitle();

    List<Thumbnail> getThumbnails();
}

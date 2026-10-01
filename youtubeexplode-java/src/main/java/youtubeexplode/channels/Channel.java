package youtubeexplode.channels;

import java.util.List;
import youtubeexplode.common.Thumbnail;

/** Metadata associated with a YouTube channel. */
public final class Channel implements ChannelInfo {
    private final ChannelId id;
    private final String title;
    private final List<Thumbnail> thumbnails;

    public Channel(ChannelId id, String title, List<Thumbnail> thumbnails) {
        this.id = id;
        this.title = title;
        this.thumbnails = List.copyOf(thumbnails);
    }

    @Override
    public ChannelId getId() {
        return id;
    }

    @Override
    public String getUrl() {
        return "https://www.youtube.com/channel/" + id;
    }

    @Override
    public String getTitle() {
        return title;
    }

    @Override
    public List<Thumbnail> getThumbnails() {
        return thumbnails;
    }

    @Override
    public String toString() {
        return "Channel (" + title + ")";
    }
}

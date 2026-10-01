package youtubeexplode.common;

import youtubeexplode.channels.ChannelId;

/** YouTube channel that published a video or playlist. */
public final class Author {
    private final ChannelId channelId;
    private final String channelTitle;

    public Author(ChannelId channelId, String channelTitle) {
        this.channelId = channelId;
        this.channelTitle = channelTitle;
    }

    public ChannelId getChannelId() {
        return channelId;
    }

    public String getChannelUrl() {
        return "https://www.youtube.com/channel/" + channelId;
    }

    public String getChannelTitle() {
        return channelTitle;
    }

    @Override
    public String toString() {
        return channelTitle;
    }
}

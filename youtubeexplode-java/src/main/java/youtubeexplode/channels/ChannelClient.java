package youtubeexplode.channels;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import youtubeexplode.YoutubeHttp;
import youtubeexplode.bridge.ChannelPage;
import youtubeexplode.common.Resolution;
import youtubeexplode.common.Thumbnail;
import youtubeexplode.exceptions.YoutubeExplodeException;
import youtubeexplode.playlists.PlaylistClient;
import youtubeexplode.playlists.PlaylistId;
import youtubeexplode.playlists.PlaylistVideo;
import youtubeexplode.utils.Strings;

/** Operations related to YouTube channels. */
public final class ChannelClient {
    private static final Pattern LOGO_SIZE = Pattern.compile("\\bs(\\d+)\\b");

    private final YoutubeHttp http;
    private final ChannelController controller;

    public ChannelClient(YoutubeHttp http) {
        this.http = http;
        this.controller = new ChannelController(http);
    }

    private Channel get(ChannelPage channelPage) {
        String channelId = channelPage.id();
        if (channelId == null) throw new YoutubeExplodeException("Failed to extract the channel ID.");

        String title = channelPage.title();
        if (title == null) throw new YoutubeExplodeException("Failed to extract the channel title.");

        String logoUrl = channelPage.logoUrl();
        if (logoUrl == null) throw new YoutubeExplodeException("Failed to extract the channel logo URL.");

        Integer logoSize = null;
        Matcher m = LOGO_SIZE.matcher(logoUrl);
        while (m.find()) logoSize = Strings.parseInt(m.group(1));
        int size = logoSize != null ? logoSize : 100;

        return new Channel(
                ChannelId.parse(channelId), title, List.of(new Thumbnail(logoUrl, new Resolution(size, size))));
    }

    /** Gets the metadata associated with the specified channel. */
    public Channel get(ChannelId channelId) {
        // Special case for the "Movies & TV" channel, which has a custom page
        if (channelId.getValue().equals("UCuVPpxrm2VAgpH3Ktln4HXg")) {
            return new Channel(
                    channelId,
                    "Movies & TV",
                    List.of(new Thumbnail(
                            "https://www.gstatic.com/youtube/img/tvfilm/clapperboard_profile.png",
                            new Resolution(1024, 1024))));
        }

        return get(controller.getChannelPage(channelId));
    }

    public Channel get(String channelIdOrUrl) {
        return get(ChannelId.parse(channelIdOrUrl));
    }

    /** Gets the metadata associated with the channel of the specified user. */
    public Channel getByUser(UserName userName) {
        return get(controller.getChannelPage(userName));
    }

    /** Gets the metadata associated with the channel identified by the specified slug or legacy custom URL. */
    public Channel getBySlug(ChannelSlug channelSlug) {
        return get(controller.getChannelPage(channelSlug));
    }

    /** Gets the metadata associated with the channel identified by the specified handle. */
    public Channel getByHandle(ChannelHandle channelHandle) {
        return get(controller.getChannelPage(channelHandle));
    }

    /** Enumerates videos uploaded by the specified channel as a lazy stream. */
    public Stream<PlaylistVideo> getUploads(ChannelId channelId) {
        // Replace 'UC' in the channel ID with 'UU'
        PlaylistId playlistId = PlaylistId.parse("UU" + channelId.getValue().substring(2));
        return new PlaylistClient(http).getVideos(playlistId);
    }
}

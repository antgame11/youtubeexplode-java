package youtubeexplode.channels;

import youtubeexplode.YoutubeHttp;
import youtubeexplode.bridge.ChannelPage;
import youtubeexplode.exceptions.YoutubeExplodeException;

class ChannelController {
    private final YoutubeHttp http;

    ChannelController(YoutubeHttp http) {
        this.http = http;
    }

    private ChannelPage getChannelPage(String channelRoute) {
        for (int retriesRemaining = 5; ; retriesRemaining--) {
            ChannelPage channelPage = ChannelPage.tryParse(http.getString("https://www.youtube.com/" + channelRoute));

            if (channelPage == null) {
                if (retriesRemaining > 0) continue;
                throw new YoutubeExplodeException("Channel page is broken. Please try again in a few minutes.");
            }

            return channelPage;
        }
    }

    ChannelPage getChannelPage(ChannelId channelId) {
        return getChannelPage("channel/" + channelId);
    }

    ChannelPage getChannelPage(UserName userName) {
        return getChannelPage("user/" + userName);
    }

    ChannelPage getChannelPage(ChannelSlug channelSlug) {
        return getChannelPage("c/" + channelSlug);
    }

    ChannelPage getChannelPage(ChannelHandle channelHandle) {
        return getChannelPage("@" + channelHandle);
    }
}

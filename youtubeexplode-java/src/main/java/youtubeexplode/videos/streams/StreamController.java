package youtubeexplode.videos.streams;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import youtubeexplode.YoutubeHttp;
import youtubeexplode.bridge.DashManifest;
import youtubeexplode.bridge.PlayerSource;
import youtubeexplode.exceptions.YoutubeExplodeException;
import youtubeexplode.videos.VideoController;

class StreamController extends VideoController {
    private static final Pattern PLAYER_VERSION = Pattern.compile("player\\\\?/([0-9a-fA-F]{8})\\\\?/");

    StreamController(YoutubeHttp http) {
        super(http);
    }

    PlayerSource getPlayerSource() {
        String iframe = http.getString("https://www.youtube.com/iframe_api");

        Matcher m = PLAYER_VERSION.matcher(iframe);
        if (!m.find() || m.group(1).isBlank()) throw new YoutubeExplodeException("Failed to extract the player version.");

        return PlayerSource.parse(
                http.getString("https://www.youtube.com/s/player/" + m.group(1) + "/player_ias.vflset/en_US/base.js"));
    }

    DashManifest getDashManifest(String url) {
        return DashManifest.parse(http.getString(url));
    }
}

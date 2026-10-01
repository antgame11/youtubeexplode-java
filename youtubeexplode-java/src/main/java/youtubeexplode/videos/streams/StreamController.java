package youtubeexplode.videos.streams;

import youtubeexplode.YoutubeHttp;
import youtubeexplode.bridge.DashManifest;
import youtubeexplode.videos.VideoController;

class StreamController extends VideoController {
    StreamController(YoutubeHttp http) {
        super(http);
    }

    DashManifest getDashManifest(String url) {
        return DashManifest.parse(http.getString(url));
    }
}

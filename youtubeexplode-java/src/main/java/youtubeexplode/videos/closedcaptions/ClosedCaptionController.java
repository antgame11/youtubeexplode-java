package youtubeexplode.videos.closedcaptions;

import youtubeexplode.YoutubeHttp;
import youtubeexplode.bridge.ClosedCaptionTrackResponse;
import youtubeexplode.utils.Url;
import youtubeexplode.videos.VideoController;

class ClosedCaptionController extends VideoController {
    ClosedCaptionController(YoutubeHttp http) {
        super(http);
    }

    ClosedCaptionTrackResponse getClosedCaptionTrackResponse(String url) {
        // Enforce known format
        String urlWithFormat = Url.setQueryParameter(Url.setQueryParameter(url, "format", "3"), "fmt", "3");
        return ClosedCaptionTrackResponse.parse(http.getString(urlWithFormat));
    }
}

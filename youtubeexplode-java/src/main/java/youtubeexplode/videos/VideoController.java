package youtubeexplode.videos;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import youtubeexplode.YoutubeHttp;
import youtubeexplode.bridge.PlayerResponse;
import youtubeexplode.bridge.VideoWatchPage;
import youtubeexplode.exceptions.VideoUnavailableException;
import youtubeexplode.exceptions.VideoUnplayableException;
import youtubeexplode.exceptions.YoutubeExplodeException;
import youtubeexplode.utils.Json;
import youtubeexplode.utils.Strings;

public class VideoController {
    protected final YoutubeHttp http;
    private volatile String visitorData;

    public VideoController(YoutubeHttp http) {
        this.http = http;
    }

    private String resolveVisitorData() {
        String cached = visitorData;
        if (!Strings.isBlank(cached)) return cached;

        String body = http.string(YoutubeHttp.Request.get(
                "https://www.youtube.com/sw.js_data",
                Map.of(
                        "Accept", "application/json",
                        "User-Agent", "com.google.android.youtube/20.10.38 (Linux; U; ANDROID 11) gzip")));

        if (body.startsWith(")]}'")) body = body.substring(4);

        // This is just an ordered (but unstructured) blob of data
        JsonNode json = Json.parse(body);
        String value = Json.str(json, 0, 2, 0, 0, 13);
        if (Strings.isBlank(value)) throw new YoutubeExplodeException("Failed to resolve visitor data.");

        visitorData = value;
        return value;
    }

    public VideoWatchPage getVideoWatchPage(VideoId videoId) {
        for (int retriesRemaining = 5; ; retriesRemaining--) {
            VideoWatchPage watchPage = VideoWatchPage.tryParse(
                    http.getString("https://www.youtube.com/watch?v=" + videoId + "&bpctr=9999999999"));

            if (watchPage == null) {
                if (retriesRemaining > 0) continue;
                throw new YoutubeExplodeException("Video watch page is broken. Please try again in a few minutes.");
            }

            if (!watchPage.isAvailable()) throw new VideoUnavailableException("Video '" + videoId + "' is not available.");

            return watchPage;
        }
    }

    private PlayerResponse requestPlayerResponse(VideoId videoId, String body, String userAgent) {
        String raw = http.string(YoutubeHttp.Request.postJson(
                "https://www.youtube.com/youtubei/v1/player", body, Map.of("User-Agent", userAgent)));

        PlayerResponse playerResponse = PlayerResponse.parse(raw);

        if (!playerResponse.isAvailable()) throw new VideoUnavailableException("Video '" + videoId + "' is not available.");
        if (!playerResponse.isPlayable()) throw new VideoUnplayableException("Video '" + videoId + "' is unplayable.");

        return playerResponse;
    }

    private PlayerResponse getPlayerResponseForVisionOs(VideoId videoId, String visitorData) {
        String body = """
                {
                  "videoId": %s,
                  "contentCheckOk": true,
                  "racyCheckOk": true,
                  "context": {
                    "client": {
                      "clientName": "VISIONOS",
                      "clientVersion": "1.02",
                      "deviceMake": "Apple",
                      "deviceModel": "RealityDevice17,1",
                      "osName": "visionOS",
                      "osVersion": "26.5.23O471",
                      "visitorData": %s,
                      "hl": "en",
                      "gl": "US",
                      "utcOffsetMinutes": 0
                    }
                  }
                }
                """.formatted(Json.encode(videoId.getValue()), Json.encode(visitorData));

        return requestPlayerResponse(
                videoId,
                body,
                "Mozilla/5.0 (Macintosh; Intel Mac OS X 15_7_3) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.0 Safari/605.1.15");
    }

    private PlayerResponse getPlayerResponseForAndroid(VideoId videoId, String visitorData) {
        String body = """
                {
                  "videoId": %s,
                  "contentCheckOk": true,
                  "racyCheckOk": true,
                  "context": {
                    "client": {
                      "clientName": "ANDROID",
                      "clientVersion": "21.26.364",
                      "androidSdkVersion": 30,
                      "osName": "Android",
                      "osVersion": "11",
                      "visitorData": %s,
                      "hl": "en",
                      "gl": "US",
                      "utcOffsetMinutes": 0
                    }
                  }
                }
                """.formatted(Json.encode(videoId.getValue()), Json.encode(visitorData));

        return requestPlayerResponse(
                videoId, body, "com.google.android.youtube/21.26.364 (Linux; U; Android 11) gzip");
    }

    private PlayerResponse getPlayerResponseForTv(VideoId videoId, String visitorData, String signatureTimestamp) {
        String body = """
                {
                  "videoId": %s,
                  "context": {
                    "client": {
                      "clientName": "TVHTML5_SIMPLY_EMBEDDED_PLAYER",
                      "clientVersion": "2.0",
                      "visitorData": %s,
                      "hl": "en",
                      "gl": "US",
                      "utcOffsetMinutes": 0
                    },
                    "thirdParty": {
                      "embedUrl": "https://www.youtube.com"
                    }
                  },
                  "playbackContext": {
                    "contentPlaybackContext": {
                      "signatureTimestamp": %s
                    }
                  }
                }
                """.formatted(Json.encode(videoId.getValue()), Json.encode(visitorData), Json.encode(signatureTimestamp));

        return requestPlayerResponse(
                videoId,
                body,
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/89.0.4389.114 Safari/537.36");
    }

    public PlayerResponse getPlayerResponse(VideoId videoId, String signatureTimestamp) {
        String visitorData = resolveVisitorData();

        // We use the TV client for age-restricted videos as it circumvents the age gate, but it
        // imposes signature ciphering, so we only use this client if we have a signature timestamp.
        if (!Strings.isBlank(signatureTimestamp)) return getPlayerResponseForTv(videoId, visitorData, signatureTimestamp);

        try {
            // VisionOS is the primary client, as it works for most videos
            return getPlayerResponseForVisionOs(videoId, visitorData);
        } catch (VideoUnplayableException ex) {
            // Android is used as a fallback as it works for certain other videos, such as videos intended for kids
            return getPlayerResponseForAndroid(videoId, visitorData);
        }
    }

    public PlayerResponse getPlayerResponse(VideoId videoId) {
        return getPlayerResponse(videoId, null);
    }
}

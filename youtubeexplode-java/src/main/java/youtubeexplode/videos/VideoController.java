package youtubeexplode.videos;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import youtubeexplode.YoutubeHttp;
import youtubeexplode.bridge.PlayerResponse;
import youtubeexplode.bridge.PlayerSource;
import youtubeexplode.bridge.VideoWatchPage;
import youtubeexplode.exceptions.HttpStatusException;
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

    private static final Pattern PLAYER_VERSION = Pattern.compile("player\\\\?/([0-9a-fA-F]{8})\\\\?/");

    private PlayerSource playerSource;

    /** The current player script (base.js). Fetched once per controller. */
    public synchronized PlayerSource getPlayerSource() {
        if (playerSource != null) return playerSource;

        String iframe = http.getString("https://www.youtube.com/iframe_api");
        Matcher m = PLAYER_VERSION.matcher(iframe);
        if (!m.find() || m.group(1).isBlank()) throw new YoutubeExplodeException("Failed to extract the player version.");

        return playerSource = PlayerSource.parse(
                http.getString("https://www.youtube.com/s/player/" + m.group(1) + "/player_ias.vflset/en_US/base.js"));
    }

    private String resolveVisitorData() {
        String cached = visitorData;
        if (!Strings.isBlank(cached)) return cached;

        String body = http.string(YoutubeHttp.Request.get(
                        "https://www.youtube.com/sw.js_data",
                        Map.of(
                                "Accept", "application/json",
                                "User-Agent", "com.google.android.youtube/20.10.38 (Linux; U; ANDROID 11) gzip"))
                .asAnonymous());

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

    private PlayerResponse requestPlayerResponse(VideoId videoId, String body, String userAgent, YoutubeHttp.Login login) {
        YoutubeHttp.Request request = YoutubeHttp.Request.postJson(
                "https://www.youtube.com/youtubei/v1/player", body, Map.of("User-Agent", userAgent));
        String raw = http.string(switch (login) {
            case NONE -> request.asAnonymous();
            case COOKIES_ONLY -> request.cookiesOnly();
            case FULL -> request;
        });

        PlayerResponse playerResponse = PlayerResponse.parse(raw);

        if (!playerResponse.isAvailable()) {
            throw new VideoUnavailableException("Video '" + videoId + "' is not available." + youtubeSaid(playerResponse));
        }
        if (!playerResponse.isPlayable()) {
            throw new VideoUnplayableException("Video '" + videoId + "' is unplayable." + youtubeSaid(playerResponse));
        }

        return playerResponse;
    }

    /**
     * What YouTube itself said, so "not available" does not hide the real cause (for example a bot check
     * that blocks your IP address and needs a login or a different network).
     */
    private static String youtubeSaid(PlayerResponse r) {
        String status = r.playabilityStatusText();
        String reason = r.playabilityError();
        if (status == null && reason == null) return " YouTube returned no video details (often a blocked IP address).";
        return " YouTube said: " + (status != null ? status : "?") + (reason != null ? " - " + reason : "") + ".";
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
                "Mozilla/5.0 (Macintosh; Intel Mac OS X 15_7_3) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.0 Safari/605.1.15",
                YoutubeHttp.Login.NONE);
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
                videoId, body, "com.google.android.youtube/21.26.364 (Linux; U; Android 11) gzip", YoutubeHttp.Login.NONE);
    }

    /**
     * The TV client. It honors a web login (cookies only: with the Authorization header too YouTube answers
     * HTTP 400). It needs the player's signature timestamp, and its streams are ciphered with a scheme that
     * only a JavaScript engine running YouTube's player script can solve, so this library cannot download
     * from it today. (The "embedded" TV client the original library used here was retired by YouTube: it
     * answers "YouTube is no longer supported in this application".)
     */
    private PlayerResponse getPlayerResponseForTv(VideoId videoId, String visitorData, String signatureTimestamp) {
        String sts = !Strings.isBlank(signatureTimestamp) ? signatureTimestamp : getPlayerSource().signatureTimestamp();
        if (Strings.isBlank(sts)) throw new YoutubeExplodeException("Failed to extract the signature timestamp.");

        String body = """
                {
                  "videoId": %s,
                  "contentCheckOk": true,
                  "racyCheckOk": true,
                  "context": {
                    "client": {
                      "clientName": "TVHTML5_SIMPLY",
                      "clientVersion": "1.0",
                      "visitorData": %s,
                      "hl": "en",
                      "gl": "US",
                      "utcOffsetMinutes": 0
                    }
                  },
                  "playbackContext": {
                    "contentPlaybackContext": {
                      "signatureTimestamp": %s
                    }
                  }
                }
                """.formatted(Json.encode(videoId.getValue()), Json.encode(visitorData), Json.encode(sts));

        return requestPlayerResponse(
                videoId,
                body,
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/89.0.4389.114 Safari/537.36",
                YoutubeHttp.Login.COOKIES_ONLY);
    }

    public PlayerResponse getPlayerResponse(VideoId videoId, String signatureTimestamp) {
        String visitorData = resolveVisitorData();

        // We use the TV client for age-restricted videos as it circumvents the age gate, but it
        // imposes signature ciphering, so we only use this client if we have a signature timestamp.
        if (!Strings.isBlank(signatureTimestamp)) return getPlayerResponseForTv(videoId, visitorData, signatureTimestamp);

        // The clients to try, in order. Only these return plain download URLs. They are anonymous: the mobile
        // clients ignore a web login (and reject one that sends cookies together with Authorization).
        List<String> names = List.of("VisionOS", "Android");
        List<Supplier<PlayerResponse>> attempts = List.of(
                () -> getPlayerResponseForVisionOs(videoId, visitorData),
                () -> getPlayerResponseForAndroid(videoId, visitorData)); // works for some videos VisionOS refuses, e.g. for kids

        List<String> failures = new ArrayList<>();
        RuntimeException last = null;
        for (int i = 0; i < attempts.size(); i++) {
            try {
                return attempts.get(i).get();
            } catch (YoutubeExplodeException | HttpStatusException e) {
                failures.add(names.get(i) + ": " + e.getMessage());
                last = e;
            }
        }

        // Every client failed. Say what each one answered: the last answer alone hides the real cause.
        if (failures.size() == 1) throw last;
        String report = "Video '" + videoId + "' could not be loaded by any YouTube client. " + String.join(" | ", failures);
        if (last instanceof VideoUnavailableException) throw new VideoUnavailableException(report);
        throw new VideoUnplayableException(report);
    }

    public PlayerResponse getPlayerResponse(VideoId videoId) {
        return getPlayerResponse(videoId, null);
    }
}

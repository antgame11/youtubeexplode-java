package youtubeexplode.playlists;

import youtubeexplode.YoutubeHttp;
import youtubeexplode.bridge.PlaylistBrowseResponse;
import youtubeexplode.bridge.PlaylistData;
import youtubeexplode.bridge.PlaylistNextResponse;
import youtubeexplode.exceptions.PlaylistUnavailableException;
import youtubeexplode.utils.Json;
import youtubeexplode.utils.Strings;
import youtubeexplode.videos.VideoId;

class PlaylistController {
    private final YoutubeHttp http;

    PlaylistController(YoutubeHttp http) {
        this.http = http;
    }

    // Works only with user-made playlists
    PlaylistBrowseResponse getPlaylistBrowseResponse(PlaylistId playlistId) {
        String body = """
                {
                  "browseId": %s,
                  "context": {
                    "client": {
                      "clientName": "WEB",
                      "clientVersion": "2.20210408.08.00",
                      "hl": "en",
                      "gl": "US",
                      "utcOffsetMinutes": 0
                    }
                  }
                }
                """.formatted(Json.encode("VL" + playlistId));

        PlaylistBrowseResponse response = PlaylistBrowseResponse.parse(
                http.string(YoutubeHttp.Request.postJson("https://www.youtube.com/youtubei/v1/browse", body)));

        if (!response.isAvailable()) throw new PlaylistUnavailableException("Playlist '" + playlistId + "' is not available.");

        return response;
    }

    // Works on all playlists, but contains limited metadata
    PlaylistNextResponse getPlaylistNextResponse(PlaylistId playlistId, VideoId videoId, int index, String visitorData) {
        final int retriesCount = 5;
        for (int retriesRemaining = retriesCount; ; retriesRemaining--) {
            String body = """
                    {
                      "playlistId": %s,
                      "videoId": %s,
                      "playlistIndex": %s,
                      "context": {
                        "client": {
                          "clientName": "WEB",
                          "clientVersion": "2.20210408.08.00",
                          "hl": "en",
                          "gl": "US",
                          "utcOffsetMinutes": 0,
                          "visitorData": %s
                        }
                      }
                    }
                    """.formatted(
                            Json.encode(playlistId.getValue()),
                            Json.encode(videoId != null ? videoId.getValue() : null),
                            Json.encode(index),
                            Json.encode(visitorData));

            PlaylistNextResponse response = PlaylistNextResponse.parse(
                    http.string(YoutubeHttp.Request.postJson("https://www.youtube.com/youtubei/v1/next", body)));

            if (!response.isAvailable()) {
                // Playlist is unavailable, this is the first request, and we haven't retried yet.
                // Try to "open" the playlist page, because some system playlists don't actually
                // fully materialize through this endpoint until someone opens them at least once.
                if (index <= 0 && Strings.isBlank(visitorData) && retriesRemaining >= retriesCount) {
                    // We don't actually care about the outcome of this request
                    http.discard(YoutubeHttp.Request.get("https://youtube.com/playlist?list=" + playlistId));
                    continue;
                }

                // Playlist is unavailable, but this is not the first request and previous requests were successful.
                // Retry because this is most likely a transient error.
                if (index > 0 && !Strings.isBlank(visitorData) && retriesRemaining > 0) continue;

                // Playlist is unavailable but contains videos. This might be caused by the fact that the target
                // video is unavailable, but the playlist itself is not.
                // Return the response anyway and let the call site move on.
                // https://github.com/Tyrrrz/YoutubeExplode/issues/921#issuecomment-3447937054
                if (retriesRemaining <= 0 && !response.videos().isEmpty()) return response;

                throw new PlaylistUnavailableException("Playlist '" + playlistId + "' is not available.");
            }

            return response;
        }
    }

    PlaylistData getPlaylistResponse(PlaylistId playlistId) {
        try {
            return getPlaylistBrowseResponse(playlistId);
        } catch (PlaylistUnavailableException e) {
            return getPlaylistNextResponse(playlistId, null, 0, null);
        }
    }
}

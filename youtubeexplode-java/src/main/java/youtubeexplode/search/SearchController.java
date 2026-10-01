package youtubeexplode.search;

import youtubeexplode.YoutubeHttp;
import youtubeexplode.bridge.SearchResponse;
import youtubeexplode.utils.Json;

class SearchController {
    private final YoutubeHttp http;

    SearchController(YoutubeHttp http) {
        this.http = http;
    }

    SearchResponse getSearchResponse(String searchQuery, SearchFilter searchFilter, String continuationToken) {
        String params =
                switch (searchFilter) {
                    case VIDEO -> "EgIQAQ%3D%3D";
                    case PLAYLIST -> "EgIQAw%3D%3D";
                    case CHANNEL -> "EgIQAg%3D%3D";
                    default -> null;
                };

        String body = """
                {
                  "query": %s,
                  "params": %s,
                  "continuation": %s,
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
                """.formatted(Json.encode(searchQuery), Json.encode(params), Json.encode(continuationToken));

        return SearchResponse.parse(
                http.string(YoutubeHttp.Request.postJson("https://www.youtube.com/youtubei/v1/search", body)));
    }
}

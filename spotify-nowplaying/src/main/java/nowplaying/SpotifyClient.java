package nowplaying;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Minimal Spotify Web API client: just "currently playing". */
public final class SpotifyClient {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final SpotifyAuth auth;
    private final String apiBase;
    private final HttpClient http;

    public SpotifyClient(SpotifyAuth auth) {
        this(auth, "https://api.spotify.com/v1", HttpClient.newHttpClient());
    }

    SpotifyClient(SpotifyAuth auth, String apiBase, HttpClient http) {
        this.auth = auth;
        this.apiBase = apiBase;
        this.http = http;
    }

    /** Empty if nothing is playing, or if the current item is not a music track (e.g. a podcast or ad). */
    public Optional<NowPlaying> currentlyPlaying() throws IOException, InterruptedException {
        for (int attempt = 0; ; attempt++) {
            HttpRequest request = HttpRequest.newBuilder(URI.create(apiBase + "/me/player/currently-playing"))
                    .header("Authorization", "Bearer " + auth.accessToken())
                    .GET()
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());

            switch (response.statusCode()) {
                case 200 -> {
                    return parse(response.body());
                }
                case 204 -> {
                    return Optional.empty();
                }
                case 401 -> {
                    if (attempt > 0) throw new IOException("Spotify rejected the access token (401)");
                    auth.invalidate();
                }
                case 429 -> {
                    if (attempt >= 3) throw new IOException("Spotify rate limit exceeded (429)");
                    long wait = response.headers().firstValueAsLong("Retry-After").orElse(2);
                    Thread.sleep(wait * 1000);
                }
                default -> throw new IOException("Spotify returned " + response.statusCode() + ": " + response.body());
            }
        }
    }

    static Optional<NowPlaying> parse(String body) throws IOException {
        if (body == null || body.isBlank()) return Optional.empty();
        JsonNode json = MAPPER.readTree(body);

        JsonNode item = json.path("item");
        if (item.isMissingNode() || item.isNull() || !"track".equals(json.path("currently_playing_type").asText("track"))) {
            return Optional.empty();
        }

        return Optional.of(toNowPlaying(item, json.path("is_playing").asBoolean(true), json.path("progress_ms").asLong(0)));
    }

    private static NowPlaying toNowPlaying(JsonNode item, boolean isPlaying, long progressMs) {
        List<String> artists = new ArrayList<>();
        for (JsonNode a : item.path("artists")) artists.add(a.path("name").asText());

        // Spotify lists album images from largest to smallest: use the largest for the web page and the
        // smallest one that is still sharp at ~80px on a high-DPI screen (>= 160px) for the SVG card
        JsonNode images = item.path("album").path("images");
        String image = images.path(0).path("url").asText(null);
        String thumb = image;
        for (JsonNode img : images) {
            if (img.path("width").asInt(0) >= 160) thumb = img.path("url").asText(thumb);
        }

        return new NowPlaying(
                item.path("id").asText(null),
                item.path("name").asText(),
                artists,
                item.path("album").path("name").asText(null),
                item.path("duration_ms").asLong(),
                isPlaying,
                progressMs,
                image,
                thumb);
    }

    /** Upcoming tracks in the queue (used to prefetch audio). Empty on any failure. */
    public List<NowPlaying> queue() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(apiBase + "/me/player/queue"))
                .header("Authorization", "Bearer " + auth.accessToken())
                .GET()
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) return List.of();
        return parseQueue(response.body());
    }

    static List<NowPlaying> parseQueue(String body) throws IOException {
        List<NowPlaying> result = new ArrayList<>();
        for (JsonNode item : MAPPER.readTree(body).path("queue")) {
            if ("track".equals(item.path("type").asText("track")) && item.hasNonNull("id")) {
                result.add(toNowPlaying(item, false, 0));
            }
        }
        return result;
    }
}

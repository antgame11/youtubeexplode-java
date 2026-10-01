package nowplaying.web;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/** Downloads cover art and turns it into data: URIs (GitHub's image proxy blocks external images in SVGs). */
public final class ImageCache {
    private static final int MAX_BYTES = 300_000;

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final Map<String, String> cache = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
            return size() > 24;
        }
    };

    /** The image as a data: URI, or null if it could not be fetched. Successful lookups are cached. */
    public synchronized String dataUri(String url) {
        if (url == null) return null;
        String cached = cache.get(url);
        if (cached != null) return cached;

        try {
            HttpResponse<byte[]> response = http.send(
                    HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(5)).GET().build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            String type = response.headers().firstValue("Content-Type").orElse("");
            if (response.statusCode() != 200 || !type.startsWith("image/") || response.body().length > MAX_BYTES) return null;

            String uri = "data:" + type.split(";")[0] + ";base64," + Base64.getEncoder().encodeToString(response.body());
            cache.put(url, uri);
            return uri;
        } catch (IOException | IllegalArgumentException e) {
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }
}

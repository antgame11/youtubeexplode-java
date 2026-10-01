package youtubeexplode;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.UncheckedIOException;
import java.net.HttpCookie;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import youtubeexplode.exceptions.HttpStatusException;
import youtubeexplode.exceptions.RequestLimitExceededException;
import youtubeexplode.utils.Url;

/**
 * Thin wrapper over {@link HttpClient} that applies the headers, cookies, localization and
 * retry behavior that YouTube expects.
 */
public final class YoutubeHttp implements AutoCloseable {
    private static final String DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/89.0.4389.114 Safari/537.36";

    private static volatile HttpClient sharedClient;

    private final HttpClient client;
    private final Map<String, String> cookies = new ConcurrentHashMap<>();

    YoutubeHttp(HttpClient client, List<HttpCookie> initialCookies) {
        this.client = client;

        // Consent to the use of cookies on YouTube.
        // This is required to access some personalized content, such as mix playlists.
        // The cookie is supposed to be invalidated after 13 months, at which point the value
        // becomes invalid and needs to be manually replaced in code with a new one.
        // https://policies.google.com/technologies/cookies/embedded
        cookies.put("SOCS", "CAISEwgDEgk4MTM4MzYzNTIaAmVuIAEaBgiApPzGBg");

        for (HttpCookie cookie : initialCookies) cookies.put(cookie.getName(), cookie.getValue());
    }

    static HttpClient sharedClient() {
        HttpClient c = sharedClient;
        if (c == null) {
            synchronized (YoutubeHttp.class) {
                c = sharedClient;
                if (c == null) {
                    c = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();
                    sharedClient = c;
                }
            }
        }
        return c;
    }

    /** Request description; a new {@link HttpRequest} is built for every attempt. */
    public record Request(String method, String url, String body, Map<String, String> headers) {
        public static Request get(String url) {
            return new Request("GET", url, null, Map.of());
        }

        public static Request get(String url, Map<String, String> headers) {
            return new Request("GET", url, null, headers);
        }

        public static Request head(String url) {
            return new Request("HEAD", url, null, Map.of());
        }

        public static Request postJson(String url, String json) {
            return new Request("POST", url, json, Map.of());
        }

        public static Request postJson(String url, String json, Map<String, String> headers) {
            return new Request("POST", url, json, headers);
        }
    }

    private static boolean isYoutubeHost(URI uri) {
        String host = uri.getHost();
        return host != null && (host.equals("youtube.com") || host.endsWith(".youtube.com"));
    }

    private String tryGenerateAuthHeaderValue(URI uri) {
        String sessionId = cookies.get("__Secure-3PAPISID");
        if (sessionId == null || sessionId.isBlank()) sessionId = cookies.get("SAPISID");
        if (sessionId == null || sessionId.isBlank()) return null;

        long timestamp = Instant.now().getEpochSecond();
        String token = timestamp + " " + sessionId + " " + Url.domain(uri);
        try {
            byte[] hash = MessageDigest.getInstance("SHA-1").digest(token.getBytes(StandardCharsets.UTF_8));
            return "SAPISIDHASH " + timestamp + "_" + HexFormat.of().withUpperCase().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private HttpRequest buildRequest(Request request) {
        String url = request.url();
        URI uri = URI.create(url);

        // Set internal API key (only valid for the main site; other hosts, like YouTube Music, work without one)
        if ("www.youtube.com".equals(uri.getHost())
                && uri.getPath() != null
                && uri.getPath().startsWith("/youtubei/")
                && !Url.containsQueryParameter(url, "key")) {
            // This key doesn't appear to change
            url = Url.setQueryParameter(url, "key", "AIzaSyA8eiZmM1FaDVjRy-df2KTyQ_vz_yYM39w");
        }

        // Set localization language
        if (!Url.containsQueryParameter(url, "hl")) url = Url.setQueryParameter(url, "hl", "en");

        uri = URI.create(url);

        Map<String, String> headers = new LinkedHashMap<>();
        request.headers().forEach((k, v) -> headers.put(k.toLowerCase(), v));

        headers.putIfAbsent("origin", Url.domain(uri));
        headers.putIfAbsent("user-agent", DEFAULT_USER_AGENT);

        if (isYoutubeHost(uri)) {
            if (!headers.containsKey("cookie") && !cookies.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                cookies.forEach((k, v) -> {
                    if (sb.length() > 0) sb.append("; ");
                    sb.append(k).append('=').append(v);
                });
                headers.put("cookie", sb.toString());
            }

            if (!headers.containsKey("authorization")) {
                String auth = tryGenerateAuthHeaderValue(uri);
                if (auth != null) headers.put("authorization", auth);
            }
        }

        HttpRequest.Builder builder = HttpRequest.newBuilder(uri);
        headers.forEach(builder::header);

        if (request.body() != null) {
            builder.header("Content-Type", "text/plain; charset=utf-8");
            builder.method(request.method(), HttpRequest.BodyPublishers.ofString(request.body()));
        } else {
            builder.method(request.method(), HttpRequest.BodyPublishers.noBody());
        }

        return builder.build();
    }

    private <T> HttpResponse<T> handleResponse(HttpResponse<T> response) {
        // Custom exception for rate limit errors
        if (response.statusCode() == 429) {
            closeQuietly(response.body());
            throw new RequestLimitExceededException("Exceeded request rate limit. "
                    + "Please try again in a few hours. "
                    + "Alternatively, inject cookies corresponding to a pre-authenticated user when initializing an instance of `YoutubeClient`.");
        }

        // Store cookies set by YouTube
        if (isYoutubeHost(response.uri())) {
            for (String header : response.headers().allValues("Set-Cookie")) {
                try {
                    for (HttpCookie cookie : HttpCookie.parse(header)) {
                        // YouTube may send cookies for other domains, ignore them
                        String domain = cookie.getDomain();
                        if (domain != null && !domain.isEmpty()) {
                            String d = domain.startsWith(".") ? domain.substring(1) : domain;
                            if (!d.equals("youtube.com") && !d.endsWith(".youtube.com")) continue;
                        }
                        cookies.put(cookie.getName(), cookie.getValue());
                    }
                } catch (IllegalArgumentException ignored) {
                    // Malformed cookie
                }
            }
        }

        return response;
    }

    private static void closeQuietly(Object body) {
        if (body instanceof AutoCloseable c) {
            try {
                c.close();
            } catch (Exception ignored) {
            }
        }
    }

    private <T> HttpResponse<T> send(Request request, HttpResponse.BodyHandler<T> handler) {
        for (int retriesRemaining = 5; ; retriesRemaining--) {
            HttpResponse<T> response;
            try {
                response = handleResponse(client.send(buildRequest(request), handler));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new UncheckedIOException(new InterruptedIOException("Request was interrupted."));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }

            // Retry on 5XX errors
            if (response.statusCode() >= 500 && retriesRemaining > 0) {
                closeQuietly(response.body());
                continue;
            }

            return response;
        }
    }

    private static void ensureSuccess(HttpResponse<?> response) {
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            closeQuietly(response.body());
            throw new HttpStatusException(response.statusCode(), response.uri().toString());
        }
    }

    /** Sends the request and returns the body as a string. Throws on unsuccessful status. */
    public String string(Request request) {
        HttpResponse<String> response = send(request, HttpResponse.BodyHandlers.ofString());
        ensureSuccess(response);
        return response.body();
    }

    public String getString(String url) {
        return string(Request.get(url));
    }

    /** Sends the request without failing on unsuccessful status codes; the caller inspects it. */
    public HttpResponse<Void> discard(Request request) {
        return send(request, HttpResponse.BodyHandlers.discarding());
    }

    /** Sends the request and returns the streaming body. Throws on unsuccessful status. */
    public InputStream stream(Request request) {
        HttpResponse<InputStream> response = send(request, HttpResponse.BodyHandlers.ofInputStream());
        ensureSuccess(response);
        return response.body();
    }

    /** Whether a failure is worth retrying (connectivity problem rather than interruption). */
    public static boolean isRetryable(Throwable t) {
        return t instanceof UncheckedIOException u && !(u.getCause() instanceof InterruptedIOException);
    }

    @Override
    public void close() {
        // The underlying client may be shared with the caller and is not owned by us.
    }
}

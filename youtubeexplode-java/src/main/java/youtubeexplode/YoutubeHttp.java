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

    private static final String DEFAULT_SOCS = "CAISEwgDEgk4MTM4MzYzNTIaAmVuIAEaBgiApPzGBg";

    private static volatile HttpClient sharedClient;

    private final HttpClient client;
    private final CookieJar cookies = new CookieJar();

    YoutubeHttp(HttpClient client, List<HttpCookie> initialCookies) {
        this.client = client;

        // Consent to the use of cookies on YouTube.
        // This is required to access some personalized content, such as mix playlists.
        // The cookie is supposed to be invalidated after 13 months, at which point the value
        // becomes invalid and needs to be manually replaced in code with a new one.
        // https://policies.google.com/technologies/cookies/embedded
        long now = System.currentTimeMillis();
        cookies.put(new CookieJar.Entry("SOCS", DEFAULT_SOCS, "youtube.com", "/", true, Long.MAX_VALUE));

        // Cookies without a domain are assumed to belong to youtube.com
        for (HttpCookie cookie : initialCookies) cookies.put(CookieJar.entryOf(cookie, "youtube.com", now).asLogin());
    }

    /** Whether the client was given a login (cookies) that has not expired. */
    public boolean hasLogin() {
        return cookies.hasLogin();
    }

    /** Current cookies, including any YouTube has rotated since the client was created. */
    List<HttpCookie> cookies() {
        return cookies.snapshot();
    }

    static HttpClient sharedClient() {
        HttpClient c = sharedClient;
        if (c == null) {
            synchronized (YoutubeHttp.class) {
                c = sharedClient;
                if (c == null) {
                    c = HttpClient.newBuilder()
                            .followRedirects(HttpClient.Redirect.NORMAL)
                            .connectTimeout(java.time.Duration.ofSeconds(20))
                            .build();
                    sharedClient = c;
                }
            }
        }
        return c;
    }

    /**
     * How much of the user's login a request carries. Findings against the live service: YouTube answers
     * HTTP 400 "Request contains an invalid argument" when cookies <em>and</em> the signed Authorization header
     * are sent together to anything but the web clients, and the mobile app clients ignore a login entirely.
     */
    public enum Login {
        /** Cookies and the SAPISIDHASH Authorization header (web clients). */
        FULL,
        /** Cookies only (TV client). */
        COOKIES_ONLY,
        /** Nothing but the consent cookie (mobile app clients). */
        NONE
    }

    /** Request description; a new {@link HttpRequest} is built for every attempt. */
    public record Request(String method, String url, String body, Map<String, String> headers, Login login) {
        public Request(String method, String url, String body, Map<String, String> headers) {
            this(method, url, body, headers, Login.FULL);
        }

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

        public Request asAnonymous() {
            return new Request(method, url, body, headers, Login.NONE);
        }

        public Request cookiesOnly() {
            return new Request(method, url, body, headers, Login.COOKIES_ONLY);
        }
    }

    private static boolean isYoutubeHost(URI uri) {
        String host = uri.getHost();
        return host != null && (host.equals("youtube.com") || host.endsWith(".youtube.com"));
    }

    /** The cookies an anonymous client would have: no login, but always the consent cookie. */
    private String anonymousCookieHeader(URI uri) {
        String header = cookies.anonymousHeader(uri);
        if (header == null) return "SOCS=" + DEFAULT_SOCS;
        return header.contains("SOCS=") ? header : header + "; SOCS=" + DEFAULT_SOCS;
    }

    private String tryGenerateAuthHeaderValue(URI uri) {
        String sessionId = cookies.valueFor("__Secure-3PAPISID", uri);
        if (sessionId == null) sessionId = cookies.valueFor("SAPISID", uri);
        if (sessionId == null) return null;

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
            if (!headers.containsKey("cookie")) {
                String cookieHeader = request.login() == Login.NONE ? anonymousCookieHeader(uri) : cookies.header(uri);
                if (cookieHeader != null) headers.put("cookie", cookieHeader);
            }

            if (request.login() == Login.FULL && !headers.containsKey("authorization")) {
                String auth = tryGenerateAuthHeaderValue(uri);
                if (auth != null) headers.put("authorization", auth);
            }
        }

        // Without a timeout a stalled connection would hang forever instead of failing (and being retried)
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri).timeout(java.time.Duration.ofSeconds(60));
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
                        cookies.put(CookieJar.entryOf(cookie, response.uri().getHost(), System.currentTimeMillis()));
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
            // Keep the start of the body: YouTube explains most rejections there
            String detail = response.body() instanceof String text ? text : null;
            closeQuietly(response.body());
            throw new HttpStatusException(response.statusCode(), response.uri().toString(), detail);
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

package nowplaying;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpServer;
import java.awt.Desktop;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Spotify Authorization Code flow with PKCE: needs only a client ID (no secret). On first use it
 * opens a browser for consent and stores the refresh token; afterwards it refreshes silently.
 */
public final class SpotifyAuth {
    static final String SCOPE = "user-read-currently-playing user-read-playback-state";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String clientId;
    private final int redirectPort;
    private final String authorizeUrl;
    private final String tokenUrl;
    private final Path tokenFile;
    private final HttpClient http;

    private String accessToken;
    private Instant accessTokenExpiry = Instant.EPOCH;
    private String refreshToken;

    public SpotifyAuth(String clientId, int redirectPort, Path tokenFile) {
        this(clientId, redirectPort, tokenFile, "https://accounts.spotify.com/authorize",
                "https://accounts.spotify.com/api/token", HttpClient.newHttpClient());
    }

    SpotifyAuth(String clientId, int redirectPort, Path tokenFile, String authorizeUrl, String tokenUrl, HttpClient http) {
        this.clientId = clientId;
        this.redirectPort = redirectPort;
        this.tokenFile = tokenFile;
        this.authorizeUrl = authorizeUrl;
        this.tokenUrl = tokenUrl;
        this.http = http;
        this.refreshToken = loadRefreshToken();
    }

    /** The redirect URI that must be registered in the Spotify app settings. */
    public String redirectUri() {
        return "http://127.0.0.1:" + redirectPort + "/callback";
    }

    /** Returns a valid access token, refreshing or logging in interactively as needed. */
    public synchronized String accessToken() throws IOException, InterruptedException {
        if (accessToken != null && Instant.now().isBefore(accessTokenExpiry.minusSeconds(30))) return accessToken;

        if (refreshToken != null) {
            try {
                refresh();
                return accessToken;
            } catch (IOException e) {
                // Refresh token revoked or expired: fall through to a fresh login
                System.err.println("Spotify session expired (" + e.getMessage() + "), logging in again...");
                refreshToken = null;
            }
        }

        login();
        return accessToken;
    }

    /** Forces the next call to fetch a new access token (e.g. after a 401). */
    public synchronized void invalidate() {
        accessToken = null;
    }

    private void refresh() throws IOException, InterruptedException {
        applyTokenResponse(postForm(Map.of(
                "grant_type", "refresh_token", "refresh_token", refreshToken, "client_id", clientId)));
    }

    private void login() throws IOException, InterruptedException {
        byte[] rnd = new byte[64];
        new SecureRandom().nextBytes(rnd);
        String verifier = Base64.getUrlEncoder().withoutPadding().encodeToString(rnd);
        String challenge;
        try {
            challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(
                    MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        String state = Long.toHexString(new SecureRandom().nextLong());

        CompletableFuture<String> codeFuture = new CompletableFuture<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), redirectPort), 0);
        server.createContext("/callback", exchange -> {
            Map<String, String> q = parseQuery(exchange.getRequestURI().getRawQuery());
            String message;
            if (!state.equals(q.get("state"))) {
                message = "State mismatch. You can close this tab.";
                codeFuture.completeExceptionally(new IOException("Spotify login: state mismatch"));
            } else if (q.get("code") != null) {
                message = "Logged in to Spotify. You can close this tab.";
                codeFuture.complete(q.get("code"));
            } else {
                message = "Login failed: " + q.getOrDefault("error", "unknown error");
                codeFuture.completeExceptionally(new IOException("Spotify login failed: " + q.get("error")));
            }
            byte[] body = message.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/plain; charset=utf-8");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        try {
            String url = authorizeUrl + "?" + form(Map.of(
                    "client_id", clientId,
                    "response_type", "code",
                    "redirect_uri", redirectUri(),
                    "code_challenge_method", "S256",
                    "code_challenge", challenge,
                    "scope", SCOPE,
                    "state", state));

            System.err.println("Open this URL to log in to Spotify (opening your browser if possible):\n  " + url);
            try {
                if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                    Desktop.getDesktop().browse(URI.create(url));
                }
            } catch (Exception ignored) {
                // The URL is printed above
            }

            String code;
            try {
                code = codeFuture.get(5, TimeUnit.MINUTES);
            } catch (java.util.concurrent.ExecutionException e) {
                throw e.getCause() instanceof IOException io ? io : new IOException(e.getCause());
            } catch (java.util.concurrent.TimeoutException e) {
                throw new IOException("Timed out waiting for the Spotify login.");
            }

            applyTokenResponse(postForm(Map.of(
                    "grant_type", "authorization_code",
                    "code", code,
                    "redirect_uri", redirectUri(),
                    "client_id", clientId,
                    "code_verifier", verifier)));
        } finally {
            server.stop(0);
        }
    }

    private JsonNode postForm(Map<String, String> fields) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(tokenUrl))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form(fields)))
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("Spotify token endpoint returned " + response.statusCode() + ": " + response.body());
        }
        return MAPPER.readTree(response.body());
    }

    private void applyTokenResponse(JsonNode json) throws IOException {
        accessToken = json.path("access_token").asText(null);
        if (accessToken == null) throw new IOException("Spotify token response has no access_token");
        accessTokenExpiry = Instant.now().plusSeconds(json.path("expires_in").asLong(3600));

        // Refresh responses may or may not rotate the refresh token
        String newRefresh = json.path("refresh_token").asText(null);
        if (newRefresh != null) {
            refreshToken = newRefresh;
            saveRefreshToken();
        }
    }

    private String loadRefreshToken() {
        try {
            if (Files.exists(tokenFile)) {
                JsonNode json = MAPPER.readTree(Files.readString(tokenFile));
                if (clientId.equals(json.path("client_id").asText())) return json.path("refresh_token").asText(null);
            }
        } catch (IOException ignored) {
        }
        return null;
    }

    private void saveRefreshToken() throws IOException {
        Files.createDirectories(tokenFile.toAbsolutePath().getParent());
        ObjectNode json = MAPPER.createObjectNode();
        json.put("client_id", clientId);
        json.put("refresh_token", refreshToken);
        Files.writeString(tokenFile, MAPPER.writeValueAsString(json));
        try {
            Files.setPosixFilePermissions(tokenFile, PosixFilePermissions.fromString("rw-------"));
        } catch (UnsupportedOperationException ignored) {
            // Non-POSIX file system
        }
    }

    private static String form(Map<String, String> fields) {
        return fields.entrySet().stream()
                .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "="
                        + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
    }

    private static Map<String, String> parseQuery(String raw) {
        Map<String, String> result = new java.util.HashMap<>();
        if (raw == null) return result;
        for (String pair : raw.split("&")) {
            int eq = pair.indexOf('=');
            if (eq < 0) continue;
            result.put(java.net.URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8),
                    java.net.URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8));
        }
        return result;
    }
}

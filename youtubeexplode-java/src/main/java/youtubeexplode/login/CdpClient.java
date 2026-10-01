package youtubeexplode.login;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Minimal Chrome DevTools Protocol client over the browser-level WebSocket. It only issues browser
 * commands (no page is attached to or scripted), so the browser does not look automated to Google.
 */
final class CdpClient implements AutoCloseable {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final WebSocket socket;
    private final AtomicInteger nextId = new AtomicInteger(1);
    private final Map<Integer, CompletableFuture<JsonNode>> pending = new ConcurrentHashMap<>();

    private CdpClient(URI uri) {
        Listener listener = new Listener();
        this.socket = HttpClient.newHttpClient().newWebSocketBuilder().buildAsync(uri, listener).join();
    }

    static CdpClient connect(URI uri) {
        return new CdpClient(uri);
    }

    private final class Listener implements WebSocket.Listener {
        private final StringBuilder message = new StringBuilder();

        @Override
        public void onOpen(WebSocket ws) {
            ws.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
            message.append(data);
            if (last) {
                String text = message.toString();
                message.setLength(0);
                try {
                    JsonNode json = MAPPER.readTree(text);
                    if (json.has("id")) {
                        CompletableFuture<JsonNode> future = pending.remove(json.get("id").asInt());
                        if (future != null) {
                            if (json.has("error")) {
                                future.completeExceptionally(new IOException("DevTools error: " + json.get("error")));
                            } else {
                                future.complete(json.path("result"));
                            }
                        }
                    }
                } catch (IOException ignored) {
                    // Not JSON: ignore
                }
            }
            ws.request(1);
            return null;
        }

        @Override
        public void onError(WebSocket ws, Throwable error) {
            pending.values().forEach(f -> f.completeExceptionally(error));
        }

        @Override
        public CompletionStage<?> onClose(WebSocket ws, int statusCode, String reason) {
            pending.values().forEach(f -> f.completeExceptionally(new IOException("Browser connection closed")));
            return null;
        }
    }

    JsonNode call(String method, ObjectNode params) throws IOException, InterruptedException {
        int id = nextId.getAndIncrement();
        CompletableFuture<JsonNode> future = new CompletableFuture<>();
        pending.put(id, future);

        ObjectNode request = MAPPER.createObjectNode();
        request.put("id", id);
        request.put("method", method);
        if (params != null) request.set("params", params);
        socket.sendText(request.toString(), true);

        try {
            return future.get(15, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            throw e.getCause() instanceof IOException io ? io : new IOException(e.getCause());
        } catch (TimeoutException e) {
            pending.remove(id);
            throw new IOException("Timed out waiting for the browser to answer " + method);
        }
    }

    /** All cookies of the browser's default profile. */
    List<HttpCookie> getCookies() throws IOException, InterruptedException {
        JsonNode result = call("Storage.getCookies", null);
        List<HttpCookie> cookies = new ArrayList<>();
        long now = System.currentTimeMillis() / 1000;
        for (JsonNode c : result.path("cookies")) {
            double expires = c.path("expires").asDouble(-1);
            HttpCookie cookie = new HttpCookie(c.path("name").asText(), c.path("value").asText());
            cookie.setDomain(c.path("domain").asText());
            cookie.setPath(c.path("path").asText("/"));
            cookie.setSecure(c.path("secure").asBoolean());
            cookie.setHttpOnly(c.path("httpOnly").asBoolean());
            if (c.path("session").asBoolean(false) || expires <= 0) cookie.setMaxAge(-1);
            else cookie.setMaxAge(Math.max(0, (long) expires - now));
            cookies.add(cookie);
        }
        return cookies;
    }

    /** Test helper: puts cookies into the browser's default profile. */
    void setCookies(List<HttpCookie> cookies) throws IOException, InterruptedException {
        ObjectNode params = MAPPER.createObjectNode();
        var array = params.putArray("cookies");
        for (HttpCookie c : cookies) {
            ObjectNode o = array.addObject();
            o.put("name", c.getName());
            o.put("value", c.getValue());
            o.put("domain", c.getDomain());
            o.put("path", c.getPath() == null ? "/" : c.getPath());
            o.put("secure", c.getSecure());
            if (c.getMaxAge() > 0) o.put("expires", System.currentTimeMillis() / 1000.0 + c.getMaxAge());
        }
        call("Storage.setCookies", params);
    }

    void closeBrowser() {
        try {
            call("Browser.close", null);
        } catch (IOException | InterruptedException ignored) {
            // The browser closing the socket on its way out is expected
        }
    }

    @Override
    public void close() {
        socket.sendClose(WebSocket.NORMAL_CLOSURE, "done").completeOnTimeout(null, 2, TimeUnit.SECONDS);
        socket.abort();
    }
}

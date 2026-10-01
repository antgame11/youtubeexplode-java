package nowplaying;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import youtubeexplode.YoutubeClient;

/**
 * Simulates a machine where YouTube's player endpoint is unreachable (as with "confirm you're not a bot") while
 * search still works, and checks that the app falls back to yt-dlp by itself.
 */
@Tag("live")
class FallbackLiveTest {
    @TempDir Path tmp;

    /** The real HTTP client, except that every request to the player endpoint fails. */
    static final class BlockedPlayerClient extends HttpClient {
        private final HttpClient real = HttpClient.newHttpClient();
        int blocked;

        @Override public Optional<CookieHandler> cookieHandler() { return real.cookieHandler(); }
        @Override public Optional<Duration> connectTimeout() { return real.connectTimeout(); }
        @Override public Redirect followRedirects() { return real.followRedirects(); }
        @Override public Optional<ProxySelector> proxy() { return real.proxy(); }
        @Override public SSLContext sslContext() { return real.sslContext(); }
        @Override public SSLParameters sslParameters() { return real.sslParameters(); }
        @Override public Optional<Authenticator> authenticator() { return real.authenticator(); }
        @Override public Version version() { return real.version(); }
        @Override public Optional<Executor> executor() { return real.executor(); }

        @Override
        public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> handler) throws IOException, InterruptedException {
            if (request.uri().getPath().contains("/youtubei/v1/player")) {
                blocked++;
                throw new IOException("simulated: YouTube refuses the player request");
            }
            return real.send(request, handler);
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest r, HttpResponse.BodyHandler<T> h) {
            return real.sendAsync(r, h);
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                HttpRequest r, HttpResponse.BodyHandler<T> h, HttpResponse.PushPromiseHandler<T> p) {
            return real.sendAsync(r, h, p);
        }
    }

    @Test
    void fallsBackToYtDlpWhenThePlayerEndpointFails() throws Exception {
        assumeTrue(YtDlp.find().isPresent(), "yt-dlp not installed");

        NowPlaying np = new NowPlaying("sp", "Runaway", List.of("Kanye West", "Pusha T"), "My Beautiful Dark Twisted Fantasy", 548_000, true);
        BlockedPlayerClient blockedHttp = new BlockedPlayerClient();

        try (YoutubeClient youtube = new YoutubeClient(blockedHttp)) {
            var app = new NowPlayingDownloader(null, youtube, tmp);
            Path file = app.download(np).orElseThrow();

            assertTrue(blockedHttp.blocked > 0, "the built-in downloader should have been blocked and have tried");
            assertTrue(Files.size(file) > 1_000_000, "size " + Files.size(file));
            assertTrue(file.getFileName().toString().startsWith("Kanye West, Pusha T - Runaway."), file.getFileName().toString());
            try (var left = Files.list(tmp)) {
                assertTrue(left.noneMatch(p -> p.getFileName().toString().startsWith(".ytdlp-")), "temporary folder should be cleaned up");
            }
            System.out.println("fallback produced " + file.getFileName() + " (" + Files.size(file) + " bytes)");
        }
    }
}

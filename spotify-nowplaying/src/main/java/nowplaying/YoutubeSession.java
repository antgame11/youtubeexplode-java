package nowplaying;

import java.io.IOException;
import java.net.HttpCookie;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import youtubeexplode.YoutubeClient;
import youtubeexplode.login.CookieStore;

/** Opens the YouTube client, using the saved Google login when there is one. */
public final class YoutubeSession {
    private YoutubeSession() {}

    /** {@code YOUTUBE_COOKIES_FILE} from the settings, or the default {@code ~/.config/youtubeexplode/cookies.json}. */
    public static Path cookiesFile() {
        String configured = Config.get("YOUTUBE_COOKIES_FILE");
        return configured != null ? Path.of(configured) : CookieStore.configDir().resolve("cookies.json");
    }

    public static boolean isLoggedIn() {
        return Files.exists(cookiesFile());
    }

    /** The saved login, or an empty list if there is none (or it cannot be read). */
    public static List<HttpCookie> loadCookies() {
        Path file = cookiesFile();
        if (!Files.exists(file)) return List.of();
        try {
            return CookieStore.load(file);
        } catch (IOException e) {
            return List.of();
        }
    }

    public static YoutubeClient open() {
        Path file = cookiesFile();
        YoutubeClient youtube = YoutubeClient.withLogin(file);

        if (Files.exists(file)) {
            System.err.println("Using the saved YouTube login (" + file + ")");
            // YouTube rotates some session cookies as you use them: write them back so the login keeps working
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    List<HttpCookie> cookies = youtube.getCookies();
                    if (!cookies.isEmpty()) CookieStore.save(file, cookies);
                } catch (IOException ignored) {
                    // Best effort
                }
            }));
        }
        return youtube;
    }
}

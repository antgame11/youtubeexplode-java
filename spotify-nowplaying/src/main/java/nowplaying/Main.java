package nowplaying;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import youtubeexplode.exceptions.YoutubeExplodeException;
import youtubeexplode.login.CookieStore;
import youtubeexplode.login.GoogleLogin;
import nowplaying.web.WebApp;

/** The single entry point of the jar: {@code java -jar spotify-nowplaying.jar <command> [options]}. */
public final class Main {
    private static final String USAGE = """
            Usage: java -jar spotify-nowplaying.jar [command] [options]

            Commands:
              download   Download the song playing on Spotify right now (default).
                         Options: --out DIR  --watch  --client-id ID  --port 8888
              web        Web player that plays what you're playing, in sync. Open http://127.0.0.1:8080
                         Options: --web-port 8080  --host 127.0.0.1  --cache DIR  --offset-ms 0
                                  --card-only (only the /now.svg card and /embed, no audio)
              login      Sign in to your Google account in a browser window and save the login
                         (experimental: it does not currently make more videos downloadable). Needs a screen.
              logout     Delete the saved login.

            Settings come from environment variables or a .env file: SPOTIFY_CLIENT_ID,
            YOUTUBE_COOKIES_FILE (optional).
            """;

    private Main() {}

    public static void main(String[] args) throws Exception {
        String command = args.length > 0 && !args[0].startsWith("-") ? args[0] : "download";
        String[] rest = args.length > 0 && !args[0].startsWith("-") ? Arrays.copyOfRange(args, 1, args.length) : args;

        try {
            switch (command) {
                case "download" -> {
                    if (Arrays.asList(rest).contains("--help")) System.out.print(USAGE);
                    else NowPlayingDownloader.main(rest);
                }
                case "web" -> WebApp.main(rest);
                case "login" -> login();
                case "logout" -> logout();
                case "help", "--help", "-h" -> System.out.print(USAGE);
                default -> {
                    System.err.println("Unknown command: " + command + "\n");
                    System.err.print(USAGE);
                    System.exit(1);
                }
            }
        } catch (YoutubeExplodeException e) {
            // A readable message instead of a stack trace
            System.err.println("YouTube error: " + e.getMessage());
            if (e.getMessage() != null && e.getMessage().contains("not available")) {
                System.err.println("Hint: the message above says what each YouTube client answered. A block on your IP address "
                        + "(servers, VPNs, shared addresses) is the usual cause; a normal home connection fixes it. "
                        + "A Google login does not help with that.");
            }
            System.exit(1);
        }
    }

    private static void login() throws Exception {
        Path file = YoutubeSession.cookiesFile();
        var cookies = GoogleLogin.login(GoogleLogin.defaultProfileDir(), Duration.ofMinutes(5), System.err::println);
        CookieStore.save(file, cookies);
        System.err.println("Saved the login (" + cookies.size() + " cookies) to " + file);
        System.err.println("Keep that file private: anyone who has it can act as your Google account.");
    }

    private static void logout() throws Exception {
        Path file = YoutubeSession.cookiesFile();
        System.err.println(Files.deleteIfExists(file) ? "Deleted " + file : "No saved login at " + file);
    }
}

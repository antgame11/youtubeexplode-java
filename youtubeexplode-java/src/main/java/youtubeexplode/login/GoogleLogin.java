package youtubeexplode.login;

import java.io.IOException;
import java.net.HttpCookie;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Signs in to a Google account in a real browser window and collects the YouTube login cookies.
 *
 * <p>An embedded browser would not work: Google refuses sign-in from embedded web views. A browser that
 * looks automated is refused too ("This browser or app may not be secure"), and Chrome marks itself as
 * automated ({@code navigator.webdriver == true}) whenever remote debugging is enabled. So this starts your
 * installed Chrome, Chromium, Edge or Brave with its own profile folder, with debugging enabled but that
 * automation marker switched off, and only talks to the browser process over the DevTools protocol (it never
 * attaches to or scripts a page). You sign in normally, 2-step verification included. The program notices
 * when the login completes by reading the browser's cookies from memory, then closes the window. Nothing is
 * typed or clicked for you and your password never passes through this program.
 *
 * <p>Set {@code YOUTUBE_BROWSER} to a browser executable to override the choice.
 */
public final class GoogleLogin {
    public static final String LOGIN_URL =
            "https://accounts.google.com/ServiceLogin?service=youtube&continue=https%3A%2F%2Fwww.youtube.com%2F";

    private GoogleLogin() {}

    /** A dedicated profile, so the login does not touch your everyday browser profile. */
    public static Path defaultProfileDir() {
        return CookieStore.configDir().resolve("browser-profile");
    }

    /** Opens the login window and waits for you to sign in. Returns the YouTube/Google cookies. */
    public static List<HttpCookie> login(Path profileDir, Duration timeout, Consumer<String> log)
            throws IOException, InterruptedException {
        Path browser = findBrowser().orElseThrow(() -> new IOException(
                "No Chrome, Chromium, Edge or Brave found. Install one, or set YOUTUBE_BROWSER to its executable."));

        Files.createDirectories(profileDir);
        Path portFile = profileDir.resolve("DevToolsActivePort");
        Files.deleteIfExists(portFile); // a stale file would point at a browser that is gone

        log.accept("Opening " + browser.getFileName() + ". Sign in to your Google account in the window that appears.");
        log.accept("The window closes by itself when you're signed in. (If it doesn't, press Enter here.)");
        Process window = new ProcessBuilder(command(browser, profileDir, LOGIN_URL))
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .start();
        try {
            return waitForLogin(window, portFile, timeout, log);
        } finally {
            stop(window);
        }
    }

    /**
     * Browser command line: remote debugging (so the cookies can be read) with the automation marker disabled,
     * otherwise Google rejects the sign-in.
     */
    static List<String> command(Path browser, Path profileDir, String... extra) {
        List<String> cmd = new ArrayList<>();
        cmd.add(browser.toString());
        cmd.add("--user-data-dir=" + profileDir.toAbsolutePath());
        cmd.add("--remote-debugging-port=0");
        cmd.add("--remote-allow-origins=*");
        cmd.add("--disable-blink-features=AutomationControlled"); // keeps navigator.webdriver false
        cmd.add("--no-first-run");
        cmd.add("--no-default-browser-check");
        cmd.add("--password-store=basic"); // no keyring prompt
        if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac")) cmd.add("--use-mock-keychain");
        if ("root".equals(System.getProperty("user.name"))) cmd.add("--no-sandbox"); // Chrome refuses to run as root otherwise
        cmd.addAll(List.of(extra));
        return cmd;
    }

    private static List<HttpCookie> waitForLogin(Process window, Path portFile, Duration timeout, Consumer<String> log)
            throws IOException, InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();

        // Chrome writes its debugging port and WebSocket path here once it is up
        while (!Files.exists(portFile) || Files.readAllLines(portFile).size() < 2) {
            if (!window.isAlive()) {
                throw new IOException("The browser exited before it was ready. Is another window already using this profile?");
            }
            if (System.nanoTime() > deadline) throw new IOException("Timed out waiting for the browser to start.");
            Thread.sleep(200);
        }
        List<String> lines = Files.readAllLines(portFile);
        URI endpoint = URI.create("ws://127.0.0.1:" + lines.get(0).strip() + lines.get(1).strip());

        try (CdpClient cdp = CdpClient.connect(endpoint)) {
            while (true) {
                if (!window.isAlive()) throw new IOException("The browser was closed before the login finished.");
                if (System.nanoTime() > deadline) throw new IOException("Timed out waiting for you to sign in.");

                List<HttpCookie> cookies = cdp.getCookies();
                if (isLoggedIn(cookies)) {
                    log.accept("Signed in. Finishing up...");
                    Thread.sleep(3_000); // let the redirects finish setting the remaining cookies
                    List<HttpCookie> result = relevant(cdp.getCookies());
                    cdp.closeBrowser();
                    return result;
                }

                if (stdinHasInput()) {
                    // The user says they are done but we do not see a YouTube login: say what we do see
                    throw new IOException("No YouTube login found yet (" + describe(cookies) + "). "
                            + "Finish signing in until you can see YouTube, then run the login again.");
                }
                Thread.sleep(1_000);
            }
        }
    }

    /** A short, value-free summary of what the browser holds, to explain a failed login. */
    static String describe(List<HttpCookie> cookies) {
        long google = cookies.stream().filter(c -> domainMatches(c, "google.com")).count();
        long youtube = cookies.stream().filter(c -> domainMatches(c, "youtube.com")).count();
        return google + " Google cookies, " + youtube + " YouTube cookies";
    }

    private static boolean stdinHasInput() {
        try {
            if (System.in.available() > 0) {
                while (System.in.available() > 0) System.in.read();
                return true;
            }
        } catch (IOException ignored) {
            // No usable stdin: only the sign-in itself ends the wait
        }
        return false;
    }

    /** Closes the browser politely, forcibly only as a last resort. */
    private static void stop(Process process) throws InterruptedException {
        if (!process.isAlive()) return;
        process.destroy();
        if (!process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS)) {
            process.destroyForcibly();
            process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
        }
    }

    /** True once YouTube has issued the session cookies that prove a signed-in account. */
    static boolean isLoggedIn(List<HttpCookie> cookies) {
        for (HttpCookie c : cookies) {
            String name = c.getName();
            if ((name.equals("SAPISID") || name.equals("__Secure-3PAPISID")) && domainMatches(c, "youtube.com")) return true;
        }
        return false;
    }

    /** Keeps only cookies for YouTube and Google. */
    static List<HttpCookie> relevant(List<HttpCookie> cookies) {
        List<HttpCookie> result = new ArrayList<>();
        for (HttpCookie c : cookies) {
            if (domainMatches(c, "youtube.com") || domainMatches(c, "google.com")) result.add(c);
        }
        return result;
    }

    private static boolean domainMatches(HttpCookie c, String domain) {
        String d = c.getDomain() == null ? "" : c.getDomain().toLowerCase(Locale.ROOT);
        if (d.startsWith(".")) d = d.substring(1);
        return d.equals(domain) || d.endsWith("." + domain);
    }

    // ---- Finding a browser ----

    public static Optional<Path> findBrowser() {
        String override = System.getenv("YOUTUBE_BROWSER");
        if (override != null && !override.isBlank()) {
            Path p = Path.of(override);
            return Files.isExecutable(p) ? Optional.of(p) : Optional.empty();
        }

        List<Path> candidates = new ArrayList<>();
        String[] names = {
            "google-chrome-stable", "google-chrome", "chromium", "chromium-browser", "chrome",
            "microsoft-edge-stable", "microsoft-edge", "brave-browser", "brave"
        };
        String path = System.getenv("PATH");
        if (path != null) {
            for (String dir : path.split(java.io.File.pathSeparator)) {
                for (String name : names) {
                    candidates.add(Path.of(dir, name));
                    candidates.add(Path.of(dir, name + ".exe"));
                }
            }
        }

        // Typical install locations on macOS and Windows (browsers are rarely on the PATH there)
        candidates.add(Path.of("/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"));
        candidates.add(Path.of("/Applications/Microsoft Edge.app/Contents/MacOS/Microsoft Edge"));
        candidates.add(Path.of("/Applications/Brave Browser.app/Contents/MacOS/Brave Browser"));
        candidates.add(Path.of("/Applications/Chromium.app/Contents/MacOS/Chromium"));
        for (String env : new String[] {"ProgramFiles", "ProgramFiles(x86)", "LOCALAPPDATA"}) {
            String base = System.getenv(env);
            if (base == null) continue;
            candidates.add(Path.of(base, "Google", "Chrome", "Application", "chrome.exe"));
            candidates.add(Path.of(base, "Microsoft", "Edge", "Application", "msedge.exe"));
            candidates.add(Path.of(base, "BraveSoftware", "Brave-Browser", "Application", "brave.exe"));
        }

        return candidates.stream().filter(Files::isExecutable).findFirst();
    }
}

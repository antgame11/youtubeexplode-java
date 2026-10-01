package nowplaying;

import java.io.IOException;
import java.net.HttpCookie;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Backup downloader that runs the external <a href="https://github.com/yt-dlp/yt-dlp">yt-dlp</a> tool.
 *
 * <p>The built-in downloader only understands the plain-URL streams of YouTube's mobile app clients. When
 * YouTube refuses those (for example "Sign in to confirm you're not a bot" on a server IP address), yt-dlp can
 * still succeed: it is updated constantly, it can use your saved Google login, and with Node or Deno installed
 * it solves the challenges in YouTube's obfuscated player script, which this program cannot.
 *
 * <p>Needs {@code yt-dlp} and {@code ffmpeg} on the PATH (or {@code YT_DLP} pointing at yt-dlp); Node or Deno is
 * strongly recommended.
 */
public final class YtDlp {
    private static final long TIMEOUT_MINUTES = 5;
    private static final List<String> RUNTIMES = List.of("deno", "node", "bun");

    private final String executable;

    YtDlp(String executable) {
        this.executable = executable;
    }

    /** Finds a working yt-dlp: {@code YT_DLP} from the settings, or {@code yt-dlp} on the PATH. */
    public static Optional<YtDlp> find() {
        String configured = Config.get("YT_DLP");
        List<String> candidates = new ArrayList<>();
        if (configured != null) candidates.add(configured);
        candidates.add("yt-dlp");
        candidates.add("yt-dlp.exe");

        for (String candidate : candidates) {
            try {
                Process p = new ProcessBuilder(candidate, "--version").redirectErrorStream(true).start();
                p.getInputStream().readAllBytes();
                if (p.waitFor(15, TimeUnit.SECONDS) && p.exitValue() == 0) return Optional.of(new YtDlp(candidate));
            } catch (IOException e) {
                // Not found under this name
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return Optional.empty();
    }

    /**
     * Downloads the audio of a video into {@code dir} as {@code <videoId>.<ext>} (m4a/opus, whatever the best audio
     * is) and returns the file.
     *
     * @param cookies the saved Google login, or empty/null to go without
     */
    public Path downloadAudio(String videoId, Path dir, List<HttpCookie> cookies) throws IOException, InterruptedException {
        Files.createDirectories(dir);
        Path cookieFile = null;
        try {
            if (cookies != null && !cookies.isEmpty()) {
                cookieFile = Files.createTempFile("yt-cookies", ".txt"); // private (mode 600) on POSIX systems
                Files.writeString(cookieFile, toNetscape(cookies), StandardCharsets.UTF_8);
            }

            Output result = run(command(executable, videoId, dir, cookieFile, installedRuntimes(), true));
            // Old yt-dlp versions do not know the JavaScript-runtime options: try again without them
            if (result.exitCode != 0 && (result.text.contains("no such option") || result.text.contains("unrecognized arguments"))) {
                result = run(command(executable, videoId, dir, cookieFile, List.of(), false));
            }

            if (result.exitCode != 0) throw new IOException("yt-dlp failed: " + summarize(result.text));

            // With --print after_move:filepath the last line that is a file is the result
            String[] lines = result.text.split("\\R");
            for (int i = lines.length - 1; i >= 0; i--) {
                String line = lines[i].strip();
                if (line.isEmpty()) continue;
                try {
                    Path candidate = Path.of(line);
                    if (Files.isRegularFile(candidate)) return candidate;
                } catch (RuntimeException ignored) {
                    // Not a path
                }
            }
            throw new IOException("yt-dlp finished but did not report a file: " + summarize(result.text));
        } finally {
            if (cookieFile != null) Files.deleteIfExists(cookieFile);
        }
    }

    // ---- Command line ----

    /** Visible for tests. */
    static List<String> command(String executable, String videoId, Path dir, Path cookieFile, List<String> runtimes, boolean modern) {
        List<String> cmd = new ArrayList<>();
        cmd.add(executable);
        cmd.add("--no-playlist");
        cmd.add("--no-warnings");
        cmd.add("--no-progress");
        if (modern) {
            for (String runtime : runtimes) {
                cmd.add("--js-runtimes");
                cmd.add(runtime);
            }
            // Lets yt-dlp download its player-script solver if it is not bundled with the installed version
            cmd.add("--remote-components");
            cmd.add("ejs:github");
        }
        if (cookieFile != null) {
            cmd.add("--cookies");
            cmd.add(cookieFile.toString());
        }
        // Best audio-only stream; if only combined audio+video streams exist (YouTube's TV client), take the best of
        // those and keep just the audio without re-encoding.
        cmd.addAll(List.of("-f", "ba/b", "-x", "--audio-format", "best"));
        cmd.addAll(List.of("-o", dir.resolve(videoId + ".%(ext)s").toString()));
        cmd.addAll(List.of("--print", "after_move:filepath", "--no-simulate"));
        cmd.add("https://www.youtube.com/watch?v=" + videoId);
        return cmd;
    }

    /** Runtimes yt-dlp can use to solve YouTube's player-script challenges, if installed. */
    static List<String> installedRuntimes() {
        List<String> found = new ArrayList<>();
        String path = System.getenv("PATH");
        if (path == null) return found;
        for (String runtime : RUNTIMES) {
            for (String dir : path.split(java.io.File.pathSeparator)) {
                if (Files.isExecutable(Path.of(dir, runtime)) || Files.isExecutable(Path.of(dir, runtime + ".exe"))) {
                    found.add(runtime);
                    break;
                }
            }
        }
        return found;
    }

    /** The Netscape cookies.txt format yt-dlp reads. Only YouTube and Google cookies are written. */
    static String toNetscape(List<HttpCookie> cookies) {
        StringBuilder sb = new StringBuilder("# Netscape HTTP Cookie File\n");
        long now = System.currentTimeMillis() / 1000;
        for (HttpCookie c : cookies) {
            String domain = c.getDomain() == null ? "" : c.getDomain().toLowerCase(Locale.ROOT);
            String bare = domain.startsWith(".") ? domain.substring(1) : domain;
            if (!(bare.equals("youtube.com") || bare.endsWith(".youtube.com") || bare.equals("google.com") || bare.endsWith(".google.com"))) {
                continue;
            }
            long expires = c.getMaxAge() < 0 ? 0 : now + c.getMaxAge(); // 0 = session cookie
            sb.append(domain).append('\t')
                    .append(domain.startsWith(".") ? "TRUE" : "FALSE").append('\t')
                    .append(c.getPath() == null || c.getPath().isEmpty() ? "/" : c.getPath()).append('\t')
                    .append(c.getSecure() ? "TRUE" : "FALSE").append('\t')
                    .append(expires).append('\t')
                    .append(c.getName()).append('\t')
                    .append(c.getValue()).append('\n');
        }
        return sb.toString();
    }

    // ---- Running it ----

    private record Output(int exitCode, String text) {}

    private static Output run(List<String> command) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        // Read on this thread while the process runs; the timeout is enforced by destroying the process
        Thread watchdog = new Thread(() -> {
            try {
                if (!process.waitFor(TIMEOUT_MINUTES, TimeUnit.MINUTES)) process.destroyForcibly();
            } catch (InterruptedException ignored) {
                process.destroyForcibly();
            }
        }, "yt-dlp-watchdog");
        watchdog.setDaemon(true);
        watchdog.start();

        String text = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int exit = process.waitFor();
        watchdog.interrupt();
        return new Output(exit, text);
    }

    /** The ERROR lines of yt-dlp's output (or the tail), which say what went wrong. */
    static String summarize(String output) {
        List<String> errors = new ArrayList<>();
        for (String line : output.split("\\R")) {
            if (line.startsWith("ERROR")) errors.add(line.strip());
        }
        if (!errors.isEmpty()) return String.join(" ", errors.subList(0, Math.min(3, errors.size())));
        String flat = output.strip().replaceAll("\\s+", " ");
        return flat.length() > 300 ? flat.substring(flat.length() - 300) : flat;
    }
}

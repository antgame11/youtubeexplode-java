package nowplaying;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.net.HttpCookie;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class YtDlpTest {
    @TempDir Path tmp;

    private static HttpCookie cookie(String name, String value, String domain, long maxAge) {
        HttpCookie c = new HttpCookie(name, value);
        c.setDomain(domain);
        c.setPath("/");
        c.setSecure(true);
        c.setMaxAge(maxAge);
        return c;
    }

    @Test
    void writesNetscapeCookiesForYoutubeAndGoogleOnly() {
        String txt = YtDlp.toNetscape(List.of(
                cookie("SAPISID", "abc", ".youtube.com", 3600),
                cookie("SESSION", "s", "accounts.google.com", -1),
                cookie("tracker", "t", ".example.com", 3600)));

        String[] lines = txt.split("\n");
        assertEquals("# Netscape HTTP Cookie File", lines[0]);
        assertEquals(3, lines.length); // header + 2 cookies: example.com is dropped
        String[] f = lines[1].split("\t");
        assertEquals(List.of(".youtube.com", "TRUE", "/", "TRUE"), List.of(f).subList(0, 4));
        assertTrue(Long.parseLong(f[4]) > System.currentTimeMillis() / 1000);
        assertEquals("SAPISID", f[5]);
        assertEquals("abc", f[6]);
        String[] g = lines[2].split("\t");
        assertEquals("FALSE", g[1]); // host-only cookie
        assertEquals("0", g[4]);     // session cookie
    }

    @Test
    void commandLine() {
        List<String> cmd = YtDlp.command("yt-dlp", "abcdefghijk", tmp, tmp.resolve("c.txt"), List.of("deno", "node"), true);
        assertEquals("yt-dlp", cmd.get(0));
        assertEquals("https://www.youtube.com/watch?v=abcdefghijk", cmd.get(cmd.size() - 1));
        assertTrue(cmd.containsAll(List.of("--no-playlist", "-x", "--audio-format", "best", "--cookies", "--remote-components")));
        assertEquals(List.of("deno", "node"), cmd.stream().filter(a -> a.equals("deno") || a.equals("node")).toList());
        assertEquals(2, cmd.stream().filter("--js-runtimes"::equals).count());
        assertTrue(cmd.contains(tmp.resolve("abcdefghijk.%(ext)s").toString()));

        List<String> plain = YtDlp.command("yt-dlp", "abcdefghijk", tmp, null, List.of(), false);
        assertFalse(plain.contains("--cookies"));
        assertFalse(plain.contains("--js-runtimes"));
        assertFalse(plain.contains("--remote-components"));
    }

    /** A stand-in for yt-dlp: shell script created on the fly. */
    private Path fakeYtDlp(String body) throws IOException {
        assumeTrue(!System.getProperty("os.name").toLowerCase().contains("win"), "needs a POSIX shell");
        Path script = tmp.resolve("fake-yt-dlp");
        Files.writeString(script, "#!/bin/sh\n" + body);
        Files.setPosixFilePermissions(script, PosixFilePermissions.fromString("rwxr-xr-x"));
        return script;
    }

    /** Script prologue that finds the -o template and the --cookies file among the arguments. */
    private static final String PARSE_ARGS = """
            OUT=""; COOKIES=""; PREV=""
            for A in "$@"; do
              [ "$PREV" = "-o" ] && OUT="$A"
              [ "$PREV" = "--cookies" ] && COOKIES="$A"
              PREV="$A"
            done
            FILE="${OUT%.%(ext)s}.m4a"
            """;

    @Test
    void downloadsAndReturnsTheReportedFile() throws Exception {
        Path script = fakeYtDlp(PARSE_ARGS + """
                cp "$COOKIES" "%s"
                echo "some chatter"
                echo fake-audio > "$FILE"
                echo "$FILE"
                """.formatted(tmp.resolve("seen-cookies.txt")));

        Path out = tmp.resolve("out");
        Path file = new YtDlp(script.toString()).downloadAudio("abcdefghijk", out, List.of(cookie("SAPISID", "abc", ".youtube.com", 3600)));

        assertEquals(out.resolve("abcdefghijk.m4a"), file);
        assertTrue(Files.isRegularFile(file));
        // The script saw a real cookie file...
        String seen = Files.readString(tmp.resolve("seen-cookies.txt"));
        assertTrue(seen.startsWith("# Netscape HTTP Cookie File"));
        assertTrue(seen.contains("SAPISID\tabc"));
    }

    @Test
    void temporaryCookieFileIsDeleted() throws Exception {
        Path where = tmp.resolve("cookiepath.txt");
        Path script = fakeYtDlp(PARSE_ARGS + """
                echo "$COOKIES" > "%s"
                echo x > "$FILE"; echo "$FILE"
                """.formatted(where));
        new YtDlp(script.toString()).downloadAudio("abcdefghijk", tmp.resolve("o"), List.of(cookie("A", "b", ".youtube.com", -1)));
        Path cookieFile = Path.of(Files.readString(where).strip());
        assertFalse(Files.exists(cookieFile), "the cookie file holds secrets and must not be left behind");
    }

    @Test
    void retriesWithoutNewOptionsForOldVersions() throws Exception {
        Path script = fakeYtDlp(PARSE_ARGS + """
                case "$*" in
                  *--js-runtimes*|*--remote-components*) echo "yt-dlp: error: no such option: --remote-components" >&2; exit 2;;
                esac
                echo x > "$FILE"; echo "$FILE"
                """);
        Path file = new YtDlp(script.toString()).downloadAudio("abcdefghijk", tmp.resolve("o"), List.of());
        assertTrue(Files.isRegularFile(file));
    }

    @Test
    void failureCarriesYtDlpsOwnError() throws Exception {
        Path script = fakeYtDlp("""
                echo "[youtube] abc: Downloading webpage"
                echo "ERROR: [youtube] abc: Sign in to confirm you're not a bot" >&2
                exit 1
                """);
        IOException e = assertThrows(IOException.class,
                () -> new YtDlp(script.toString()).downloadAudio("abcdefghijk", tmp.resolve("o"), List.of()));
        assertTrue(e.getMessage().contains("Sign in to confirm you're not a bot"), e.getMessage());
        assertFalse(e.getMessage().contains("Downloading webpage"));
    }

    @Test
    void successWithoutAReportedFileIsAnError() throws Exception {
        Path script = fakeYtDlp("echo nothing useful\n");
        assertThrows(IOException.class, () -> new YtDlp(script.toString()).downloadAudio("abcdefghijk", tmp.resolve("o"), List.of()));
    }

    /** Real yt-dlp through the Java wrapper. Run with: mvn test -Dtest.excludedGroups=none -Dtest=YtDlpTest#live */
    @org.junit.jupiter.api.Tag("live")
    @Test
    void live() throws Exception {
        var ytDlp = YtDlp.find();
        assumeTrue(ytDlp.isPresent(), "yt-dlp not installed");
        Path file = ytDlp.get().downloadAudio("rZNxD-b4mPY", tmp.resolve("live"), YoutubeSession.loadCookies());
        assertTrue(Files.size(file) > 500_000, "downloaded " + Files.size(file) + " bytes");
        System.out.println("yt-dlp live download: " + file.getFileName() + " " + Files.size(file) + " bytes");
    }
}

package youtubeexplode;

import static org.junit.jupiter.api.Assertions.*;

import java.net.HttpCookie;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import youtubeexplode.login.CookieStore;

class CookieTest {
    @TempDir Path tmp;

    private static HttpCookie cookie(String name, String value, String domain, String path, boolean secure, long maxAge) {
        HttpCookie c = new HttpCookie(name, value);
        c.setDomain(domain);
        c.setPath(path);
        c.setSecure(secure);
        c.setMaxAge(maxAge);
        return c;
    }

    private static CookieJar jar(HttpCookie... cookies) {
        CookieJar jar = new CookieJar();
        for (HttpCookie c : cookies) jar.put(CookieJar.entryOf(c, "youtube.com", System.currentTimeMillis()));
        return jar;
    }

    @Test
    void sendsCookiesOnlyToMatchingHosts() {
        CookieJar jar = jar(
                cookie("SID", "yt", ".youtube.com", "/", true, -1),
                cookie("SID", "goog", ".google.com", "/", true, -1),
                cookie("HOSTONLY", "1", "www.youtube.com", "/", true, -1));

        assertEquals("SID=yt; HOSTONLY=1", jar.header(URI.create("https://www.youtube.com/watch")));
        assertEquals("SID=yt", jar.header(URI.create("https://music.youtube.com/youtubei/v1/search")));
        assertEquals("SID=goog", jar.header(URI.create("https://accounts.google.com/")));
        assertNull(jar.header(URI.create("https://rr1---sn-xyz.googlevideo.com/videoplayback")));
        assertNull(jar.header(URI.create("https://notyoutube.com/")));
    }

    @Test
    void honoursSecurePathAndExpiry() {
        CookieJar jar = jar(
                cookie("S", "1", "youtube.com", "/", true, -1),
                cookie("P", "1", "youtube.com", "/api", false, -1));
        assertEquals("P=1", jar.header(URI.create("http://youtube.com/api/x")));   // "S" is secure: not sent over http
        assertEquals("P=1; S=1", jar.header(URI.create("https://youtube.com/api/x"))); // longer path first
        assertEquals("S=1", jar.header(URI.create("https://youtube.com/")));       // path "/api" does not match "/"
    }

    @Test
    void replacingAndDeleting() {
        CookieJar jar = jar(cookie("A", "1", "youtube.com", "/", true, -1));
        jar.put(CookieJar.entryOf(cookie("A", "2", ".youtube.com", "/", true, -1), "youtube.com", System.currentTimeMillis()));
        assertEquals("A=2", jar.header(URI.create("https://youtube.com/")));
        jar.put(CookieJar.entryOf(cookie("A", "", "youtube.com", "/", true, 0), "youtube.com", System.currentTimeMillis())); // Max-Age=0
        assertNull(jar.header(URI.create("https://youtube.com/")));
    }

    @Test
    void userSuppliedCookiesWithoutDomainGoToYoutube() {
        CookieJar jar = jar(new HttpCookie("LOGIN_INFO", "x"));
        assertEquals("LOGIN_INFO=x", jar.header(URI.create("https://www.youtube.com/")));
    }

    @Test
    void sapisidComesFromTheMatchingDomain() {
        CookieJar jar = jar(
                cookie("SAPISID", "from-google", ".google.com", "/", true, -1),
                cookie("SAPISID", "from-youtube", ".youtube.com", "/", true, -1));
        assertEquals("from-youtube", jar.valueFor("SAPISID", URI.create("https://www.youtube.com/")));
        assertEquals("from-google", jar.valueFor("SAPISID", URI.create("https://accounts.google.com/")));
    }

    @Test
    void jsonRoundTripKeepsEverythingAndIsPrivate() throws Exception {
        Path file = tmp.resolve("sub/cookies.json");
        CookieStore.save(file, List.of(
                cookie("SAPISID", "abc", ".youtube.com", "/", true, 3600),
                cookie("SESSION", "s", ".google.com", "/accounts", true, -1)));

        List<HttpCookie> loaded = CookieStore.load(file);
        assertEquals(2, loaded.size());
        assertEquals("abc", loaded.get(0).getValue());
        assertEquals(".youtube.com", loaded.get(0).getDomain());
        assertTrue(loaded.get(0).getMaxAge() > 3500 && loaded.get(0).getMaxAge() <= 3600);
        assertEquals(-1, loaded.get(1).getMaxAge());
        assertEquals("/accounts", loaded.get(1).getPath());

        try {
            assertEquals("rw-------", java.nio.file.attribute.PosixFilePermissions.toString(Files.getPosixFilePermissions(file)));
        } catch (UnsupportedOperationException ignored) {
            // not a POSIX file system
        }
    }

    @Test
    void expiredCookiesAreNotLoaded() throws Exception {
        Path file = tmp.resolve("c.json");
        Files.writeString(file, "[{\"name\":\"OLD\",\"value\":\"1\",\"domain\":\".youtube.com\",\"path\":\"/\",\"secure\":true,\"expires\":1000},"
                + "{\"name\":\"NEW\",\"value\":\"2\",\"domain\":\".youtube.com\",\"path\":\"/\",\"secure\":true,\"expires\":-1}]");
        List<HttpCookie> loaded = CookieStore.load(file);
        assertEquals(1, loaded.size());
        assertEquals("NEW", loaded.get(0).getName());
    }

    @Test
    void readsNetscapeCookiesTxt() throws Exception {
        long future = System.currentTimeMillis() / 1000 + 86400;
        Path file = tmp.resolve("cookies.txt");
        Files.writeString(file, "# Netscape HTTP Cookie File\n"
                + ".youtube.com\tTRUE\t/\tTRUE\t" + future + "\tSAPISID\tabc123\n"
                + "#HttpOnly_.youtube.com\tTRUE\t/\tTRUE\t0\tLOGIN_INFO\tinfo\n"
                + ".youtube.com\tTRUE\t/\tTRUE\t5\tEXPIRED\tx\n"
                + "garbage line\n");
        List<HttpCookie> loaded = CookieStore.load(file);
        assertEquals(List.of("SAPISID", "LOGIN_INFO"), loaded.stream().map(HttpCookie::getName).toList());
        assertEquals(-1, loaded.get(1).getMaxAge()); // expiry 0 = session
    }
}

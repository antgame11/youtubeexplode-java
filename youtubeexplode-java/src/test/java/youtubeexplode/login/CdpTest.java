package youtubeexplode.login;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.net.HttpCookie;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Talks to a real (headless) Chrome/Chromium; skipped when none is installed. */
class CdpTest {
    @TempDir Path profile;

    private static HttpCookie cookie(String name, String value, String domain) {
        HttpCookie c = new HttpCookie(name, value);
        c.setDomain(domain);
        c.setPath("/");
        c.setSecure(true);
        c.setMaxAge(3600);
        return c;
    }

    @Test
    void readsCookiesFromABrowserOverDevTools() throws Exception {
        Optional<Path> browser = GoogleLogin.findBrowser();
        assumeTrue(browser.isPresent(), "no Chrome/Chromium installed");

        Process p = new ProcessBuilder(browser.get().toString(), "--headless=new", "--no-sandbox", "--disable-gpu",
                "--user-data-dir=" + profile, "--remote-debugging-port=0", "about:blank")
                .redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
        try {
            Path portFile = profile.resolve("DevToolsActivePort");
            for (int i = 0; i < 100 && !(Files.exists(portFile) && Files.readAllLines(portFile).size() >= 2); i++) Thread.sleep(100);
            List<String> lines = Files.readAllLines(portFile);

            try (CdpClient cdp = CdpClient.connect(URI.create("ws://127.0.0.1:" + lines.get(0) + lines.get(1)))) {
                assertFalse(GoogleLogin.isLoggedIn(cdp.getCookies()));

                cdp.setCookies(List.of(
                        cookie("SAPISID", "secret-value", ".youtube.com"),
                        cookie("LOGIN_INFO", "li", ".youtube.com"),
                        cookie("NID", "n", ".google.com"),
                        cookie("tracker", "t", ".example.com")));

                List<HttpCookie> all = cdp.getCookies();
                assertTrue(GoogleLogin.isLoggedIn(all));

                List<HttpCookie> kept = GoogleLogin.relevant(all);
                assertEquals(3, kept.size()); // example.com is dropped
                HttpCookie sapisid = kept.stream().filter(c -> c.getName().equals("SAPISID")).findFirst().orElseThrow();
                assertEquals("secret-value", sapisid.getValue());
                assertTrue(sapisid.getSecure());
                assertTrue(sapisid.getMaxAge() > 3000 && sapisid.getMaxAge() <= 3600);

                cdp.closeBrowser();
            }
            assertTrue(p.waitFor(10, java.util.concurrent.TimeUnit.SECONDS), "browser should close on request");
        } finally {
            p.destroyForcibly();
        }
    }

    @Test
    void loginDetectionNeedsYoutubeSessionCookies() {
        assertFalse(GoogleLogin.isLoggedIn(List.of(cookie("SAPISID", "x", ".google.com")))); // google only: not enough
        assertFalse(GoogleLogin.isLoggedIn(List.of(cookie("NID", "x", ".youtube.com"))));
        assertTrue(GoogleLogin.isLoggedIn(List.of(cookie("__Secure-3PAPISID", "x", ".youtube.com"))));
        assertTrue(GoogleLogin.isLoggedIn(List.of(cookie("SAPISID", "x", "music.youtube.com"))));
    }

    /**
     * Regression test for "This browser or app may not be secure": Chrome marks itself as automated
     * (navigator.webdriver == true) when remote debugging is on, and Google rejects such browsers. The command
     * line GoogleLogin builds must keep the flag off.
     */
    @Test
    void loginBrowserDoesNotLookAutomated() throws Exception {
        Optional<Path> browser = GoogleLogin.findBrowser();
        assumeTrue(browser.isPresent(), "no Chrome/Chromium installed");

        String page = "data:text/html,<title>x</title><script>document.title='webdriver='+navigator.webdriver</script>";
        List<String> cmd = new java.util.ArrayList<>(GoogleLogin.command(browser.get(), profile, "--headless=new", "--disable-gpu", page));
        if (!cmd.contains("--no-sandbox")) cmd.add(1, "--no-sandbox"); // CI containers

        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
        try {
            Path portFile = profile.resolve("DevToolsActivePort");
            for (int i = 0; i < 100 && !(Files.exists(portFile) && Files.readAllLines(portFile).size() >= 2); i++) Thread.sleep(100);
            String port = Files.readAllLines(portFile).get(0).strip();

            // Read the page title from the DevTools HTTP endpoint: this does not attach to the page
            var http = java.net.http.HttpClient.newHttpClient();
            String title = "";
            for (int i = 0; i < 30 && !title.startsWith("webdriver"); i++) {
                Thread.sleep(200);
                String json = http.send(java.net.http.HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/json/list")).build(),
                        java.net.http.HttpResponse.BodyHandlers.ofString()).body();
                var m = java.util.regex.Pattern.compile("\"title\":\\s*\"(webdriver=[a-z]+)\"").matcher(json);
                if (m.find()) title = m.group(1);
            }
            assertEquals("webdriver=false", title);

            // Shut the browser down properly: its child processes keep writing into the profile folder
            // and would make the temp directory cleanup fail.
            try (CdpClient cdp = CdpClient.connect(URI.create("ws://127.0.0.1:" + port + Files.readAllLines(portFile).get(1)))) {
                cdp.closeBrowser();
            }
            assertTrue(p.waitFor(10, java.util.concurrent.TimeUnit.SECONDS), "browser should close on request");
            Thread.sleep(300);
        } finally {
            p.destroyForcibly();
        }
    }
}

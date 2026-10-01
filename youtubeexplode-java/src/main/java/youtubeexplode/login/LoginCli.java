package youtubeexplode.login;

import java.net.HttpCookie;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

/** Command line login: {@code java -cp youtubeexplode-java.jar youtubeexplode.login.LoginCli [cookies-file]}. */
public final class LoginCli {
    public static void main(String[] args) throws Exception {
        Path file = args.length > 0 ? Path.of(args[0]) : CookieStore.defaultPath();
        List<HttpCookie> cookies = GoogleLogin.login(GoogleLogin.defaultProfileDir(), Duration.ofMinutes(5), System.err::println);
        CookieStore.save(file, cookies);
        System.err.println("Saved " + cookies.size() + " cookies to " + file + " (keep this file private).");
    }
}

package youtubeexplode.login;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.HttpCookie;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.List;

/**
 * Saves and loads a YouTube login (cookies) as a file. The file is readable only by you (mode 600
 * where supported) because anyone who has it can act as your Google account: never share or commit it.
 * Loading also understands the Netscape {@code cookies.txt} format that browser extensions export.
 */
public final class CookieStore {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private CookieStore() {}

    /** {@code $YOUTUBE_COOKIES_FILE}, or {@code ~/.config/youtubeexplode/cookies.json}. */
    public static Path defaultPath() {
        String fromEnv = System.getenv("YOUTUBE_COOKIES_FILE");
        if (fromEnv != null && !fromEnv.isBlank()) return Path.of(fromEnv);
        return configDir().resolve("cookies.json");
    }

    public static Path configDir() {
        return Path.of(System.getProperty("user.home"), ".config", "youtubeexplode");
    }

    public static void save(Path file, List<HttpCookie> cookies) throws IOException {
        ArrayNode array = MAPPER.createArrayNode();
        long now = System.currentTimeMillis();
        for (HttpCookie c : cookies) {
            ObjectNode o = array.addObject();
            o.put("name", c.getName());
            o.put("value", c.getValue());
            o.put("domain", c.getDomain());
            o.put("path", c.getPath() == null ? "/" : c.getPath());
            o.put("secure", c.getSecure());
            o.put("expires", c.getMaxAge() < 0 ? -1 : (now / 1000) + c.getMaxAge()); // epoch seconds, -1 = session
        }

        Path parent = file.toAbsolutePath().getParent();
        if (parent != null) Files.createDirectories(parent);

        // Create the file private *before* writing the secrets into it
        if (!Files.exists(file)) {
            try {
                Files.createFile(file, java.nio.file.attribute.PosixFilePermissions.asFileAttribute(
                        PosixFilePermissions.fromString("rw-------")));
            } catch (UnsupportedOperationException e) {
                Files.createFile(file); // not a POSIX file system
            }
        }
        Files.writeString(file, MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(array));
    }

    /** Loads a JSON file written by {@link #save} or a Netscape cookies.txt. Expired cookies are skipped. */
    public static List<HttpCookie> load(Path file) throws IOException {
        String text = Files.readString(file).strip();
        return text.startsWith("[") ? loadJson(text) : loadNetscape(text);
    }

    private static List<HttpCookie> loadJson(String text) throws IOException {
        List<HttpCookie> result = new ArrayList<>();
        JsonNode root = MAPPER.readTree(text);
        for (JsonNode o : root) {
            HttpCookie cookie = toCookie(
                    o.path("name").asText(), o.path("value").asText(), o.path("domain").asText(null),
                    o.path("path").asText("/"), o.path("secure").asBoolean(true), o.path("expires").asLong(-1));
            if (cookie != null) result.add(cookie);
        }
        return result;
    }

    /** Tab separated: domain, include-subdomains, path, secure, expiry, name, value ("#HttpOnly_" prefix allowed). */
    static List<HttpCookie> loadNetscape(String text) {
        List<HttpCookie> result = new ArrayList<>();
        for (String raw : text.split("\\R")) {
            String line = raw.strip();
            if (line.startsWith("#HttpOnly_")) line = line.substring("#HttpOnly_".length());
            else if (line.isEmpty() || line.startsWith("#")) continue;

            String[] f = line.split("\t");
            if (f.length < 7) continue;
            long expires;
            try {
                expires = Long.parseLong(f[4].strip());
            } catch (NumberFormatException e) {
                continue;
            }
            HttpCookie cookie = toCookie(f[5], f[6], f[0], f[2], f[3].equalsIgnoreCase("TRUE"), expires <= 0 ? -1 : expires);
            if (cookie != null) result.add(cookie);
        }
        return result;
    }

    /** @param expires epoch seconds, or -1 for a session cookie; null result means already expired */
    private static HttpCookie toCookie(String name, String value, String domain, String path, boolean secure, long expires) {
        long now = System.currentTimeMillis() / 1000;
        if (expires >= 0 && expires <= now) return null;

        HttpCookie cookie = new HttpCookie(name, value);
        cookie.setDomain(domain);
        cookie.setPath(path == null || path.isEmpty() ? "/" : path);
        cookie.setSecure(secure);
        cookie.setMaxAge(expires < 0 ? -1 : expires - now);
        return cookie;
    }
}

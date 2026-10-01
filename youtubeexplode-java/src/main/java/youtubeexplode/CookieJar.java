package youtubeexplode;

import java.net.HttpCookie;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Cookie storage that follows the browser rules that matter here: cookies are only sent to hosts that
 * match their domain, over https, for matching paths, and expired cookies are dropped. (A plain
 * name-to-value map breaks with real browser cookies: Google sets the same names on several domains.)
 */
final class CookieJar {
    record Entry(String name, String value, String domain, String path, boolean secure, long expiresAtMs) {
        boolean expired(long now) {
            return expiresAtMs <= now;
        }

        boolean matches(URI uri, long now) {
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            String requestPath = uri.getPath() == null || uri.getPath().isEmpty() ? "/" : uri.getPath();
            boolean https = "https".equalsIgnoreCase(uri.getScheme());
            return !expired(now)
                    && (host.equals(domain) || host.endsWith("." + domain))
                    && requestPath.startsWith(path)
                    && (https || !secure);
        }
    }

    private final List<Entry> entries = new ArrayList<>();

    /** Converts an {@link HttpCookie}; cookies without a domain apply to {@code defaultDomain}. */
    static Entry entryOf(HttpCookie cookie, String defaultDomain, long now) {
        String domain = cookie.getDomain();
        if (domain == null || domain.isBlank()) domain = defaultDomain;
        domain = domain.toLowerCase(Locale.ROOT);
        if (domain.startsWith(".")) domain = domain.substring(1);

        long maxAge = cookie.getMaxAge();
        long expiresAt = maxAge < 0 ? Long.MAX_VALUE : now + maxAge * 1000;
        String path = cookie.getPath() == null || cookie.getPath().isEmpty() ? "/" : cookie.getPath();
        return new Entry(cookie.getName(), cookie.getValue(), domain, path, cookie.getSecure(), expiresAt);
    }

    /** Adds or replaces a cookie (same name, domain and path). An already expired cookie deletes it. */
    synchronized void put(Entry entry) {
        entries.removeIf(e -> e.name().equals(entry.name()) && e.domain().equals(entry.domain()) && e.path().equals(entry.path()));
        if (!entry.expired(System.currentTimeMillis())) entries.add(entry);
    }

    synchronized boolean isEmpty() {
        return entries.isEmpty();
    }

    /** Value for a {@code Cookie} header, or null if no cookie applies. Longer paths come first. */
    synchronized String header(URI uri) {
        long now = System.currentTimeMillis();
        List<Entry> matching = entries.stream()
                .filter(e -> e.matches(uri, now))
                .sorted((a, b) -> Integer.compare(b.path().length(), a.path().length()))
                .toList();
        if (matching.isEmpty()) return null;

        StringBuilder sb = new StringBuilder();
        for (Entry e : matching) {
            if (sb.length() > 0) sb.append("; ");
            sb.append(e.name()).append('=').append(e.value());
        }
        return sb.toString();
    }

    synchronized String valueFor(String name, URI uri) {
        long now = System.currentTimeMillis();
        for (Entry e : entries) {
            if (e.name().equals(name) && e.matches(uri, now) && !e.value().isBlank()) return e.value();
        }
        return null;
    }

    /** Current cookies, with {@code maxAge} set to the remaining lifetime (-1 for session cookies). */
    synchronized List<HttpCookie> snapshot() {
        long now = System.currentTimeMillis();
        List<HttpCookie> result = new ArrayList<>();
        for (Entry e : entries) {
            if (e.expired(now)) continue;
            HttpCookie cookie = new HttpCookie(e.name(), e.value());
            cookie.setDomain("." + e.domain());
            cookie.setPath(e.path());
            cookie.setSecure(e.secure());
            cookie.setMaxAge(e.expiresAtMs() == Long.MAX_VALUE ? -1 : Math.max(1, (e.expiresAtMs() - now) / 1000));
            result.add(cookie);
        }
        return result;
    }
}

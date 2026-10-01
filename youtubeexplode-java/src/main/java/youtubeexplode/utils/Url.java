package youtubeexplode.utils;

import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/** Query string helpers. */
public final class Url {
    private Url() {}

    public static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    /** RFC 3986 style escaping (spaces as %20), matching Uri.EscapeDataString. */
    public static String escape(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20").replace("*", "%2A");
    }

    private static Map<String, String> parameters(String url) {
        int q = url.indexOf('?');
        String query = q >= 0 ? url.substring(q + 1) : url;
        int hash = query.indexOf('#');
        if (hash >= 0) query = query.substring(0, hash);

        Map<String, String> result = new LinkedHashMap<>();
        for (String parameter : query.split("&")) {
            int eq = parameter.indexOf('=');
            String key = decode(eq >= 0 ? parameter.substring(0, eq) : parameter);
            String value = decode(eq >= 0 ? parameter.substring(eq + 1) : "");
            if (key.isBlank()) continue;
            result.put(key, value);
        }
        return result;
    }

    public static Map<String, String> getQueryParameters(String url) {
        return parameters(url);
    }

    public static String tryGetQueryParameter(String url, String key) {
        return parameters(url).get(key);
    }

    public static boolean containsQueryParameter(String url, String key) {
        return tryGetQueryParameter(url, key) != null;
    }

    public static String removeQueryParameter(String url, String key) {
        if (!containsQueryParameter(url, key)) return url;

        int q = url.indexOf('?');
        String head = q >= 0 ? url.substring(0, q) : url;
        StringBuilder query = new StringBuilder();
        for (Map.Entry<String, String> e : parameters(url).entrySet()) {
            if (e.getKey().equals(key)) continue;
            query.append(query.length() > 0 ? '&' : '?');
            query.append(escape(e.getKey())).append('=').append(escape(e.getValue()));
        }
        return head + query;
    }

    public static String setQueryParameter(String url, String key, String value) {
        String without = removeQueryParameter(url, key);
        boolean hasOthers = without.contains("?");
        return without + (hasOthers ? '&' : '?') + escape(key) + '=' + escape(value);
    }

    /** Host of the URL without any "www." handling, e.g. "www.youtube.com". */
    public static String domain(URI uri) {
        return uri.getScheme() + "://" + uri.getHost();
    }
}

package youtubeexplode.exceptions;

import java.io.IOException;
import java.io.UncheckedIOException;

/** Thrown when YouTube responds with an unsuccessful HTTP status code. */
public class HttpStatusException extends UncheckedIOException {
    private final int statusCode;

    public HttpStatusException(int statusCode, String url) {
        this(statusCode, url, null);
    }

    public HttpStatusException(int statusCode, String url, String responseBody) {
        super("HTTP " + statusCode + " for " + url + summarize(responseBody), new IOException("HTTP " + statusCode));
        this.statusCode = statusCode;
    }

    /** First part of the response body, whitespace collapsed, so the message stays readable. */
    private static String summarize(String body) {
        if (body == null || body.isBlank()) return "";
        String flat = body.replaceAll("\\s+", " ").strip();
        return " - " + (flat.length() > 400 ? flat.substring(0, 400) + "..." : flat);
    }

    public int getStatusCode() {
        return statusCode;
    }
}

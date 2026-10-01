package youtubeexplode.exceptions;

import java.io.IOException;
import java.io.UncheckedIOException;

/** Thrown when YouTube responds with an unsuccessful HTTP status code. */
public class HttpStatusException extends UncheckedIOException {
    private final int statusCode;

    public HttpStatusException(int statusCode, String url) {
        super("HTTP " + statusCode + " for " + url, new IOException("HTTP " + statusCode));
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }
}

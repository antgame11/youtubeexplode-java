package youtubeexplode.exceptions;

/** Thrown when YouTube rate-limits the client (HTTP 429). */
public class RequestLimitExceededException extends YoutubeExplodeException {
    public RequestLimitExceededException(String message) {
        super(message);
    }
}

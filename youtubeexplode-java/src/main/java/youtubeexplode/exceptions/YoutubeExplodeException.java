package youtubeexplode.exceptions;

/** Base type for all exceptions thrown by the library. */
public class YoutubeExplodeException extends RuntimeException {
    public YoutubeExplodeException(String message) {
        super(message);
    }

    public YoutubeExplodeException(String message, Throwable cause) {
        super(message, cause);
    }
}

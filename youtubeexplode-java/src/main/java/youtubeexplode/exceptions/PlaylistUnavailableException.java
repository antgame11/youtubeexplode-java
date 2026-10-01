package youtubeexplode.exceptions;

/** Thrown when a playlist is not available. */
public class PlaylistUnavailableException extends YoutubeExplodeException {
    public PlaylistUnavailableException(String message) {
        super(message);
    }
}

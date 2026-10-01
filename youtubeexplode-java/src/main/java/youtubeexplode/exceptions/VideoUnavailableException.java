package youtubeexplode.exceptions;

/** Thrown when a video is not available (deleted, private, etc.). */
public class VideoUnavailableException extends VideoUnplayableException {
    public VideoUnavailableException(String message) {
        super(message);
    }
}

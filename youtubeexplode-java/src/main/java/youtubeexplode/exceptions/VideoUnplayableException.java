package youtubeexplode.exceptions;

/** Thrown when a video is not playable. */
public class VideoUnplayableException extends YoutubeExplodeException {
    public VideoUnplayableException(String message) {
        super(message);
    }
}

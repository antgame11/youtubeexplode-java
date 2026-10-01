package youtubeexplode.exceptions;

import youtubeexplode.videos.VideoId;

/** Thrown when a video requires purchase to be played. */
public class VideoRequiresPurchaseException extends VideoUnplayableException {
    private final VideoId previewVideoId;

    public VideoRequiresPurchaseException(String message, VideoId previewVideoId) {
        super(message);
        this.previewVideoId = previewVideoId;
    }

    /** ID of the free preview (trailer) video. */
    public VideoId getPreviewVideoId() {
        return previewVideoId;
    }
}

package youtubeexplode.videos.closedcaptions;

import java.time.Duration;

/** A word or phrase inside a closed caption. */
public final class ClosedCaptionPart {
    private final String text;
    private final Duration offset;

    public ClosedCaptionPart(String text, Duration offset) {
        this.text = text;
        this.offset = offset;
    }

    public String getText() {
        return text;
    }

    /** Time at which the part starts, relative to the start of the caption. */
    public Duration getOffset() {
        return offset;
    }

    @Override
    public String toString() {
        return text;
    }
}

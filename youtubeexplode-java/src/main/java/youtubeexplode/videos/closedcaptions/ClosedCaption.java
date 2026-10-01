package youtubeexplode.videos.closedcaptions;

import java.time.Duration;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

/** Closed caption displayed at a specific time range. */
public final class ClosedCaption {
    private final String text;
    private final Duration offset;
    private final Duration duration;
    private final List<ClosedCaptionPart> parts;

    public ClosedCaption(String text, Duration offset, Duration duration, List<ClosedCaptionPart> parts) {
        this.text = text;
        this.offset = offset;
        this.duration = duration;
        this.parts = List.copyOf(parts);
    }

    public String getText() {
        return text;
    }

    public Duration getOffset() {
        return offset;
    }

    public Duration getDuration() {
        return duration;
    }

    public List<ClosedCaptionPart> getParts() {
        return parts;
    }

    public Optional<ClosedCaptionPart> tryGetPartByTime(Duration time) {
        return parts.stream().filter(p -> p.getOffset().compareTo(time) >= 0).findFirst();
    }

    public ClosedCaptionPart getPartByTime(Duration time) {
        return tryGetPartByTime(time)
                .orElseThrow(() -> new NoSuchElementException("No closed caption part found at " + time + "."));
    }

    @Override
    public String toString() {
        return text;
    }
}

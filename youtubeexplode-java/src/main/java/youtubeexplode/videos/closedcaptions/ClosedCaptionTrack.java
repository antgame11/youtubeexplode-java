package youtubeexplode.videos.closedcaptions;

import java.time.Duration;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

/** Closed caption track with its captions. */
public final class ClosedCaptionTrack {
    private final List<ClosedCaption> captions;

    public ClosedCaptionTrack(List<ClosedCaption> captions) {
        this.captions = List.copyOf(captions);
    }

    public List<ClosedCaption> getCaptions() {
        return captions;
    }

    public Optional<ClosedCaption> tryGetByTime(Duration time) {
        return captions.stream()
                .filter(c -> time.compareTo(c.getOffset()) >= 0
                        && time.compareTo(c.getOffset().plus(c.getDuration())) <= 0)
                .findFirst();
    }

    public ClosedCaption getByTime(Duration time) {
        return tryGetByTime(time)
                .orElseThrow(() -> new NoSuchElementException("No closed caption found at " + time + "."));
    }
}

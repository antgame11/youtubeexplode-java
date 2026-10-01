package youtubeexplode.videos.closedcaptions;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

/** Manifest that lists the closed caption tracks available for a video. */
public final class ClosedCaptionManifest {
    private final List<ClosedCaptionTrackInfo> tracks;

    public ClosedCaptionManifest(List<ClosedCaptionTrackInfo> tracks) {
        this.tracks = List.copyOf(tracks);
    }

    public List<ClosedCaptionTrackInfo> getTracks() {
        return tracks;
    }

    /** Finds a track by language code or name (case-insensitive). */
    public Optional<ClosedCaptionTrackInfo> tryGetByLanguage(String language) {
        return tracks.stream()
                .filter(t -> t.getLanguage().getCode().equalsIgnoreCase(language)
                        || t.getLanguage().getName().equalsIgnoreCase(language))
                .findFirst();
    }

    public ClosedCaptionTrackInfo getByLanguage(String language) {
        return tryGetByLanguage(language)
                .orElseThrow(() -> new NoSuchElementException(
                        "No closed caption track available for language '" + language + "'."));
    }
}

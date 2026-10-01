package nowplaying.web;

import java.nio.file.Path;
import nowplaying.NowPlaying;

/** Turns a Spotify track into a local audio file a browser can play. */
public interface AudioResolver {
    /** A playable local file. */
    record Resolved(Path file, String mimeType, String matchedTitle, String videoId) {}

    Resolved resolve(NowPlaying track) throws Exception;
}

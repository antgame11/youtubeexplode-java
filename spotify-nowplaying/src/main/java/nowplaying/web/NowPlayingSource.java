package nowplaying.web;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import nowplaying.NowPlaying;

/** Where "what is playing" comes from (Spotify in production, a fake in tests). */
public interface NowPlayingSource {
    /** The current track; empty if nothing (or no music track) is playing. */
    Optional<NowPlaying> current() throws IOException, InterruptedException;

    /** Upcoming tracks, used to prefetch audio. Best effort. */
    default List<NowPlaying> upNext() throws IOException, InterruptedException {
        return List.of();
    }
}

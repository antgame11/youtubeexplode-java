package youtubeexplode.music;

import java.time.Duration;
import java.util.List;
import youtubeexplode.common.Thumbnail;
import youtubeexplode.videos.VideoId;

/** A result of a YouTube Music search. */
public sealed interface MusicSearchResult {
    String title();

    String url();

    List<Thumbnail> thumbnails();

    /**
     * A song: an official audio track (and not a user-uploaded video). Its {@link #id()} can be
     * passed to the regular video and stream clients to download the audio.
     */
    record Song(
            VideoId id,
            String title,
            List<MusicRef> artists,
            MusicRef album,
            Duration duration,
            boolean isExplicit,
            List<Thumbnail> thumbnails)
            implements MusicSearchResult {
        @Override
        public String url() {
            return "https://music.youtube.com/watch?v=" + id;
        }

        @Override
        public String toString() {
            return "Song (" + title + ")";
        }
    }

    /** A music video or user-uploaded video. */
    record Video(
            VideoId id,
            String title,
            MusicRef channel,
            String viewsText,
            Duration duration,
            List<Thumbnail> thumbnails)
            implements MusicSearchResult {
        @Override
        public String url() {
            return "https://music.youtube.com/watch?v=" + id;
        }

        @Override
        public String toString() {
            return "Video (" + title + ")";
        }
    }

    /** An album, single or EP. */
    record Album(
            String browseId,
            String title,
            String type,
            List<MusicRef> artists,
            String year,
            List<Thumbnail> thumbnails)
            implements MusicSearchResult {
        @Override
        public String url() {
            return "https://music.youtube.com/browse/" + browseId;
        }

        @Override
        public String toString() {
            return type + " (" + title + ")";
        }
    }

    /** An artist. */
    record Artist(String browseId, String title, String subtitle, List<Thumbnail> thumbnails)
            implements MusicSearchResult {
        @Override
        public String url() {
            return "https://music.youtube.com/channel/" + browseId;
        }

        @Override
        public String toString() {
            return "Artist (" + title + ")";
        }
    }
}

package youtubeexplode;

import java.net.HttpCookie;
import java.net.http.HttpClient;
import java.util.List;
import youtubeexplode.channels.ChannelClient;
import youtubeexplode.music.MusicClient;
import youtubeexplode.playlists.PlaylistClient;
import youtubeexplode.search.SearchClient;
import youtubeexplode.videos.VideoClient;

/**
 * Client for interacting with YouTube. All operations are blocking; run them on a separate (for
 * example virtual) thread to keep your application responsive. Interrupting the thread cancels
 * an in-flight operation.
 */
public final class YoutubeClient implements AutoCloseable {
    private final YoutubeHttp http;
    private final VideoClient videos;
    private final PlaylistClient playlists;
    private final ChannelClient channels;
    private final SearchClient search;
    private final MusicClient music;

    public YoutubeClient(HttpClient http, List<HttpCookie> initialCookies) {
        this.http = new YoutubeHttp(http, List.copyOf(initialCookies));
        this.videos = new VideoClient(this.http);
        this.playlists = new PlaylistClient(this.http);
        this.channels = new ChannelClient(this.http);
        this.search = new SearchClient(this.http);
        this.music = new MusicClient(this.http);
    }

    public YoutubeClient(HttpClient http) {
        this(http, List.of());
    }

    public YoutubeClient(List<HttpCookie> initialCookies) {
        this(YoutubeHttp.sharedClient(), initialCookies);
    }

    public YoutubeClient() {
        this(YoutubeHttp.sharedClient());
    }

    /** Operations related to YouTube videos. */
    public VideoClient videos() {
        return videos;
    }

    /** Operations related to YouTube playlists. */
    public PlaylistClient playlists() {
        return playlists;
    }

    /** Operations related to YouTube channels. */
    public ChannelClient channels() {
        return channels;
    }

    /** Operations related to YouTube search. */
    public SearchClient search() {
        return search;
    }

    /** Operations related to YouTube Music. */
    public MusicClient music() {
        return music;
    }

    @Override
    public void close() {
        http.close();
    }
}

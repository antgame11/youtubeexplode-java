package youtubeexplode;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.HttpCookie;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import youtubeexplode.channels.ChannelClient;
import youtubeexplode.login.CookieStore;
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

    /**
     * Creates a client that uses a saved YouTube login (see {@link CookieStore} and
     * {@link youtubeexplode.login.GoogleLogin}). If the file is null or does not exist, the client is
     * simply not logged in.
     */
    public static YoutubeClient withLogin(Path cookiesFile) {
        List<HttpCookie> cookies = List.of();
        if (cookiesFile != null && Files.exists(cookiesFile)) {
            try {
                cookies = CookieStore.load(cookiesFile);
            } catch (IOException e) {
                throw new UncheckedIOException("Could not read the saved YouTube login " + cookiesFile, e);
            }
        }
        return new YoutubeClient(cookies);
    }

    /**
     * The client's current cookies, including any that YouTube rotated while the client was in use.
     * Save them with {@link CookieStore#save} to keep a login alive.
     */
    public List<HttpCookie> getCookies() {
        return http.cookies();
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

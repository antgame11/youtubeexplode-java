package youtubeexplode.music;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Stream;
import youtubeexplode.YoutubeHttp;
import youtubeexplode.common.Batch;
import youtubeexplode.common.Batches;
import youtubeexplode.utils.Json;
import youtubeexplode.utils.Strings;
import youtubeexplode.utils.Url;

/**
 * Operations related to YouTube Music. Song and video results carry a video ID, so they can be
 * passed straight to {@code youtube.videos()} to get metadata or download the audio.
 */
public final class MusicClient {
    private static final String SEARCH_URL = "https://music.youtube.com/youtubei/v1/search";

    private final YoutubeHttp http;

    public MusicClient(YoutubeHttp http) {
        this.http = http;
    }

    private static String context() {
        return """
                "context": {
                  "client": {
                    "clientName": "WEB_REMIX",
                    "clientVersion": "1.20260928.13.00",
                    "hl": "en",
                    "gl": "US",
                    "utcOffsetMinutes": 0
                  }
                }""";
    }

    private MusicSearchResponse requestSearch(String query, MusicSearchFilter filter, String continuationToken) {
        String url;
        String body;

        if (continuationToken == null) {
            url = SEARCH_URL + "?prettyPrint=false";
            body = "{\"query\": " + Json.encode(query) + ", \"params\": " + Json.encode(filter.params()) + ", " + context()
                    + "}";
        } else {
            String token = Url.escape(continuationToken);
            url = SEARCH_URL + "?ctoken=" + token + "&continuation=" + token + "&type=next&prettyPrint=false";
            body = "{" + context() + "}";
        }

        return MusicSearchResponse.parse(http.string(YoutubeHttp.Request.postJson(url, body)));
    }

    /** Searches YouTube Music, returning lazily fetched batches of results. */
    public Batches<MusicSearchResult> getResultBatches(String searchQuery, MusicSearchFilter filter) {
        return Batches.paged(() -> new Supplier<Batch<MusicSearchResult>>() {
            private final Set<String> encounteredIds = new HashSet<>();
            private String continuationToken;
            private boolean started;
            private boolean finished;

            @Override
            public Batch<MusicSearchResult> get() {
                if (finished) return null;
                if (started && Strings.isBlank(continuationToken)) {
                    finished = true;
                    return null;
                }
                started = true;

                MusicSearchResponse response = requestSearch(searchQuery, filter, continuationToken);
                continuationToken = response.continuationToken();

                List<MusicSearchResult> results = new ArrayList<>();
                for (MusicSearchResult result : response.results()) {
                    // Don't yield the same result twice
                    if (encounteredIds.add(idOf(result))) results.add(result);
                }
                return new Batch<>(results);
            }
        });
    }

    private static String idOf(MusicSearchResult result) {
        if (result instanceof MusicSearchResult.Song s) return "v:" + s.id();
        if (result instanceof MusicSearchResult.Video v) return "v:" + v.id();
        if (result instanceof MusicSearchResult.Album a) return "b:" + a.browseId();
        return "b:" + ((MusicSearchResult.Artist) result).browseId();
    }

    /** Searches everything (songs, videos, albums, artists mixed). Returns a single page. */
    public Stream<MusicSearchResult> getResults(String searchQuery) {
        return getResultBatches(searchQuery, MusicSearchFilter.NONE).stream();
    }

    /** Searches songs (official audio tracks) as a lazy stream. */
    public Stream<MusicSearchResult.Song> getSongs(String searchQuery) {
        return getResultBatches(searchQuery, MusicSearchFilter.SONGS)
                .stream()
                .filter(MusicSearchResult.Song.class::isInstance)
                .map(MusicSearchResult.Song.class::cast);
    }

    /** Searches music videos as a lazy stream. */
    public Stream<MusicSearchResult.Video> getVideos(String searchQuery) {
        return getResultBatches(searchQuery, MusicSearchFilter.VIDEOS)
                .stream()
                .filter(MusicSearchResult.Video.class::isInstance)
                .map(MusicSearchResult.Video.class::cast);
    }

    /** Searches albums, singles and EPs as a lazy stream. */
    public Stream<MusicSearchResult.Album> getAlbums(String searchQuery) {
        return getResultBatches(searchQuery, MusicSearchFilter.ALBUMS)
                .stream()
                .filter(MusicSearchResult.Album.class::isInstance)
                .map(MusicSearchResult.Album.class::cast);
    }

    /** Searches artists as a lazy stream. */
    public Stream<MusicSearchResult.Artist> getArtists(String searchQuery) {
        return getResultBatches(searchQuery, MusicSearchFilter.ARTISTS)
                .stream()
                .filter(MusicSearchResult.Artist.class::isInstance)
                .map(MusicSearchResult.Artist.class::cast);
    }
}

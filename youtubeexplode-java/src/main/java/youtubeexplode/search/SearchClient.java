package youtubeexplode.search;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Stream;
import youtubeexplode.YoutubeHttp;
import youtubeexplode.bridge.SearchResponse;
import youtubeexplode.channels.ChannelId;
import youtubeexplode.common.Author;
import youtubeexplode.common.Batch;
import youtubeexplode.common.Batches;
import youtubeexplode.common.Thumbnail;
import youtubeexplode.exceptions.YoutubeExplodeException;
import youtubeexplode.playlists.PlaylistId;
import youtubeexplode.utils.Strings;
import youtubeexplode.videos.VideoClient;
import youtubeexplode.videos.VideoId;

/** Operations related to YouTube search. */
public final class SearchClient {
    private final SearchController controller;

    public SearchClient(YoutubeHttp http) {
        this.controller = new SearchController(http);
    }

    /** Enumerates search results for the specified query and filter as lazily fetched batches. */
    public Batches<SearchResult> getResultBatches(String searchQuery, SearchFilter searchFilter) {
        return Batches.paged(() -> new Supplier<Batch<SearchResult>>() {
            private final Set<String> encounteredIds = new HashSet<>();
            private String continuationToken;
            private boolean started;
            private boolean finished;

            @Override
            public Batch<SearchResult> get() {
                if (finished) return null;
                if (started && Strings.isBlank(continuationToken)) {
                    finished = true;
                    return null;
                }
                started = true;

                List<SearchResult> results = new ArrayList<>();
                SearchResponse searchResults = controller.getSearchResponse(searchQuery, searchFilter, continuationToken);

                // Video results
                if (searchFilter == SearchFilter.NONE || searchFilter == SearchFilter.VIDEO) {
                    for (SearchResponse.VideoData videoData : searchResults.videos()) {
                        if (videoData.id() == null) throw new YoutubeExplodeException("Failed to extract the video ID.");

                        // Don't yield the same result twice
                        if (!encounteredIds.add(videoData.id())) continue;

                        if (videoData.title() == null) {
                            throw new YoutubeExplodeException("Failed to extract the video title.");
                        }
                        if (videoData.author() == null) {
                            throw new YoutubeExplodeException("Failed to extract the video author.");
                        }
                        if (videoData.channelId() == null) {
                            throw new YoutubeExplodeException("Failed to extract the video channel ID.");
                        }

                        // Some videos have invalid channel IDs (e.g., just "UC"). Such videos appear to generally
                        // be unplayable anyway, so it's safe to skip them.
                        // https://github.com/Tyrrrz/YoutubeExplode/issues/944
                        ChannelId channelId = ChannelId.tryParse(videoData.channelId()).orElse(null);
                        if (channelId == null) continue;

                        VideoId videoId = VideoId.parse(videoData.id());

                        List<Thumbnail> thumbnails = new ArrayList<>(VideoClient.toThumbnails(videoData.thumbnails(), "video "));
                        thumbnails.addAll(Thumbnail.defaultSet(videoId));

                        results.add(new VideoSearchResult(
                                videoId,
                                videoData.title(),
                                new Author(channelId, videoData.author()),
                                videoData.duration(),
                                thumbnails));
                    }
                }

                // Playlist results
                if (searchFilter == SearchFilter.NONE || searchFilter == SearchFilter.PLAYLIST) {
                    for (SearchResponse.PlaylistItemData playlistData : searchResults.playlists()) {
                        if (playlistData.id() == null) {
                            throw new YoutubeExplodeException("Failed to extract the playlist ID.");
                        }

                        // Don't yield the same result twice
                        if (!encounteredIds.add(playlistData.id())) continue;

                        if (playlistData.title() == null) {
                            throw new YoutubeExplodeException("Failed to extract the playlist title.");
                        }

                        // System playlists have no author
                        Author author = !Strings.isBlank(playlistData.channelId()) && !Strings.isBlank(playlistData.author())
                                ? new Author(ChannelId.parse(playlistData.channelId()), playlistData.author())
                                : null;

                        results.add(new PlaylistSearchResult(
                                PlaylistId.parse(playlistData.id()),
                                playlistData.title(),
                                author,
                                VideoClient.toThumbnails(playlistData.thumbnails(), "playlist ")));
                    }
                }

                // Channel results
                if (searchFilter == SearchFilter.NONE || searchFilter == SearchFilter.CHANNEL) {
                    for (SearchResponse.ChannelData channelData : searchResults.channels()) {
                        if (channelData.id() == null) throw new YoutubeExplodeException("Failed to extract the channel ID.");
                        if (channelData.title() == null) {
                            throw new YoutubeExplodeException("Failed to extract the channel title.");
                        }

                        results.add(new ChannelSearchResult(
                                ChannelId.parse(channelData.id()),
                                channelData.title(),
                                VideoClient.toThumbnails(channelData.thumbnails(), "channel ")));
                    }
                }

                continuationToken = searchResults.continuationToken();
                return new Batch<>(results);
            }
        });
    }

    public Batches<SearchResult> getResultBatches(String searchQuery) {
        return getResultBatches(searchQuery, SearchFilter.NONE);
    }

    /** Enumerates search results for the specified query as a lazy stream. */
    public Stream<SearchResult> getResults(String searchQuery) {
        return getResultBatches(searchQuery).stream();
    }

    /** Enumerates video search results for the specified query as a lazy stream. */
    public Stream<VideoSearchResult> getVideos(String searchQuery) {
        return getResultBatches(searchQuery, SearchFilter.VIDEO)
                .stream()
                .filter(VideoSearchResult.class::isInstance)
                .map(VideoSearchResult.class::cast);
    }

    /** Enumerates playlist search results for the specified query as a lazy stream. */
    public Stream<PlaylistSearchResult> getPlaylists(String searchQuery) {
        return getResultBatches(searchQuery, SearchFilter.PLAYLIST)
                .stream()
                .filter(PlaylistSearchResult.class::isInstance)
                .map(PlaylistSearchResult.class::cast);
    }

    /** Enumerates channel search results for the specified query as a lazy stream. */
    public Stream<ChannelSearchResult> getChannels(String searchQuery) {
        return getResultBatches(searchQuery, SearchFilter.CHANNEL)
                .stream()
                .filter(ChannelSearchResult.class::isInstance)
                .map(ChannelSearchResult.class::cast);
    }
}

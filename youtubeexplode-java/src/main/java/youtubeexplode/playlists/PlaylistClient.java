package youtubeexplode.playlists;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Stream;
import youtubeexplode.YoutubeHttp;
import youtubeexplode.bridge.PlaylistData;
import youtubeexplode.bridge.PlaylistNextResponse;
import youtubeexplode.bridge.PlaylistVideoData;
import youtubeexplode.channels.ChannelId;
import youtubeexplode.common.Author;
import youtubeexplode.common.Batch;
import youtubeexplode.common.Batches;
import youtubeexplode.common.Thumbnail;
import youtubeexplode.exceptions.PlaylistUnavailableException;
import youtubeexplode.exceptions.YoutubeExplodeException;
import youtubeexplode.videos.VideoClient;
import youtubeexplode.videos.VideoId;

/** Operations related to YouTube playlists. */
public final class PlaylistClient {
    private final PlaylistController controller;

    public PlaylistClient(YoutubeHttp http) {
        this.controller = new PlaylistController(http);
    }

    /** Gets the metadata associated with the specified playlist. */
    public Playlist get(PlaylistId playlistId) {
        PlaylistData response = controller.getPlaylistResponse(playlistId);

        String title = response.title();
        if (title == null) throw new YoutubeExplodeException("Failed to extract the playlist title.");

        // System playlists have no author
        String channelId = response.channelId();
        String channelTitle = response.author();
        Author author = channelId != null && channelTitle != null ? new Author(ChannelId.parse(channelId), channelTitle) : null;

        // System playlists have no description
        String description = response.description() != null ? response.description() : "";

        List<Thumbnail> thumbnails = VideoClient.toThumbnails(response.thumbnails(), "");

        return new Playlist(playlistId, title, author, description, response.count(), thumbnails);
    }

    public Playlist get(String playlistIdOrUrl) {
        return get(PlaylistId.parse(playlistIdOrUrl));
    }

    /** Enumerates videos included in the specified playlist as lazily fetched batches. */
    public Batches<PlaylistVideo> getVideoBatches(PlaylistId playlistId) {
        return Batches.paged(() -> new Supplier<Batch<PlaylistVideo>>() {
            private final Set<VideoId> encounteredIds = new HashSet<>();
            private VideoId lastVideoId;
            private int lastVideoIndex;
            private String visitorData;
            private boolean finished;

            @Override
            public Batch<PlaylistVideo> get() {
                if (finished) return null;

                List<PlaylistVideo> videos = new ArrayList<>();

                try {
                    PlaylistNextResponse response =
                            controller.getPlaylistNextResponse(playlistId, lastVideoId, lastVideoIndex, visitorData);

                    for (PlaylistVideoData videoData : response.videos()) {
                        if (videoData.id() == null) throw new YoutubeExplodeException("Failed to extract the video ID.");
                        VideoId videoId = VideoId.parse(videoData.id());

                        lastVideoId = videoId;

                        if (videoData.index() == null) {
                            throw new YoutubeExplodeException("Failed to extract the video index.");
                        }
                        lastVideoIndex = videoData.index();

                        // Don't yield the same video twice
                        if (!encounteredIds.add(videoId)) continue;

                        // Videos without title are legal
                        // https://github.com/Tyrrrz/YoutubeExplode/issues/700
                        String videoTitle = videoData.title() != null ? videoData.title() : "";

                        if (videoData.author() == null) throw new YoutubeExplodeException("Failed to extract the video author.");
                        if (videoData.channelId() == null) {
                            throw new YoutubeExplodeException("Failed to extract the video channel ID.");
                        }

                        List<Thumbnail> videoThumbnails = new ArrayList<>(VideoClient.toThumbnails(videoData.thumbnails(), ""));
                        videoThumbnails.addAll(Thumbnail.defaultSet(videoId));

                        videos.add(new PlaylistVideo(
                                playlistId,
                                videoId,
                                videoTitle,
                                new Author(ChannelId.parse(videoData.channelId()), videoData.author()),
                                videoData.duration(),
                                videoThumbnails));
                    }

                    // Stop extracting if there are no new videos
                    if (videos.isEmpty()) {
                        finished = true;
                        return null;
                    }

                    if (visitorData == null) visitorData = response.visitorData();
                } catch (PlaylistUnavailableException e) {
                    // If we get playlist unavailable error, but we already extracted some videos
                    // then treat it as end of the playlist instead of a failure.
                    // https://github.com/Tyrrrz/YoutubeExplode/issues/921#issuecomment-3447937054
                    if (lastVideoIndex > 0) {
                        finished = true;
                        return null;
                    }
                    throw e;
                }

                return new Batch<>(videos);
            }
        });
    }

    public Batches<PlaylistVideo> getVideoBatches(String playlistIdOrUrl) {
        return getVideoBatches(PlaylistId.parse(playlistIdOrUrl));
    }

    /** Enumerates videos included in the specified playlist as a lazy stream. */
    public Stream<PlaylistVideo> getVideos(PlaylistId playlistId) {
        return getVideoBatches(playlistId).stream();
    }

    public Stream<PlaylistVideo> getVideos(String playlistIdOrUrl) {
        return getVideos(PlaylistId.parse(playlistIdOrUrl));
    }
}

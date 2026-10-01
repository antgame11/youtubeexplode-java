package youtubeexplode.videos;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import youtubeexplode.YoutubeHttp;
import youtubeexplode.bridge.PlayerResponse;
import youtubeexplode.bridge.ThumbnailData;
import youtubeexplode.bridge.VideoWatchPage;
import youtubeexplode.channels.ChannelId;
import youtubeexplode.common.Author;
import youtubeexplode.common.Resolution;
import youtubeexplode.common.Thumbnail;
import youtubeexplode.exceptions.YoutubeExplodeException;
import youtubeexplode.videos.closedcaptions.ClosedCaptionClient;
import youtubeexplode.videos.streams.StreamClient;

/** Operations related to YouTube videos. */
public final class VideoClient {
    private final VideoController controller;
    private final StreamClient streams;
    private final ClosedCaptionClient closedCaptions;

    public VideoClient(YoutubeHttp http) {
        this.controller = new VideoController(http);
        this.streams = new StreamClient(http);
        this.closedCaptions = new ClosedCaptionClient(http);
    }

    /** Operations related to media streams of YouTube videos. */
    public StreamClient streams() {
        return streams;
    }

    /** Operations related to closed captions of YouTube videos. */
    public ClosedCaptionClient closedCaptions() {
        return closedCaptions;
    }

    /** Converts raw thumbnail data to thumbnails, failing if any required field is missing. */
    public static List<Thumbnail> toThumbnails(List<ThumbnailData> datas, String what) {
        List<Thumbnail> result = new ArrayList<>();
        for (ThumbnailData t : datas) {
            if (t.url() == null) throw new YoutubeExplodeException("Failed to extract the " + what + "thumbnail URL.");
            if (t.width() == null) throw new YoutubeExplodeException("Failed to extract the " + what + "thumbnail width.");
            if (t.height() == null) {
                throw new YoutubeExplodeException("Failed to extract the " + what + "thumbnail height.");
            }
            result.add(new Thumbnail(t.url(), new Resolution(t.width(), t.height())));
        }
        return result;
    }

    /** Gets the metadata associated with the specified video. */
    public Video get(VideoId videoId) {
        VideoWatchPage watchPage = controller.getVideoWatchPage(videoId);

        PlayerResponse playerResponse = watchPage.playerResponse();
        if (playerResponse == null) playerResponse = controller.getPlayerResponse(videoId);

        // Videos without title are legal
        // https://github.com/Tyrrrz/YoutubeExplode/issues/700
        String title = playerResponse.title() != null ? playerResponse.title() : "";

        String channelTitle = playerResponse.author();
        if (channelTitle == null) throw new YoutubeExplodeException("Failed to extract the video author.");

        String channelId = playerResponse.channelId();
        if (channelId == null) throw new YoutubeExplodeException("Failed to extract the video channel ID.");

        OffsetDateTime uploadDate = playerResponse.uploadDate() != null ? playerResponse.uploadDate() : watchPage.uploadDate();
        if (uploadDate == null) throw new YoutubeExplodeException("Failed to extract the video upload date.");

        List<Thumbnail> thumbnails = new ArrayList<>(toThumbnails(playerResponse.thumbnails(), ""));
        thumbnails.addAll(Thumbnail.defaultSet(videoId));

        return new Video(
                videoId,
                title,
                new Author(ChannelId.parse(channelId), channelTitle),
                uploadDate,
                playerResponse.description() != null ? playerResponse.description() : "",
                playerResponse.duration(),
                thumbnails,
                playerResponse.keywords(),
                // Engagement statistics may be hidden
                new Engagement(
                        playerResponse.viewCount() != null ? playerResponse.viewCount() : 0,
                        watchPage.likeCount() != null ? watchPage.likeCount() : 0,
                        watchPage.dislikeCount() != null ? watchPage.dislikeCount() : 0));
    }

    public Video get(String videoIdOrUrl) {
        return get(VideoId.parse(videoIdOrUrl));
    }
}

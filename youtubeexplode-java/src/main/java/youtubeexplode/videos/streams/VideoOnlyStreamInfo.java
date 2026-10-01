package youtubeexplode.videos.streams;

import youtubeexplode.common.Resolution;

/** Metadata for a video-only stream. */
public final class VideoOnlyStreamInfo implements VideoStreamInfo {
    private final String url;
    private final Container container;
    private final FileSize size;
    private final Bitrate bitrate;
    private final String videoCodec;
    private final VideoQuality videoQuality;
    private final Resolution videoResolution;
    private final boolean isVideoUpscaled;

    public VideoOnlyStreamInfo(
            String url,
            Container container,
            FileSize size,
            Bitrate bitrate,
            String videoCodec,
            VideoQuality videoQuality,
            Resolution videoResolution,
            boolean isVideoUpscaled) {
        this.url = url;
        this.container = container;
        this.size = size;
        this.bitrate = bitrate;
        this.videoCodec = videoCodec;
        this.videoQuality = videoQuality;
        this.videoResolution = videoResolution;
        this.isVideoUpscaled = isVideoUpscaled;
    }

    public VideoOnlyStreamInfo(
            String url,
            Container container,
            FileSize size,
            Bitrate bitrate,
            String videoCodec,
            VideoQuality videoQuality,
            Resolution videoResolution) {
        this(url, container, size, bitrate, videoCodec, videoQuality, videoResolution, false);
    }

    @Override
    public String getUrl() {
        return url;
    }

    @Override
    public Container getContainer() {
        return container;
    }

    @Override
    public FileSize getSize() {
        return size;
    }

    @Override
    public Bitrate getBitrate() {
        return bitrate;
    }

    @Override
    public String getVideoCodec() {
        return videoCodec;
    }

    @Override
    public VideoQuality getVideoQuality() {
        return videoQuality;
    }

    @Override
    public Resolution getVideoResolution() {
        return videoResolution;
    }

    @Override
    public boolean isVideoUpscaled() {
        return isVideoUpscaled;
    }

    @Override
    public String toString() {
        return "Video-only (" + videoQuality + " | " + container + ")";
    }
}

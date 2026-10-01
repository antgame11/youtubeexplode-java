package youtubeexplode.videos.streams;

import youtubeexplode.common.Resolution;
import youtubeexplode.videos.closedcaptions.Language;

/** Metadata for a stream that contains both audio and video. */
public final class MuxedStreamInfo implements AudioStreamInfo, VideoStreamInfo {
    private final String url;
    private final Container container;
    private final FileSize size;
    private final Bitrate bitrate;
    private final String audioCodec;
    private final Language audioLanguage;
    private final Boolean isAudioLanguageDefault;
    private final String videoCodec;
    private final VideoQuality videoQuality;
    private final Resolution videoResolution;
    private final boolean isVideoUpscaled;

    public MuxedStreamInfo(
            String url,
            Container container,
            FileSize size,
            Bitrate bitrate,
            String audioCodec,
            Language audioLanguage,
            Boolean isAudioLanguageDefault,
            String videoCodec,
            VideoQuality videoQuality,
            Resolution videoResolution,
            boolean isVideoUpscaled) {
        this.url = url;
        this.container = container;
        this.size = size;
        this.bitrate = bitrate;
        this.audioCodec = audioCodec;
        this.audioLanguage = audioLanguage;
        this.isAudioLanguageDefault = isAudioLanguageDefault;
        this.videoCodec = videoCodec;
        this.videoQuality = videoQuality;
        this.videoResolution = videoResolution;
        this.isVideoUpscaled = isVideoUpscaled;
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
    public String getAudioCodec() {
        return audioCodec;
    }

    @Override
    public Language getAudioLanguage() {
        return audioLanguage;
    }

    @Override
    public Boolean isAudioLanguageDefault() {
        return isAudioLanguageDefault;
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
        return "Muxed (" + videoQuality + " | " + container + ")";
    }
}

package youtubeexplode.videos.streams;

import youtubeexplode.videos.closedcaptions.Language;

/** Metadata for an audio-only stream. */
public final class AudioOnlyStreamInfo implements AudioStreamInfo {
    private final String url;
    private final Container container;
    private final FileSize size;
    private final Bitrate bitrate;
    private final String audioCodec;
    private final Language audioLanguage;
    private final Boolean isAudioLanguageDefault;

    public AudioOnlyStreamInfo(
            String url,
            Container container,
            FileSize size,
            Bitrate bitrate,
            String audioCodec,
            Language audioLanguage,
            Boolean isAudioLanguageDefault) {
        this.url = url;
        this.container = container;
        this.size = size;
        this.bitrate = bitrate;
        this.audioCodec = audioCodec;
        this.audioLanguage = audioLanguage;
        this.isAudioLanguageDefault = isAudioLanguageDefault;
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
    public String toString() {
        return audioLanguage != null
                ? "Audio-only (" + container + " | " + audioLanguage + ")"
                : "Audio-only (" + container + ")";
    }
}

package youtubeexplode.bridge;

/** Stream description coming from either the player response or the DASH manifest. */
public interface StreamData {
    Integer itag();

    String url();

    String signature();

    String signatureParameter();

    Long contentLength();

    Long bitrate();

    String container();

    String audioCodec();

    String audioLanguageCode();

    String audioLanguageName();

    Boolean isAudioLanguageDefault();

    String videoCodec();

    String videoQualityLabel();

    Integer videoWidth();

    Integer videoHeight();

    boolean isVideoUpscaled();

    Integer videoFramerate();
}

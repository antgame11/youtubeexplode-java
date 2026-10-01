package youtubeexplode.videos.streams;

import youtubeexplode.videos.closedcaptions.Language;

/** Metadata for a stream that contains audio. */
public interface AudioStreamInfo extends StreamInfo {
    String getAudioCodec();

    /** Audio language, if the stream has multiple audio tracks. May be null. */
    Language getAudioLanguage();

    /** Whether this is the default audio track. May be null if unknown. */
    Boolean isAudioLanguageDefault();
}

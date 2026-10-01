package youtubeexplode.videos.streams;

import java.util.Collection;
import java.util.Comparator;
import java.util.NoSuchElementException;
import java.util.Optional;
import youtubeexplode.utils.Url;

/** Metadata associated with a media stream of a YouTube video. */
public interface StreamInfo {
    /** Stream URL. */
    String getUrl();

    /** Stream container. */
    Container getContainer();

    /** Stream size. */
    FileSize getSize();

    /** Stream bitrate. */
    Bitrate getBitrate();

    /** Whether YouTube throttles the download speed of this stream. */
    default boolean isThrottled() {
        return !"yes".equalsIgnoreCase(Url.tryGetQueryParameter(getUrl(), "ratebypass"));
    }

    static <T extends StreamInfo> Optional<T> tryGetWithHighestBitrate(Collection<T> streamInfos) {
        return streamInfos.stream().max(Comparator.comparing(StreamInfo::getBitrate));
    }

    static <T extends StreamInfo> T getWithHighestBitrate(Collection<T> streamInfos) {
        return tryGetWithHighestBitrate(streamInfos)
                .orElseThrow(() -> new NoSuchElementException("Input stream collection is empty."));
    }
}

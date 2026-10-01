package youtubeexplode.videos.streams;

import java.util.Collection;
import java.util.Comparator;
import java.util.NoSuchElementException;
import java.util.Optional;
import youtubeexplode.common.Resolution;

/** Metadata for a stream that contains video. */
public interface VideoStreamInfo extends StreamInfo {
    String getVideoCodec();

    VideoQuality getVideoQuality();

    Resolution getVideoResolution();

    /** Whether the video was upscaled by YouTube's Super Resolution feature. */
    boolean isVideoUpscaled();

    static <T extends VideoStreamInfo> Optional<T> tryGetWithHighestVideoQuality(Collection<T> streamInfos) {
        return streamInfos.stream().max(Comparator.comparing(VideoStreamInfo::getVideoQuality));
    }

    static <T extends VideoStreamInfo> T getWithHighestVideoQuality(Collection<T> streamInfos) {
        return tryGetWithHighestVideoQuality(streamInfos)
                .orElseThrow(() -> new NoSuchElementException("Input stream collection is empty."));
    }
}

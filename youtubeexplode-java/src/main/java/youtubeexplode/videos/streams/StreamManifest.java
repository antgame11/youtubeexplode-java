package youtubeexplode.videos.streams;

import java.util.List;

/** Manifest that lists the streams available for a video. */
public final class StreamManifest {
    private final List<StreamInfo> streams;

    public StreamManifest(List<StreamInfo> streams) {
        this.streams = List.copyOf(streams);
    }

    public List<StreamInfo> getStreams() {
        return streams;
    }

    private <T> List<T> ofType(Class<T> type) {
        return streams.stream().filter(type::isInstance).map(type::cast).toList();
    }

    /** Streams that contain audio (muxed and audio-only). */
    public List<AudioStreamInfo> getAudioStreams() {
        return ofType(AudioStreamInfo.class);
    }

    /** Streams that contain video (muxed and video-only). */
    public List<VideoStreamInfo> getVideoStreams() {
        return ofType(VideoStreamInfo.class);
    }

    public List<MuxedStreamInfo> getMuxedStreams() {
        return ofType(MuxedStreamInfo.class);
    }

    public List<AudioOnlyStreamInfo> getAudioOnlyStreams() {
        return ofType(AudioOnlyStreamInfo.class);
    }

    public List<VideoOnlyStreamInfo> getVideoOnlyStreams() {
        return ofType(VideoOnlyStreamInfo.class);
    }
}

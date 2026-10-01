package youtubeexplode.videos.streams;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import youtubeexplode.YoutubeHttp;
import youtubeexplode.utils.Url;

/**
 * Seekable input stream over a YouTube media stream. Works around YouTube's rate throttling by
 * downloading the stream in multiple ranged segments, and retries on connectivity issues.
 */
public final class MediaStream extends InputStream {
    // For most streams, YouTube limits transfer speed to match the video playback rate.
    // This helps them avoid unnecessary bandwidth, but for us it's a hindrance because
    // we want to download the stream as fast as possible.
    // To solve this, we divide the logical stream up into multiple segments and download
    // them all separately.
    private static final long THROTTLED_SEGMENT_LENGTH = 9_898_989;

    private final YoutubeHttp http;
    private final StreamInfo streamInfo;
    private final long segmentLength;

    private InputStream segmentStream;
    private long actualPosition;
    private long position;

    public MediaStream(YoutubeHttp http, StreamInfo streamInfo) {
        this.http = http;
        this.streamInfo = streamInfo;
        this.segmentLength = streamInfo.isThrottled() ? THROTTLED_SEGMENT_LENGTH : streamInfo.getSize().getBytes();
    }

    public static String getSegmentUrl(String streamUrl, long from, long to) {
        return Url.setQueryParameter(streamUrl, "range", from + "-" + to);
    }

    /** Total length of the stream in bytes. */
    public long length() {
        return streamInfo.getSize().getBytes();
    }

    /** Current position in the stream. */
    public long position() {
        return position;
    }

    /** Moves the read position to an absolute offset. */
    public void seek(long newPosition) {
        if (newPosition < 0) throw new IllegalArgumentException("Position cannot be negative.");
        position = newPosition;
    }

    private void resetSegment() {
        if (segmentStream != null) {
            try {
                segmentStream.close();
            } catch (IOException ignored) {
            }
            segmentStream = null;
        }
    }

    private InputStream resolveSegment() {
        if (segmentStream != null) return segmentStream;

        String url = getSegmentUrl(streamInfo.getUrl(), position, position + segmentLength - 1);
        segmentStream = http.stream(YoutubeHttp.Request.get(url));
        return segmentStream;
    }

    /** Opens the first segment eagerly so that connection errors surface early. */
    public void initialize() {
        resolveSegment();
    }

    private int readSegment(byte[] buffer, int offset, int count) throws IOException {
        for (int retriesRemaining = 5; ; retriesRemaining--) {
            try {
                return resolveSegment().read(buffer, offset, count);
            } catch (UncheckedIOException e) {
                if (!YoutubeHttp.isRetryable(e) || retriesRemaining <= 0) throw e;
                resetSegment();
            } catch (IOException e) {
                if (retriesRemaining <= 0 || e instanceof java.io.InterruptedIOException) throw e;
                resetSegment();
            }
        }
    }

    @Override
    public int read(byte[] buffer, int offset, int count) throws IOException {
        if (count == 0) return 0;

        while (true) {
            long requestedPosition = position;

            // If the consumer changed position since the last read, reset the segment
            // to get the correct data.
            if (actualPosition != requestedPosition) resetSegment();

            // Exit if we reached the end of the stream
            if (requestedPosition >= length()) return -1;

            int bytesRead = readSegment(buffer, offset, count);
            if (bytesRead > 0) {
                position = actualPosition = requestedPosition + bytesRead;
                return bytesRead;
            }

            // Reached the end of the segment, load the next one and loop around
            actualPosition = requestedPosition;
            resetSegment();
        }
    }

    @Override
    public int read() throws IOException {
        byte[] one = new byte[1];
        int n = read(one, 0, 1);
        return n < 0 ? -1 : one[0] & 0xFF;
    }

    @Override
    public long skip(long n) {
        long target = Math.min(Math.max(position + n, 0), length());
        long skipped = target - position;
        position = target;
        return skipped;
    }

    @Override
    public int available() {
        return (int) Math.min(length() - position, Integer.MAX_VALUE);
    }

    @Override
    public void close() {
        resetSegment();
    }
}

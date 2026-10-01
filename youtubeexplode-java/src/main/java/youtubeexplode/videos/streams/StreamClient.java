package youtubeexplode.videos.streams;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;
import java.util.function.DoubleConsumer;
import youtubeexplode.YoutubeHttp;
import youtubeexplode.bridge.DashManifest;
import youtubeexplode.bridge.PlayerResponse;
import youtubeexplode.bridge.StreamData;
import youtubeexplode.bridge.cipher.CipherManifest;
import youtubeexplode.common.Resolution;
import youtubeexplode.exceptions.HttpStatusException;
import youtubeexplode.exceptions.VideoRequiresPurchaseException;
import youtubeexplode.exceptions.VideoUnavailableException;
import youtubeexplode.exceptions.VideoUnplayableException;
import youtubeexplode.exceptions.YoutubeExplodeException;
import youtubeexplode.utils.Strings;
import youtubeexplode.utils.Url;
import youtubeexplode.videos.VideoId;
import youtubeexplode.videos.closedcaptions.Language;

/** Operations related to media streams of YouTube videos. */
public final class StreamClient {
    private final YoutubeHttp http;
    private final StreamController controller;

    // Because we determine the player version ourselves, it's safe to cache the cipher manifest
    // for the entire lifetime of the client.
    private CipherManifest cipherManifest;

    public StreamClient(YoutubeHttp http) {
        this.http = http;
        this.controller = new StreamController(http);
    }

    private synchronized CipherManifest resolveCipherManifest() {
        if (cipherManifest != null) return cipherManifest;

        CipherManifest manifest = controller.getPlayerSource().cipherManifest();
        if (manifest == null) throw new YoutubeExplodeException("Failed to extract the cipher manifest: YouTube's player script has changed and this "
                    + "stream needs it decoded. Streams that do not need it (most videos) still work.");

        return cipherManifest = manifest;
    }

    /** Returns the verified content length, or empty if the stream is not available. */
    private OptionalLong tryGetContentLength(StreamData streamData, String url) {
        Long contentLength = streamData.contentLength();

        // If content length is not available in the metadata, get it by
        // sending a HEAD request and parsing the Content-Length header.
        if (contentLength == null) {
            HttpResponse<Void> response = http.discard(YoutubeHttp.Request.head(url));

            // 404 error indicates that the stream is not available
            if (response.statusCode() == 404) return OptionalLong.empty();
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new HttpStatusException(response.statusCode(), url);
            }

            OptionalLong header = response.headers().firstValueAsLong("Content-Length");
            if (header.isPresent()) contentLength = header.getAsLong();
        }

        if (contentLength != null) {
            // Streams may have mismatched content length, so ensure that the obtained value is correct
            // Try to access the last byte of the stream
            HttpResponse<Void> response = http.discard(YoutubeHttp.Request.get(
                    MediaStream.getSegmentUrl(url, contentLength - 2, contentLength - 1)));

            // 404 error indicates that the stream has mismatched content length or is not available
            if (response.statusCode() == 404) return OptionalLong.empty();
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new HttpStatusException(response.statusCode(), url);
            }
        }

        return contentLength != null ? OptionalLong.of(contentLength) : OptionalLong.empty();
    }

    private List<StreamInfo> getStreamInfos(List<StreamData> streamDatas) {
        List<StreamInfo> result = new ArrayList<>();

        for (StreamData streamData : streamDatas) {
            // SABR / server-side streams have no progressive URL we can download.
            // ANDROID in particular mixes one muxed itag-18 URL with SABR-only adaptive
            // formats, so skip those instead of failing the whole manifest.
            String url = streamData.url();
            if (Strings.isBlank(url)) continue;

            Integer itag = streamData.itag();
            if (itag == null) throw new YoutubeExplodeException("Failed to extract the stream itag.");

            // Handle cipher-protected streams
            if (!Strings.isBlank(streamData.signature())) {
                CipherManifest manifest = resolveCipherManifest();
                String parameter = streamData.signatureParameter() != null ? streamData.signatureParameter() : "sig";
                url = Url.setQueryParameter(url, parameter, manifest.decipher(streamData.signature()));
            }

            OptionalLong contentLength = tryGetContentLength(streamData, url);
            if (contentLength.isEmpty()) continue;

            if (streamData.container() == null) throw new YoutubeExplodeException("Failed to extract the stream container.");
            Container container = new Container(streamData.container());

            if (streamData.bitrate() == null) throw new YoutubeExplodeException("Failed to extract the stream bitrate.");
            Bitrate bitrate = new Bitrate(streamData.bitrate());

            FileSize size = new FileSize(contentLength.getAsLong());

            Language audioLanguage = !Strings.isBlank(streamData.audioLanguageCode())
                    ? new Language(
                            streamData.audioLanguageCode(),
                            streamData.audioLanguageName() != null
                                    ? streamData.audioLanguageName()
                                    : streamData.audioLanguageCode())
                    : null;

            // Muxed or video-only stream
            if (!Strings.isBlank(streamData.videoCodec())) {
                int framerate = streamData.videoFramerate() != null ? streamData.videoFramerate() : 24;

                VideoQuality videoQuality = !Strings.isBlank(streamData.videoQualityLabel())
                        ? VideoQuality.fromLabel(streamData.videoQualityLabel(), framerate)
                        : VideoQuality.fromItag(itag, framerate);

                Resolution videoResolution = streamData.videoWidth() != null && streamData.videoHeight() != null
                        ? new Resolution(streamData.videoWidth(), streamData.videoHeight())
                        : videoQuality.getDefaultVideoResolution();

                if (!Strings.isBlank(streamData.audioCodec())) {
                    // Muxed
                    result.add(new MuxedStreamInfo(
                            url,
                            container,
                            size,
                            bitrate,
                            streamData.audioCodec(),
                            audioLanguage,
                            streamData.isAudioLanguageDefault(),
                            streamData.videoCodec(),
                            videoQuality,
                            videoResolution,
                            streamData.isVideoUpscaled()));
                } else {
                    // Video-only
                    result.add(new VideoOnlyStreamInfo(
                            url,
                            container,
                            size,
                            bitrate,
                            streamData.videoCodec(),
                            videoQuality,
                            videoResolution,
                            streamData.isVideoUpscaled()));
                }
            }
            // Audio-only
            else if (!Strings.isBlank(streamData.audioCodec())) {
                result.add(new AudioOnlyStreamInfo(
                        url,
                        container,
                        size,
                        bitrate,
                        streamData.audioCodec(),
                        audioLanguage,
                        streamData.isAudioLanguageDefault()));
            } else {
                throw new YoutubeExplodeException("Failed to extract the stream codec.");
            }
        }

        return result;
    }

    private List<StreamInfo> getStreamInfos(VideoId videoId, PlayerResponse playerResponse) {
        // Video is pay-to-play
        if (!Strings.isBlank(playerResponse.previewVideoId())) {
            throw new VideoRequiresPurchaseException(
                    "Video '" + videoId + "' requires purchase and cannot be played.",
                    VideoId.parse(playerResponse.previewVideoId()));
        }

        // Video is unplayable
        if (!playerResponse.isPlayable()) {
            throw new VideoUnplayableException(
                    "Video '" + videoId + "' is unplayable. Reason: '" + playerResponse.playabilityError() + "'.");
        }

        // Extract streams from the player response
        List<StreamInfo> streamInfos = new ArrayList<>(getStreamInfos(playerResponse.streams()));

        // Extract streams from the DASH manifest
        if (!Strings.isBlank(playerResponse.dashManifestUrl())) {
            try {
                DashManifest dashManifest = controller.getDashManifest(playerResponse.dashManifestUrl());
                streamInfos.addAll(getStreamInfos(dashManifest.streams()));
            } catch (HttpStatusException ignored) {
                // Some DASH manifest URLs return 404 for whatever reason
                // https://github.com/Tyrrrz/YoutubeExplode/issues/728
            }
        }

        // Error if no streams were found
        if (streamInfos.isEmpty()) {
            throw new VideoUnplayableException("Video '" + videoId + "' does not contain any playable streams.");
        }

        return streamInfos;
    }

    private List<StreamInfo> getStreamInfos(VideoId videoId) {
        try {
            // Try to get player response from a cipher-less client
            PlayerResponse playerResponse = controller.getPlayerResponse(videoId);
            return getStreamInfos(videoId, playerResponse);
        }
        // Only retry on videos that are unplayable for reasons other than being unavailable (deleted, private, etc)
        catch (VideoUnavailableException ex) {
            throw ex;
        }
        // Retry with deciphering
        catch (VideoUnplayableException ex) {
            CipherManifest manifest = resolveCipherManifest();

            // Try to get player response from a client with cipher
            PlayerResponse playerResponse = controller.getPlayerResponse(videoId, manifest.signatureTimestamp());
            return getStreamInfos(videoId, playerResponse);
        }
    }

    /** Gets the manifest that lists available streams for the specified video. */
    public StreamManifest getManifest(VideoId videoId) {
        for (int retriesRemaining = 5; ; retriesRemaining--) {
            try {
                return new StreamManifest(getStreamInfos(videoId));
            }
            // Retry on connectivity issues
            catch (UncheckedIOException e) {
                if (!YoutubeHttp.isRetryable(e) || retriesRemaining <= 0) throw e;
            }
        }
    }

    public StreamManifest getManifest(String videoIdOrUrl) {
        return getManifest(VideoId.parse(videoIdOrUrl));
    }

    /** Gets the HTTP Live Stream (HLS) manifest URL for the specified video (if it is a livestream). */
    public String getHttpLiveStreamUrl(VideoId videoId) {
        PlayerResponse playerResponse = controller.getPlayerResponse(videoId);
        if (!playerResponse.isPlayable()) {
            throw new VideoUnplayableException(
                    "Video '" + videoId + "' is unplayable. Reason: '" + playerResponse.playabilityError() + "'.");
        }

        if (Strings.isBlank(playerResponse.hlsManifestUrl())) {
            throw new YoutubeExplodeException("Failed to extract the HTTP Live Stream manifest URL. Video '"
                    + videoId + "' is likely not a live stream.");
        }

        return playerResponse.hlsManifestUrl();
    }

    public String getHttpLiveStreamUrl(String videoIdOrUrl) {
        return getHttpLiveStreamUrl(VideoId.parse(videoIdOrUrl));
    }

    /** Gets a seekable input stream for the stream identified by the specified metadata. Close it when done. */
    public MediaStream get(StreamInfo streamInfo) {
        MediaStream stream = new MediaStream(http, streamInfo);
        try {
            stream.initialize();
        } catch (RuntimeException e) {
            stream.close();
            throw e;
        }
        return stream;
    }

    /** Copies the stream to the destination, optionally reporting progress in the range [0, 1]. */
    public void copyTo(StreamInfo streamInfo, OutputStream destination, DoubleConsumer progress) throws IOException {
        try (InputStream input = get(streamInfo)) {
            byte[] buffer = new byte[81920];
            long total = streamInfo.getSize().getBytes();
            long copied = 0;

            int read;
            while ((read = input.read(buffer)) > 0) {
                if (Thread.currentThread().isInterrupted()) {
                    throw new java.io.InterruptedIOException("Copy was interrupted.");
                }
                destination.write(buffer, 0, read);
                copied += read;
                if (progress != null && total > 0) progress.accept((double) copied / total);
            }
        }
    }

    public void copyTo(StreamInfo streamInfo, OutputStream destination) throws IOException {
        copyTo(streamInfo, destination, null);
    }

    /** Downloads the stream to the specified file, optionally reporting progress in the range [0, 1]. */
    public void download(StreamInfo streamInfo, Path filePath, DoubleConsumer progress) throws IOException {
        try (OutputStream destination = Files.newOutputStream(filePath)) {
            copyTo(streamInfo, destination, progress);
        }
    }

    public void download(StreamInfo streamInfo, Path filePath) throws IOException {
        download(streamInfo, filePath, null);
    }
}

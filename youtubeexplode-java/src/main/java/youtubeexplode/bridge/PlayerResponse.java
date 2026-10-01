package youtubeexplode.bridge;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import youtubeexplode.utils.Dates;
import youtubeexplode.utils.Json;
import youtubeexplode.utils.Protobuf;
import youtubeexplode.utils.Strings;
import youtubeexplode.utils.Url;

public final class PlayerResponse {
    private static final Pattern PREVIEW_VIDEO_ID = Pattern.compile("video_id=(.{11})");

    private final JsonNode content;

    public PlayerResponse(JsonNode content) {
        this.content = content;
    }

    public static PlayerResponse parse(String raw) {
        return new PlayerResponse(Json.parse(raw));
    }

    private JsonNode playability() {
        return Json.at(content, "playabilityStatus");
    }

    private String playabilityStatus() {
        return Json.str(playability(), "status");
    }

    private JsonNode details() {
        return Json.at(content, "videoDetails");
    }

    /** YouTube's own status, e.g. "OK", "ERROR", "LOGIN_REQUIRED". May be null. */
    public String playabilityStatusText() {
        return playabilityStatus();
    }

    public String playabilityError() {
        return Json.str(playability(), "reason");
    }

    public boolean isAvailable() {
        return !"error".equalsIgnoreCase(playabilityStatus()) && details() != null;
    }

    public boolean isPlayable() {
        return "ok".equalsIgnoreCase(playabilityStatus());
    }

    public String title() {
        return Json.str(details(), "title");
    }

    public String channelId() {
        return Json.str(details(), "channelId");
    }

    public String author() {
        return Json.str(details(), "author");
    }

    public OffsetDateTime uploadDate() {
        return Dates.tryParseOffsetDateTime(
                Json.str(content, "microformat", "playerMicroformatRenderer", "uploadDate"));
    }

    public Duration duration() {
        Double seconds = Strings.parseDouble(Json.str(details(), "lengthSeconds"));
        return seconds != null ? Dates.ofSecondsDouble(seconds) : null;
    }

    public List<ThumbnailData> thumbnails() {
        List<ThumbnailData> result = new ArrayList<>();
        for (JsonNode j : Json.arrayOrEmpty(details(), "thumbnail", "thumbnails")) result.add(new ThumbnailData(j));
        return result;
    }

    public List<String> keywords() {
        List<String> result = new ArrayList<>();
        for (JsonNode j : Json.arrayOrEmpty(details(), "keywords")) {
            if (j.isTextual()) result.add(j.asText());
        }
        return result;
    }

    public String description() {
        return Json.str(details(), "shortDescription");
    }

    public Long viewCount() {
        return Strings.parseLong(Json.str(details(), "viewCount"));
    }

    public String previewVideoId() {
        JsonNode errorScreen = Json.at(playability(), "errorScreen");

        String id = Json.str(errorScreen, "playerLegacyDesktopYpcTrailerRenderer", "trailerVideoId");
        if (id != null) return id;

        String playerVars = Json.str(errorScreen, "ypcTrailerRenderer", "playerVars");
        if (playerVars != null) {
            String fromVars = Url.getQueryParameters(playerVars).get("video_id");
            if (fromVars != null) return fromVars;
        }

        String playerResponse = Json.str(errorScreen, "ypcTrailerRenderer", "playerResponse");
        if (playerResponse != null) {
            // YouTube uses weird base64-like encoding here that I don't know how to deal with.
            // It's supposed to have JSON inside, but if extracted as is, it contains garbage.
            // Luckily, some of the text gets decoded correctly, which is enough for us to
            // extract the preview video ID using regex.
            try {
                String decoded = new String(
                        Base64.getDecoder().decode(playerResponse.replace('-', '+').replace('_', '/')),
                        StandardCharsets.UTF_8);
                Matcher m = PREVIEW_VIDEO_ID.matcher(decoded);
                if (m.find()) return Strings.nullIfBlank(m.group(1));
            } catch (IllegalArgumentException ignored) {
            }
        }

        return null;
    }

    private JsonNode streamingData() {
        return Json.at(content, "streamingData");
    }

    public String dashManifestUrl() {
        return Json.str(streamingData(), "dashManifestUrl");
    }

    public String hlsManifestUrl() {
        return Json.str(streamingData(), "hlsManifestUrl");
    }

    public List<StreamData> streams() {
        List<StreamData> result = new ArrayList<>();
        for (JsonNode j : Json.arrayOrEmpty(streamingData(), "formats")) result.add(new Stream(j));
        for (JsonNode j : Json.arrayOrEmpty(streamingData(), "adaptiveFormats")) result.add(new Stream(j));
        return result;
    }

    public List<ClosedCaptionTrackData> closedCaptionTracks() {
        List<ClosedCaptionTrackData> result = new ArrayList<>();
        for (JsonNode j : Json.arrayOrEmpty(
                content, "captions", "playerCaptionsTracklistRenderer", "captionTracks")) {
            result.add(new ClosedCaptionTrackData(j));
        }
        return result;
    }

    public static final class ClosedCaptionTrackData {
        private final JsonNode content;

        public ClosedCaptionTrackData(JsonNode content) {
            this.content = content;
        }

        public String url() {
            return Json.str(content, "baseUrl");
        }

        public String languageCode() {
            return Json.str(content, "languageCode");
        }

        public String languageName() {
            String simple = Json.str(content, "name", "simpleText");
            return simple != null ? simple : Json.runsText(content, "name", "runs");
        }

        public boolean isAutoGenerated() {
            String vssId = Json.str(content, "vssId");
            return vssId != null && vssId.regionMatches(true, 0, "a.", 0, 2);
        }
    }

    private static final class Stream implements StreamData {
        private final JsonNode content;
        private final Map<String, String> cipherData;

        Stream(JsonNode content) {
            this.content = content;
            String cipher = Json.str(content, "cipher");
            if (cipher == null) cipher = Json.str(content, "signatureCipher");
            this.cipherData = cipher != null ? Url.getQueryParameters(cipher) : null;
        }

        private String mimeType() {
            return Json.str(content, "mimeType");
        }

        private boolean isAudioOnly() {
            String mime = mimeType();
            return mime != null && mime.regionMatches(true, 0, "audio/", 0, 6);
        }

        private String codecs() {
            String mime = mimeType();
            if (mime == null) return null;
            return Strings.until(Strings.after(mime, "codecs=\""), "\"");
        }

        @Override
        public Integer itag() {
            return Json.integer(content, "itag");
        }

        @Override
        public String url() {
            String url = Json.str(content, "url");
            return url != null ? url : cipherData != null ? cipherData.get("url") : null;
        }

        @Override
        public String signature() {
            return cipherData != null ? cipherData.get("s") : null;
        }

        @Override
        public String signatureParameter() {
            return cipherData != null ? cipherData.get("sp") : null;
        }

        @Override
        public Long contentLength() {
            Long fromJson = Strings.parseLong(Json.str(content, "contentLength"));
            if (fromJson != null) return fromJson;

            String url = url();
            if (url == null) return null;
            String clen = Url.tryGetQueryParameter(url, "clen");
            return Strings.parseLong(Strings.nullIfBlank(clen));
        }

        @Override
        public Long bitrate() {
            return Json.lng(content, "bitrate");
        }

        @Override
        public String container() {
            String mime = mimeType();
            return mime == null ? null : Strings.after(Strings.until(mime, ";"), "/");
        }

        @Override
        public String audioCodec() {
            String codecs = codecs();
            if (codecs == null) return null;
            return isAudioOnly() ? codecs : Strings.nullIfBlank(Strings.after(codecs, ", "));
        }

        @Override
        public String audioLanguageCode() {
            String id = Json.str(content, "audioTrack", "id");
            return id == null ? null : Strings.until(id, ".");
        }

        @Override
        public String audioLanguageName() {
            return Json.str(content, "audioTrack", "displayName");
        }

        @Override
        public Boolean isAudioLanguageDefault() {
            return Json.bool(content, "audioTrack", "audioIsDefault");
        }

        @Override
        public String videoCodec() {
            String codecs = codecs();
            String codec = isAudioOnly() || codecs == null ? null : Strings.nullIfBlank(Strings.until(codecs, ", "));

            // "unknown" value indicates av01 codec
            if ("unknown".equalsIgnoreCase(codec)) return "av01.0.05M.08";
            return codec;
        }

        @Override
        public String videoQualityLabel() {
            return Json.str(content, "qualityLabel");
        }

        @Override
        public Integer videoWidth() {
            return Json.integer(content, "width");
        }

        @Override
        public Integer videoHeight() {
            return Json.integer(content, "height");
        }

        @Override
        public boolean isVideoUpscaled() {
            // xtags is a base64-encoded protobuf map<string, string>.
            // Streams upscaled with YouTube's Super Resolution feature carry the entry {"sr": "1"}.
            String xtags = Strings.nullIfBlank(Json.str(content, "xtags"));
            if (xtags == null) return false;
            Map<String, String> map = Protobuf.tryDeserializeMap(xtags);
            return map != null && "1".equals(map.get("sr"));
        }

        @Override
        public Integer videoFramerate() {
            return Json.integer(content, "fps");
        }
    }
}

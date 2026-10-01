package youtubeexplode.bridge;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.w3c.dom.Element;
import youtubeexplode.utils.Strings;
import youtubeexplode.utils.Url;
import youtubeexplode.utils.Xml;

public final class DashManifest {
    private static final Pattern CLEN = Pattern.compile("[/\\?]clen[/=](\\d+)");
    private static final Pattern MIME = Pattern.compile("mime[/=]\\w*%2F([\\w\\d]*)");

    private final Element content;

    public DashManifest(Element content) {
        this.content = content;
    }

    public static DashManifest parse(String raw) {
        return new DashManifest(Xml.parse(raw));
    }

    public List<StreamData> streams() {
        List<StreamData> result = new ArrayList<>();
        for (Element x : Xml.descendants(content, "Representation")) {
            // Skip non-media representations (like "rawcc")
            String id = Xml.attr(x, "id");
            if (id == null || !id.chars().allMatch(Character::isDigit)) continue;

            // Skip segmented streams
            List<Element> inits = Xml.descendants(x, "Initialization");
            if (!inits.isEmpty()) {
                String source = Xml.attr(inits.get(0), "sourceURL");
                if (source != null && source.contains("sq/")) continue;
            }

            // Skip streams without codecs
            if (Strings.isBlank(Xml.attr(x, "codecs"))) continue;

            result.add(new Stream(x));
        }
        return result;
    }

    private static final class Stream implements StreamData {
        private final Element content;

        Stream(Element content) {
            this.content = content;
        }

        private boolean isAudioOnly() {
            return Xml.child(content, "AudioChannelConfiguration") != null;
        }

        @Override
        public Integer itag() {
            return Strings.parseInt(Xml.attr(content, "id"));
        }

        @Override
        public String url() {
            Element base = Xml.child(content, "BaseURL");
            return base != null ? Xml.text(base) : null;
        }

        @Override
        public String signature() {
            return null;
        }

        @Override
        public String signatureParameter() {
            return null;
        }

        @Override
        public Long contentLength() {
            Long fromAttr = Strings.parseLong(Xml.attr(content, "contentLength"));
            if (fromAttr != null) return fromAttr;

            String url = url();
            if (url == null) return null;
            Matcher m = CLEN.matcher(url);
            return m.find() ? Strings.parseLong(m.group(1)) : null;
        }

        @Override
        public Long bitrate() {
            return Strings.parseLong(Xml.attr(content, "bandwidth"));
        }

        @Override
        public String container() {
            String url = url();
            if (url == null) return null;
            Matcher m = MIME.matcher(url);
            return m.find() ? Url.decode(m.group(1)) : "";
        }

        @Override
        public String audioCodec() {
            return isAudioOnly() ? Xml.attr(content, "codecs") : null;
        }

        @Override
        public String audioLanguageCode() {
            return null;
        }

        @Override
        public String audioLanguageName() {
            return null;
        }

        @Override
        public Boolean isAudioLanguageDefault() {
            return null;
        }

        @Override
        public String videoCodec() {
            return isAudioOnly() ? null : Xml.attr(content, "codecs");
        }

        @Override
        public String videoQualityLabel() {
            return null;
        }

        @Override
        public Integer videoWidth() {
            return Strings.parseInt(Xml.attr(content, "width"));
        }

        @Override
        public Integer videoHeight() {
            return Strings.parseInt(Xml.attr(content, "height"));
        }

        @Override
        public boolean isVideoUpscaled() {
            return false;
        }

        @Override
        public Integer videoFramerate() {
            return Strings.parseInt(Xml.attr(content, "frameRate"));
        }
    }
}

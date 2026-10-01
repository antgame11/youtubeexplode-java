package youtubeexplode.bridge;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.w3c.dom.Element;
import youtubeexplode.utils.Dates;
import youtubeexplode.utils.Strings;
import youtubeexplode.utils.Xml;

public final class ClosedCaptionTrackResponse {
    private final Element content;

    public ClosedCaptionTrackResponse(Element content) {
        this.content = content;
    }

    public static ClosedCaptionTrackResponse parse(String raw) {
        return new ClosedCaptionTrackResponse(Xml.parse(raw));
    }

    public List<CaptionData> captions() {
        List<CaptionData> result = new ArrayList<>();
        for (Element p : Xml.descendants(content, "p")) result.add(new CaptionData(p));
        return result;
    }

    private static Duration millis(Element e, String attr) {
        Double ms = Strings.parseDouble(Xml.attr(e, attr));
        return ms != null ? Dates.ofMillisDouble(ms) : null;
    }

    public static final class CaptionData {
        private final Element content;

        CaptionData(Element content) {
            this.content = content;
        }

        public String text() {
            return Xml.text(content);
        }

        public Duration offset() {
            return millis(content, "t");
        }

        public Duration duration() {
            return millis(content, "d");
        }

        public List<PartData> parts() {
            List<PartData> result = new ArrayList<>();
            for (Element s : Xml.children(content, "s")) result.add(new PartData(s));
            return result;
        }
    }

    public static final class PartData {
        private final Element content;

        PartData(Element content) {
            this.content = content;
        }

        public String text() {
            return Xml.text(content);
        }

        public Duration offset() {
            Duration t = millis(content, "t");
            if (t != null) return t;
            Duration ac = millis(content, "ac");
            return ac != null ? ac : Duration.ZERO;
        }
    }
}

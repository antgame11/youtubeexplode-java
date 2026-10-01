package youtubeexplode.bridge;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import youtubeexplode.utils.Strings;

public final class ChannelPage {
    private final Document content;

    private ChannelPage(Document content) {
        this.content = content;
    }

    /** Returns null if the page doesn't look like a channel page. */
    public static ChannelPage tryParse(String raw) {
        Document content = Jsoup.parse(raw);
        if (content.selectFirst("meta[property=og:url]") == null) return null;
        return new ChannelPage(content);
    }

    private String meta(String property) {
        Element e = content.selectFirst("meta[property=" + property + "]");
        return e != null ? e.attr("content") : null;
    }

    public String url() {
        return meta("og:url");
    }

    public String id() {
        String url = url();
        if (url == null) return null;
        int i = url.toLowerCase().indexOf("channel/");
        return i < 0 ? "" : url.substring(i + "channel/".length());
    }

    public String title() {
        return meta("og:title");
    }

    public String logoUrl() {
        return meta("og:image");
    }
}

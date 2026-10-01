package nowplaying.web;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import nowplaying.NowPlaying;

class NowPlayingSvgTest {
    private static Document parse(String svg) throws Exception {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(true);
        return f.newDocumentBuilder().parse(new ByteArrayInputStream(svg.getBytes(StandardCharsets.UTF_8)));
    }

    private static NowPlaying track(String title, String artist, long durationMs, boolean playing) {
        return new NowPlaying("id", title, List.of(artist), "Album", durationMs, playing);
    }

    @Test
    void playingCardIsWellFormedAndAnimated() throws Exception {
        String svg = NowPlayingSvg.render(track("Runaway", "Kanye West", 548_000, true), 65_500, null, NowPlayingSvg.Theme.AUTO);
        Document doc = parse(svg);
        assertEquals("svg", doc.getDocumentElement().getLocalName());
        assertTrue(svg.contains("NOW PLAYING"));
        assertTrue(svg.contains("9:08")); // total

        // Bar: starts at 65.5/548 of 312px and fills for the remaining 482.5s
        NodeList animates = doc.getElementsByTagNameNS("*", "animate");
        boolean foundBar = false;
        for (int i = 0; i < animates.getLength(); i++) {
            var a = animates.item(i);
            if ("width".equals(a.getAttributes().getNamedItem("attributeName").getNodeValue())) {
                foundBar = true;
                assertEquals("312", a.getAttributes().getNamedItem("to").getNodeValue());
                assertEquals("482.5s", a.getAttributes().getNamedItem("dur").getNodeValue());
                double from = Double.parseDouble(a.getAttributes().getNamedItem("from").getNodeValue());
                assertEquals(312 * 65.5 / 548, from, 0.01);
            }
        }
        assertTrue(foundBar, "progress bar animation");
        assertTrue(doc.getElementsByTagNameNS("*", "animateTransform").getLength() >= 3, "digit rollers");
    }

    @Test
    void rollerKeyframesTickEverySecond() throws Exception {
        // 0:58.5 elapsed in a 3 minute track: the seconds-ones digit must change at t=0.5s, 1.5s, ...
        String svg = NowPlayingSvg.render(track("T", "A", 180_000, true), 58_500, null, NowPlayingSvg.Theme.DARK);
        Document doc = parse(svg);
        NodeList rolls = doc.getElementsByTagNameNS("*", "animateTransform");

        // Columns are written in order: minutes-ones, seconds-tens, seconds-ones (track < 10 minutes)
        var onesCol = rolls.item(2);
        String[] times = onesCol.getAttributes().getNamedItem("keyTimes").getNodeValue().split(";");
        String[] values = onesCol.getAttributes().getNamedItem("values").getNodeValue().split(";");
        double dur = Double.parseDouble(onesCol.getAttributes().getNamedItem("dur").getNodeValue().replace("s", ""));
        assertEquals(121.5, dur, 0.001);
        assertEquals(times.length, values.length);
        assertEquals("0 -112", values[0]);                     // 8 at 0:58 (row height 14 -> 112)
        assertEquals(0.5, Double.parseDouble(times[1]) * dur, 0.01); // 9 at 0:59
        assertEquals("0 -126", values[1]);
        assertEquals(1.5, Double.parseDouble(times[2]) * dur, 0.01); // 0 at 1:00
        assertEquals("0 0", values[2]);

        var tensCol = rolls.item(1);
        String[] tensValues = tensCol.getAttributes().getNamedItem("values").getNodeValue().split(";");
        assertEquals("0 -70", tensValues[0]);  // 5 at 0:58
        assertEquals("0 0", tensValues[1]);    // 0 at 1:00
        assertEquals("0 -14", tensValues[2]);  // 1 at 1:10

        var minutesCol = rolls.item(0);
        String[] minValues = minutesCol.getAttributes().getNamedItem("values").getNodeValue().split(";");
        assertEquals("0 0", minValues[0]);     // minute 0
        assertEquals("0 -14", minValues[1]);   // minute 1 at t = 1.5s
        assertEquals("0 -28", minValues[2]);   // minute 2 after another 60s
    }

    @Test
    void digitMath() {
        assertEquals(0, NowPlayingSvg.digitAt(3, 60));
        assertEquals(1, NowPlayingSvg.digitAt(1, 65));
        assertEquals(0, NowPlayingSvg.digitAt(2, 65));
        assertEquals(2, NowPlayingSvg.digitAt(0, 25 * 60));
        assertEquals(5, NowPlayingSvg.digitAt(1, 25 * 60));
        assertEquals("3:05", NowPlayingSvg.formatClock(185));
    }

    @Test
    void pausedCardDoesNotAnimate() throws Exception {
        String svg = NowPlayingSvg.render(track("T", "A", 200_000, false), 100_000, null, NowPlayingSvg.Theme.AUTO);
        parse(svg);
        assertTrue(svg.contains("PAUSED"));
        assertFalse(svg.contains("calcMode"));
        assertFalse(svg.contains("attributeName=\"width\""));
        assertFalse(svg.contains("repeatCount")); // equalizer is still
    }

    @Test
    void idleCard() throws Exception {
        String svg = NowPlayingSvg.render(null, 0, null, NowPlayingSvg.Theme.AUTO);
        parse(svg);
        assertTrue(svg.contains("Not playing right now"));
        assertFalse(svg.contains("<animate"));
    }

    @Test
    void textIsEscapedAndTruncated() throws Exception {
        String evil = "<script>alert(1)</script> & \"quotes\" \u0007 " + "x".repeat(80);
        String svg = NowPlayingSvg.render(track(evil, "A & B", 100_000, true), 0, null, NowPlayingSvg.Theme.AUTO);
        parse(svg); // well-formed despite hostile input
        assertFalse(svg.contains("<script>"));
        assertTrue(svg.contains("&lt;script&gt;"));
        assertTrue(svg.contains("A &amp; B"));
        assertTrue(svg.contains("…"));
    }

    @Test
    void themes() {
        assertEquals(NowPlayingSvg.Theme.DARK, NowPlayingSvg.Theme.parse("DARK"));
        assertEquals(NowPlayingSvg.Theme.AUTO, NowPlayingSvg.Theme.parse("nonsense"));
        assertTrue(NowPlayingSvg.render(null, 0, null, NowPlayingSvg.Theme.AUTO).contains("prefers-color-scheme"));
        assertFalse(NowPlayingSvg.render(null, 0, null, NowPlayingSvg.Theme.DARK).contains("prefers-color-scheme"));
    }

    @Test
    void coverIsInlined() throws Exception {
        String svg = NowPlayingSvg.render(track("T", "A", 100_000, true), 0, "data:image/jpeg;base64,AAAA", NowPlayingSvg.Theme.AUTO);
        parse(svg);
        assertTrue(svg.contains("href=\"data:image/jpeg;base64,AAAA\""));
        assertFalse(svg.contains("http://") && svg.contains("<image") && svg.matches("(?s).*<image[^>]*href=\"http.*"));
    }
}

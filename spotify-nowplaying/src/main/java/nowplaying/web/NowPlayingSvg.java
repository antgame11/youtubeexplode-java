package nowplaying.web;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import nowplaying.NowPlaying;

/**
 * Renders a "now playing" card as a standalone SVG that works as a GitHub profile image.
 *
 * <p>GitHub serves images through a proxy that strips scripts and blocks external resources, so the
 * card is pure SVG: styles are inline, the cover art is a data: URI, and everything that moves is
 * SMIL animation (the progress bar fills up over the remaining time, and the elapsed-time digits
 * roll over once a second). The animation starts when the image is loaded, from the position the
 * server computed at that moment, so it needs a fresh copy per page view: serve it uncached.
 */
public final class NowPlayingSvg {
    static final int WIDTH = 440;
    static final int HEIGHT = 112;

    private static final int TEXT_X = 112;
    private static final int BAR_WIDTH = 312;
    private static final int DIGIT_ROW = 14;      // vertical spacing of the digits in a roller
    private static final int MAX_ANIMATED_SECONDS = 900;

    private NowPlayingSvg() {}

    public enum Theme {
        /** Follows the viewer's light/dark preference. */
        AUTO,
        DARK,
        LIGHT;

        public static Theme parse(String value) {
            if (value == null) return AUTO;
            return switch (value.toLowerCase(Locale.ROOT)) {
                case "dark" -> DARK;
                case "light" -> LIGHT;
                default -> AUTO;
            };
        }
    }

    /**
     * @param track      what is playing, or null if nothing is
     * @param progressMs position within the track at the moment the card is generated
     * @param coverUri   cover art as a data: URI, or null
     */
    public static String render(NowPlaying track, long progressMs, String coverUri, Theme theme) {
        StringBuilder svg = new StringBuilder(8192);
        svg.append("<svg xmlns=\"http://www.w3.org/2000/svg\" xmlns:xlink=\"http://www.w3.org/1999/xlink\" width=\"")
                .append(WIDTH).append("\" height=\"").append(HEIGHT).append("\" viewBox=\"0 0 ")
                .append(WIDTH).append(' ').append(HEIGHT).append("\" role=\"img\" aria-label=\"")
                .append(escape(track == null ? "Not playing on Spotify" : "Now playing: " + track)).append("\">\n");
        svg.append("<title>").append(escape(track == null ? "Not playing on Spotify" : "Now playing: " + track))
                .append("</title>\n");
        svg.append(style(theme));
        svg.append("<defs>\n")
                .append("<clipPath id=\"cover\"><rect x=\"16\" y=\"16\" width=\"80\" height=\"80\" rx=\"10\"/></clipPath>\n")
                .append("<clipPath id=\"text\"><rect x=\"").append(TEXT_X).append("\" y=\"0\" width=\"")
                .append(BAR_WIDTH).append("\" height=\"").append(HEIGHT).append("\"/></clipPath>\n")
                .append("<clipPath id=\"roll\"><rect x=\"0\" y=\"-11\" width=\"8\" height=\"").append(DIGIT_ROW).append("\"/></clipPath>\n")
                .append("</defs>\n");
        svg.append("<rect class=\"card\" x=\"0.5\" y=\"0.5\" width=\"").append(WIDTH - 1).append("\" height=\"")
                .append(HEIGHT - 1).append("\" rx=\"14\"/>\n");

        if (track == null) {
            appendCover(svg, null);
            svg.append("<text class=\"label\" x=\"").append(TEXT_X).append("\" y=\"30\">SPOTIFY</text>\n")
                    .append("<text class=\"title\" x=\"").append(TEXT_X).append("\" y=\"58\">Not playing right now</text>\n")
                    .append("<text class=\"muted\" x=\"").append(TEXT_X).append("\" y=\"78\">Check back later</text>\n");
        } else {
            appendCover(svg, coverUri);
            appendTrack(svg, track, Math.max(0, Math.min(progressMs, track.durationMs())));
        }

        svg.append("</svg>\n");
        return svg.toString();
    }

    // ---- Pieces ----

    private static String style(Theme theme) {
        String dark = ".card{fill:#0d1117;stroke:#30363d}.title{fill:#f0f6fc}.muted{fill:#8b949e}.track{fill:#30363d}.ph{fill:#21262d}";
        String light = ".card{fill:#ffffff;stroke:#d0d7de}.title{fill:#1f2328}.muted{fill:#656d76}.track{fill:#d8dee4}.ph{fill:#eaeef2}";
        String css = switch (theme) {
            case DARK -> dark;
            case LIGHT -> light;
            case AUTO -> dark + "@media (prefers-color-scheme: light){" + light + "}";
        };
        return "<style>\n"
                + "text{font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Helvetica,Arial,sans-serif}\n"
                + css + "\n"
                + ".label{font-size:10px;font-weight:600;letter-spacing:1.5px;fill:#1ed760}\n"
                + ".title{font-size:17px;font-weight:700}\n"
                + ".muted{font-size:13px}\n"
                + ".time{font-size:11px}\n"
                + ".fill{fill:#1ed760}\n"
                + "</style>\n";
    }

    private static void appendCover(StringBuilder svg, String coverUri) {
        if (coverUri != null) {
            svg.append("<image x=\"16\" y=\"16\" width=\"80\" height=\"80\" preserveAspectRatio=\"xMidYMid slice\" "
                            + "clip-path=\"url(#cover)\" href=\"").append(coverUri).append("\" xlink:href=\"")
                    .append(coverUri).append("\"/>\n");
        } else {
            svg.append("<rect class=\"ph\" x=\"16\" y=\"16\" width=\"80\" height=\"80\" rx=\"10\"/>\n")
                    .append("<text class=\"muted\" x=\"56\" y=\"65\" text-anchor=\"middle\" style=\"font-size:28px\">&#9834;</text>\n");
        }
    }

    private static void appendTrack(StringBuilder svg, NowPlaying track, long progressMs) {
        boolean playing = track.isPlaying();
        double remainingSeconds = Math.max(0, (track.durationMs() - progressMs) / 1000.0);
        boolean animate = playing && remainingSeconds > 0;

        svg.append("<text class=\"label\" x=\"").append(TEXT_X).append("\" y=\"30\">")
                .append(playing ? "NOW PLAYING" : "PAUSED").append("</text>\n");
        appendEqualizer(svg, animate);

        svg.append("<g clip-path=\"url(#text)\">\n")
                .append("<text class=\"title\" x=\"").append(TEXT_X).append("\" y=\"54\">")
                .append(escape(truncate(track.title(), 33))).append("</text>\n")
                .append("<text class=\"muted\" x=\"").append(TEXT_X).append("\" y=\"73\">")
                .append(escape(truncate(track.artistLine(), 42))).append("</text>\n")
                .append("</g>\n");

        // Progress bar
        double fraction = track.durationMs() <= 0 ? 0 : (double) progressMs / track.durationMs();
        double startWidth = BAR_WIDTH * fraction;
        svg.append("<rect class=\"track\" x=\"").append(TEXT_X).append("\" y=\"84\" width=\"").append(BAR_WIDTH)
                .append("\" height=\"5\" rx=\"2.5\"/>\n");
        svg.append("<rect class=\"fill\" x=\"").append(TEXT_X).append("\" y=\"84\" width=\"").append(num(startWidth))
                .append("\" height=\"5\" rx=\"2.5\">");
        if (animate) {
            svg.append("<animate attributeName=\"width\" from=\"").append(num(startWidth)).append("\" to=\"")
                    .append(BAR_WIDTH).append("\" dur=\"").append(num(remainingSeconds)).append("s\" fill=\"freeze\"/>");
        }
        svg.append("</rect>\n");

        appendElapsed(svg, track.durationMs(), progressMs, animate, remainingSeconds);

        svg.append("<text class=\"muted time\" x=\"").append(TEXT_X + BAR_WIDTH).append("\" y=\"106\" text-anchor=\"end\">")
                .append(formatClock(track.durationMs() / 1000)).append("</text>\n");
    }

    /** Three little bars that bounce while the music plays. */
    private static void appendEqualizer(StringBuilder svg, boolean animate) {
        String[] values = {"4;12;6;14;4", "10;4;13;6;10", "6;14;4;10;6"};
        String[] durations = {"0.9s", "1.1s", "0.8s"};
        for (int i = 0; i < 3; i++) {
            int x = 404 + i * 7;
            svg.append("<rect class=\"fill\" x=\"").append(x).append("\" y=\"").append(30 - 6).append("\" width=\"4\" height=\"6\" rx=\"1\">");
            if (animate) {
                String heights = values[i];
                StringBuilder ys = new StringBuilder();
                for (String h : heights.split(";")) ys.append(ys.length() > 0 ? ";" : "").append(30 - Integer.parseInt(h));
                svg.append("<animate attributeName=\"height\" values=\"").append(heights).append("\" dur=\"")
                        .append(durations[i]).append("\" repeatCount=\"indefinite\"/>")
                        .append("<animate attributeName=\"y\" values=\"").append(ys).append("\" dur=\"")
                        .append(durations[i]).append("\" repeatCount=\"indefinite\"/>");
            }
            svg.append("</rect>\n");
        }
    }

    // ---- Elapsed time: digit "rollers" ----

    /**
     * Draws m:ss. Each digit is a column of glyphs clipped to a one-digit window; the column slides up
     * (one discrete keyframe per change) so the clock ticks without any script.
     */
    private static void appendElapsed(StringBuilder svg, long durationMs, long progressMs, boolean animate, double remainingSeconds) {
        long totalMinutes = durationMs / 60_000;
        boolean tensOfMinutes = totalMinutes >= 10;
        double startSeconds = progressMs / 1000.0;
        double animatedFor = Math.min(remainingSeconds, MAX_ANIMATED_SECONDS);

        List<int[]> columns = new ArrayList<>(); // {x, unit}: unit 0 = minutes tens, 1 = minutes ones, 2 = seconds tens, 3 = seconds ones
        int x = TEXT_X;
        if (tensOfMinutes) {
            columns.add(new int[] {x, 0});
            x += 7;
        }
        columns.add(new int[] {x, 1});
        int colonX = x + 7;
        x += 12;
        columns.add(new int[] {x, 2});
        x += 7;
        columns.add(new int[] {x, 3});

        svg.append("<text class=\"muted time\" x=\"").append(colonX).append("\" y=\"106\">:</text>\n");

        for (int[] column : columns) {
            int unit = column[1];
            svg.append("<g transform=\"translate(").append(column[0]).append(",106)\" clip-path=\"url(#roll)\">\n");
            svg.append("<g>");
            // Ten glyphs stacked top to bottom; the first row of the tens-of-minutes roller is blank
            for (int digit = 0; digit < 10; digit++) {
                String glyph = (unit == 0 && digit == 0) ? "" : String.valueOf(digit);
                svg.append("<text class=\"muted time\" x=\"0\" y=\"").append(digit * DIGIT_ROW).append("\">").append(glyph).append("</text>");
            }

            int startDigit = digitAt(unit, (long) Math.floor(startSeconds));
            svg.append("<animateTransform attributeName=\"transform\" type=\"translate\" ");
            if (animate) {
                appendRollKeyframes(svg, unit, startSeconds, animatedFor);
            } else {
                svg.append("values=\"0 ").append(-startDigit * DIGIT_ROW).append("\" dur=\"1s\" fill=\"freeze\"");
            }
            svg.append("/>");
            svg.append("</g>\n</g>\n");
        }
    }

    /** The digit shown in the given unit at a whole number of elapsed seconds. */
    static int digitAt(int unit, long seconds) {
        long minutes = seconds / 60;
        long secs = seconds % 60;
        return switch (unit) {
            case 0 -> (int) ((minutes / 10) % 10);
            case 1 -> (int) (minutes % 10);
            case 2 -> (int) (secs / 10);
            default -> (int) (secs % 10);
        };
    }

    /**
     * Writes calcMode/keyTimes/values/dur attributes with one keyframe per change of the digit between
     * now and {@code animatedFor} seconds from now.
     */
    private static void appendRollKeyframes(StringBuilder svg, int unit, double startSeconds, double animatedFor) {
        long startWhole = (long) Math.floor(startSeconds);
        double fractionPast = startSeconds - startWhole;

        List<Double> times = new ArrayList<>();
        List<Integer> digits = new ArrayList<>();
        times.add(0.0);
        digits.add(digitAt(unit, startWhole));

        // The clock reads startWhole + k from t = k - fractionPast seconds onwards
        for (long k = 1; ; k++) {
            double t = k - fractionPast;
            if (t > animatedFor + 1e-9) break; // include the tick that lands exactly on the end
            int d = digitAt(unit, startWhole + k);
            if (d != digits.get(digits.size() - 1)) {
                times.add(t);
                digits.add(d);
            }
        }

        StringBuilder keyTimes = new StringBuilder();
        StringBuilder values = new StringBuilder();
        for (int i = 0; i < times.size(); i++) {
            if (i > 0) {
                keyTimes.append(';');
                values.append(';');
            }
            keyTimes.append(num(Math.min(0.999999, times.get(i) / animatedFor), 6));
            values.append("0 ").append(-digits.get(i) * DIGIT_ROW);
        }
        svg.append("calcMode=\"discrete\" keyTimes=\"").append(keyTimes).append("\" values=\"").append(values)
                .append("\" dur=\"").append(num(animatedFor)).append("s\" fill=\"freeze\"");
    }

    // ---- Helpers ----

    static String formatClock(long seconds) {
        return String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
    }

    private static String num(double value) {
        return num(value, 3);
    }

    private static String num(double value, int decimals) {
        String s = String.format(Locale.ROOT, "%." + decimals + "f", value);
        s = s.contains(".") ? s.replaceAll("0+$", "").replaceAll("\\.$", "") : s;
        return s;
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1).stripTrailing() + "…";
    }

    static String escape(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 8);
        for (char c : s.toCharArray()) {
            switch (c) {
                case '&' -> sb.append("&amp;");
                case '<' -> sb.append("&lt;");
                case '>' -> sb.append("&gt;");
                case '"' -> sb.append("&quot;");
                case '\'' -> sb.append("&#39;");
                default -> {
                    // Drop characters that are not allowed in XML 1.0 (control codes)
                    if (c >= 0x20 || c == '\t' || c == '\n' || c == '\r') sb.append(c);
                }
            }
        }
        return sb.toString();
    }
}

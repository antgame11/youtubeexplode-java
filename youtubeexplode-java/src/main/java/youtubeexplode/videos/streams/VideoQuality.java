package youtubeexplode.videos.streams;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import youtubeexplode.common.Resolution;
import youtubeexplode.utils.Strings;

/** Video quality (resolution tier and framerate). */
public final class VideoQuality implements Comparable<VideoQuality> {
    private static final Pattern LABEL = Pattern.compile("^(\\d+)\\D(\\d+)?");

    private final String label;
    private final int maxHeight;
    private final int framerate;

    public VideoQuality(String label, int maxHeight, int framerate) {
        this.label = label;
        this.maxHeight = maxHeight;
        this.framerate = framerate;
    }

    public VideoQuality(int maxHeight, int framerate) {
        this(formatLabel(maxHeight, framerate), maxHeight, framerate);
    }

    /** Label such as "1080p" or "720p60". */
    public String getLabel() {
        return label;
    }

    public int getMaxHeight() {
        return maxHeight;
    }

    public int getFramerate() {
        return framerate;
    }

    public boolean isHighDefinition() {
        return maxHeight >= 1080;
    }

    public Resolution getDefaultVideoResolution() {
        return switch (maxHeight) {
            case 144 -> new Resolution(256, 144);
            case 240 -> new Resolution(426, 240);
            case 360 -> new Resolution(640, 360);
            case 480 -> new Resolution(854, 480);
            case 720 -> new Resolution(1280, 720);
            case 1080 -> new Resolution(1920, 1080);
            case 1440 -> new Resolution(2560, 1440);
            case 2160 -> new Resolution(3840, 2160);
            case 2880 -> new Resolution(5120, 2880);
            case 3072 -> new Resolution(4096, 3072);
            case 4320 -> new Resolution(7680, 4320);
            default -> new Resolution(16 * maxHeight / 9, maxHeight);
        };
    }

    private static String formatLabel(int maxHeight, int framerate) {
        // Framerate appears only if it's above 30
        if (framerate <= 30) return maxHeight + "p";

        // YouTube rounds framerate to the next nearest decimal
        int framerateRounded = (int) Math.ceil(framerate / 10.0) * 10;
        return maxHeight + "p" + framerateRounded;
    }

    public static VideoQuality fromLabel(String label, int framerateFallback) {
        // Video quality labels can have the following formats:
        // - 1080p (regular stream, regular fps)
        // - 1080p60 (regular stream, high fps)
        // - 1080s (360° stream, regular fps)
        // - 1080s60 (360° stream, high fps)
        // - 2160p60 HDR (high dynamic range, high fps)
        Matcher m = LABEL.matcher(label);
        if (!m.find()) throw new IllegalArgumentException("Unrecognized video quality label '" + label + "'.");

        int maxHeight = Integer.parseInt(m.group(1));
        Integer framerate = m.group(2) != null ? Strings.parseInt(m.group(2)) : null;

        return new VideoQuality(label, maxHeight, framerate != null ? framerate : framerateFallback);
    }

    public static VideoQuality fromItag(int itag, int framerate) {
        int maxHeight =
                switch (itag) {
                    case 5, 13, 17, 91, 151, 160, 161, 278, 330, 394 -> 144;
                    case 6, 36, 92, 132, 133, 142, 242, 331, 395 -> 240;
                    case 18, 34, 43, 82, 93, 100, 134, 143, 167, 243, 332, 396 -> 360;
                    case 35, 44, 59, 78, 83, 94, 101, 135, 144, 168, 212, 213, 218, 219, 222, 223, 244, 245, 246, 333, 397 -> 480;
                    case 22, 45, 84, 95, 102, 136, 145, 169, 214, 215, 224, 225, 247, 298, 302, 334, 398 -> 720;
                    case 37, 46, 85, 96, 137, 146, 170, 216, 217, 226, 227, 248, 299, 303, 335, 399 -> 1080;
                    case 264, 271, 308, 336 -> 1440;
                    case 266, 272, 313, 315, 337 -> 2160;
                    case 38 -> 3072;
                    case 138 -> 4320;
                    default -> throw new IllegalArgumentException("Unrecognized itag '" + itag + "'.");
                };

        return new VideoQuality(maxHeight, framerate);
    }

    @Override
    public int compareTo(VideoQuality other) {
        int byHeight = Integer.compare(maxHeight, other.maxHeight);
        if (byHeight != 0) return byHeight;

        int byFramerate = Integer.compare(framerate, other.framerate);
        if (byFramerate != 0) return byFramerate;

        return label.compareToIgnoreCase(other.label);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof VideoQuality other
                && label.equalsIgnoreCase(other.label)
                && maxHeight == other.maxHeight
                && framerate == other.framerate;
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(label.toLowerCase(Locale.ROOT), maxHeight, framerate);
    }

    @Override
    public String toString() {
        return label;
    }
}

package youtubeexplode.videos.streams;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/** Encoding bitrate. */
public final class Bitrate implements Comparable<Bitrate> {
    private final long bitsPerSecond;

    public Bitrate(long bitsPerSecond) {
        this.bitsPerSecond = bitsPerSecond;
    }

    public long getBitsPerSecond() {
        return bitsPerSecond;
    }

    public double getKiloBitsPerSecond() {
        return bitsPerSecond / 1024.0;
    }

    public double getMegaBitsPerSecond() {
        return getKiloBitsPerSecond() / 1024.0;
    }

    public double getGigaBitsPerSecond() {
        return getMegaBitsPerSecond() / 1024.0;
    }

    @Override
    public int compareTo(Bitrate other) {
        return Long.compare(bitsPerSecond, other.bitsPerSecond);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Bitrate other && bitsPerSecond == other.bitsPerSecond;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(bitsPerSecond);
    }

    @Override
    public String toString() {
        double value;
        String symbol;
        if (Math.abs(getGigaBitsPerSecond()) >= 1) {
            value = getGigaBitsPerSecond();
            symbol = "Gbit/s";
        } else if (Math.abs(getMegaBitsPerSecond()) >= 1) {
            value = getMegaBitsPerSecond();
            symbol = "Mbit/s";
        } else if (Math.abs(getKiloBitsPerSecond()) >= 1) {
            value = getKiloBitsPerSecond();
            symbol = "Kbit/s";
        } else {
            value = bitsPerSecond;
            symbol = "Bit/s";
        }
        return new DecimalFormat("0.##", DecimalFormatSymbols.getInstance(Locale.ROOT)).format(value) + " " + symbol;
    }
}

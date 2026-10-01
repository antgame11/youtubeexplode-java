package youtubeexplode.videos.streams;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/** File size. Loosely based on https://github.com/omar/ByteSize (MIT license). */
public final class FileSize implements Comparable<FileSize> {
    private final long bytes;

    public FileSize(long bytes) {
        this.bytes = bytes;
    }

    public long getBytes() {
        return bytes;
    }

    public double getKiloBytes() {
        return bytes / 1024.0;
    }

    public double getMegaBytes() {
        return getKiloBytes() / 1024.0;
    }

    public double getGigaBytes() {
        return getMegaBytes() / 1024.0;
    }

    @Override
    public int compareTo(FileSize other) {
        return Long.compare(bytes, other.bytes);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof FileSize other && bytes == other.bytes;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(bytes);
    }

    @Override
    public String toString() {
        double value;
        String symbol;
        if (Math.abs(getGigaBytes()) >= 1) {
            value = getGigaBytes();
            symbol = "GB";
        } else if (Math.abs(getMegaBytes()) >= 1) {
            value = getMegaBytes();
            symbol = "MB";
        } else if (Math.abs(getKiloBytes()) >= 1) {
            value = getKiloBytes();
            symbol = "KB";
        } else {
            value = bytes;
            symbol = "B";
        }
        return new DecimalFormat("0.##", DecimalFormatSymbols.getInstance(Locale.ROOT)).format(value) + " " + symbol;
    }
}

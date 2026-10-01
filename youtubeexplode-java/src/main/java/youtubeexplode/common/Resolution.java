package youtubeexplode.common;

/** Dimensions of an image or video. */
public record Resolution(int width, int height) {
    public int area() {
        return width * height;
    }

    @Override
    public String toString() {
        return width + "x" + height;
    }
}

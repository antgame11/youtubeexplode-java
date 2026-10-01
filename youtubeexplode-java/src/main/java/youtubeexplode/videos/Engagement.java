package youtubeexplode.videos;

import java.util.Locale;

/** Engagement statistics. */
public final class Engagement {
    private final long viewCount;
    private final long likeCount;
    private final long dislikeCount;

    public Engagement(long viewCount, long likeCount, long dislikeCount) {
        this.viewCount = viewCount;
        this.likeCount = likeCount;
        this.dislikeCount = dislikeCount;
    }

    public long getViewCount() {
        return viewCount;
    }

    public long getLikeCount() {
        return likeCount;
    }

    /** YouTube no longer shows dislikes, so this value is always 0. */
    public long getDislikeCount() {
        return dislikeCount;
    }

    /** YouTube no longer shows dislikes, so this value is always 5. */
    public double getAverageRating() {
        return likeCount + dislikeCount != 0 ? 1 + 4.0 * likeCount / (likeCount + dislikeCount) : 0;
    }

    @Override
    public String toString() {
        return String.format(Locale.ROOT, "Rating: %.1f", getAverageRating());
    }
}

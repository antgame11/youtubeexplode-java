package youtubeexplode.common;

import java.util.List;

/** A page of results returned by a single request. */
public final class Batch<T> {
    private final List<T> items;

    public Batch(List<T> items) {
        this.items = List.copyOf(items);
    }

    public List<T> getItems() {
        return items;
    }
}

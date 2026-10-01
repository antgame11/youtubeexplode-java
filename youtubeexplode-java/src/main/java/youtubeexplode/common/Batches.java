package youtubeexplode.common;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Lazily paged results. Each batch is fetched from YouTube only when iteration reaches it, so
 * stopping early (e.g. with {@code limit}) avoids unnecessary requests.
 */
public final class Batches<T> implements Iterable<Batch<T>> {
    private final java.util.function.Supplier<Iterator<Batch<T>>> source;

    public Batches(java.util.function.Supplier<Iterator<Batch<T>>> source) {
        this.source = source;
    }

    @Override
    public Iterator<Batch<T>> iterator() {
        return source.get();
    }

    /**
     * Creates lazily paged results. Every iteration obtains a fresh pager from the factory; the
     * pager returns the next batch on each call, or null once there are no more.
     */
    public static <T> Batches<T> paged(java.util.function.Supplier<java.util.function.Supplier<Batch<T>>> pagerFactory) {
        return new Batches<>(() -> new Iterator<>() {
            private final java.util.function.Supplier<Batch<T>> pager = pagerFactory.get();
            private Batch<T> next;
            private boolean done;

            @Override
            public boolean hasNext() {
                if (next == null && !done) {
                    next = pager.get();
                    if (next == null) done = true;
                }
                return next != null;
            }

            @Override
            public Batch<T> next() {
                if (!hasNext()) throw new NoSuchElementException();
                Batch<T> result = next;
                next = null;
                return result;
            }
        });
    }

    /** Flattens the batches into a lazy stream of items. */
    public Stream<T> stream() {
        Iterator<Batch<T>> batches = iterator();
        Iterator<T> items = new Iterator<>() {
            private Iterator<T> current = java.util.Collections.emptyIterator();

            @Override
            public boolean hasNext() {
                while (!current.hasNext()) {
                    if (!batches.hasNext()) return false;
                    current = batches.next().getItems().iterator();
                }
                return true;
            }

            @Override
            public T next() {
                if (!hasNext()) throw new NoSuchElementException();
                return current.next();
            }
        };
        return StreamSupport.stream(Spliterators.spliteratorUnknownSize(items, Spliterator.ORDERED), false);
    }

    /** Collects every item. Warning: this may issue many requests. */
    public List<T> collect() {
        return new ArrayList<>(stream().toList());
    }

    /** Collects up to {@code count} items, stopping further requests once reached. */
    public List<T> collect(int count) {
        return stream().limit(count).toList();
    }
}

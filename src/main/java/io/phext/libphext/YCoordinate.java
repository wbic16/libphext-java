package io.phext.libphext;

import java.util.Objects;

/**
 * The large-scale Y arm of a phext coordinate (see {@link Coordinate}).
 * Mirrors {@code YCoordinate} in libphext-rs {@code src/phext.rs}.
 */
public final class YCoordinate implements Comparable<YCoordinate> {
    public long collection;
    public long volume;
    public long book;

    public YCoordinate() {
        this(1, 1, 1);
    }

    public YCoordinate(long collection, long volume, long book) {
        this.collection = collection;
        this.volume = volume;
        this.book = book;
    }

    public YCoordinate copy() {
        return new YCoordinate(collection, volume, book);
    }

    @Override
    public int compareTo(YCoordinate other) {
        int c = Long.compare(collection, other.collection);
        if (c != 0) return c;
        c = Long.compare(volume, other.volume);
        if (c != 0) return c;
        return Long.compare(book, other.book);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof YCoordinate y)) return false;
        return collection == y.collection && volume == y.volume && book == y.book;
    }

    @Override
    public int hashCode() {
        return Objects.hash(collection, volume, book);
    }

    @Override
    public String toString() {
        return collection + "." + volume + "." + book;
    }
}

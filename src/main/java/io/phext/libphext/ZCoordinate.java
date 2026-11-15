package io.phext.libphext;

import java.util.Objects;

/**
 * The large-scale Z arm of a phext coordinate (see {@link Coordinate}).
 * Mirrors {@code ZCoordinate} in libphext-rs {@code src/phext.rs}.
 */
public final class ZCoordinate implements Comparable<ZCoordinate> {
    public long library;
    public long shelf;
    public long series;

    public ZCoordinate() {
        this(1, 1, 1);
    }

    public ZCoordinate(long library, long shelf, long series) {
        this.library = library;
        this.shelf = shelf;
        this.series = series;
    }

    public ZCoordinate copy() {
        return new ZCoordinate(library, shelf, series);
    }

    @Override
    public int compareTo(ZCoordinate other) {
        int c = Long.compare(library, other.library);
        if (c != 0) return c;
        c = Long.compare(shelf, other.shelf);
        if (c != 0) return c;
        return Long.compare(series, other.series);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ZCoordinate z)) return false;
        return library == z.library && shelf == z.shelf && series == z.series;
    }

    @Override
    public int hashCode() {
        return Objects.hash(library, shelf, series);
    }

    @Override
    public String toString() {
        return library + "." + shelf + "." + series;
    }
}

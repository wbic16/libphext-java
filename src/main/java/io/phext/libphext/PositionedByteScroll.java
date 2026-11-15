package io.phext.libphext;

import java.util.Arrays;
import java.util.Objects;

/** A scroll's raw bytes together with its coordinate (the byte-level twin of {@link PositionedScroll}). */
public final class PositionedByteScroll {
    public final Coordinate coord;
    public final byte[] scroll;

    public PositionedByteScroll(Coordinate coord, byte[] scroll) {
        this.coord = coord;
        this.scroll = scroll;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PositionedByteScroll other)) return false;
        return coord.equals(other.coord) && Arrays.equals(scroll, other.scroll);
    }

    @Override
    public int hashCode() {
        return 31 * coord.hashCode() + Arrays.hashCode(scroll);
    }

    @Override
    public String toString() {
        return Objects.toString(coord) + ": " + scroll.length + " bytes";
    }
}

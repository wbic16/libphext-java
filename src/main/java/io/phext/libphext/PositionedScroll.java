package io.phext.libphext;

import java.util.Objects;

/** A scroll together with the coordinate it sits at. Mirrors libphext-rs. */
public final class PositionedScroll implements Comparable<PositionedScroll> {
    public Coordinate coord;
    public String scroll;

    public PositionedScroll() {
        this(new Coordinate(), "");
    }

    public PositionedScroll(Coordinate coord, String scroll) {
        this.coord = coord;
        this.scroll = scroll;
    }

    @Override
    public int compareTo(PositionedScroll other) {
        return coord.compareTo(other.coord);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PositionedScroll other)) return false;
        return coord.equals(other.coord) && scroll.equals(other.scroll);
    }

    @Override
    public int hashCode() {
        return Objects.hash(coord, scroll);
    }

    @Override
    public String toString() {
        // Rust's Display takes the first 4 bytes of the scroll; kept total here
        // (first 4 characters, or the whole scroll when shorter).
        String head = scroll.length() <= 4 ? scroll : scroll.substring(0, 4);
        return coord + ": " + head;
    }
}

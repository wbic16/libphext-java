package io.phext.libphext;

/** A coordinate range (start..end), mirroring libphext-rs {@code Range}. */
public final class Range {
    public Coordinate start;
    public Coordinate end;

    public Range() {
        this(new Coordinate(), new Coordinate());
    }

    public Range(Coordinate start, Coordinate end) {
        this.start = start;
        this.end = end;
    }

    @Override
    public String toString() {
        return start + "-" + end;
    }
}

package io.phext.libphext;

/**
 * A parsed range address: two point endpoints joined by one hyphen,
 * {@code <point>-<point>}, normalized at parse time to ascending
 * (universe, coordinate, line, column) order — direction is discarded, per
 * the ratified range syntax (RANGE-SYNTAX-PROPOSAL.md, 2026-11-15).
 *
 * <p>The canonical {@link #toString()} is the two endpoints' canonical
 * forms joined by {@code -}.
 *
 * <p>Mirrors {@code RangeAddress} in libphext-rs {@code src/phext.rs}
 * (0.6.1 addressing).
 */
public record RangeAddress(PointAddress start, PointAddress end) {

    @Override
    public String toString() {
        return start + "-" + end;
    }
}

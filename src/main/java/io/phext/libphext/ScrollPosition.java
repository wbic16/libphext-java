package io.phext.libphext;

/**
 * A caret boundary inside one scroll: 1-based line and column.
 *
 * <p>Column 1 is the boundary before the line's first character; a line of
 * {@code n} characters has valid columns 1..{@code n}+1. Columns count
 * Unicode scalar values, never bytes and never UTF-16 code units. Lines
 * split on 0x0A, which stays inside the scroll.
 *
 * <p>Mirrors {@code ScrollPosition} in libphext-rs {@code src/phext.rs}
 * (0.6.1 addressing).
 */
public record ScrollPosition(long line, long column) implements Comparable<ScrollPosition> {

    @Override
    public int compareTo(ScrollPosition other) {
        int c = Long.compare(line, other.line);
        if (c != 0) {
            return c;
        }
        return Long.compare(column, other.column);
    }

    @Override
    public String toString() {
        return line + "," + column;
    }
}

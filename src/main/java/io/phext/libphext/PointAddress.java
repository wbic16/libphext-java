package io.phext.libphext;

/**
 * One parsed point address: {@code [$N:]<9D coordinate>[@<line>,<column>[+<offsetChars>]]}.
 *
 * <p>{@code universe} is 1-based (1 when the {@code $N:} prefix is absent).
 * {@code position} is {@code null} when the address carries no {@code @}
 * part: the point then names its whole scroll (as a range start, its first
 * boundary; as a range end, its last). {@code offset} is {@code null} for a
 * plain caret point; when present it selects forward that many Unicode
 * scalar values from the position, within the scroll.
 *
 * <p>The canonical {@link #toString()} never emits {@code $1:}, writes
 * integers in minimal decimal form, and carries no whitespace — the same
 * canonical form as libphext-rs {@code PointAddress}'s {@code Display}.
 *
 * <p>Mirrors {@code PointAddress} in libphext-rs {@code src/phext.rs}
 * (0.6.1 addressing).
 */
public record PointAddress(long universe, Coordinate coordinate, ScrollPosition position, Long offset) {

    @Override
    public String toString() {
        StringBuilder result = new StringBuilder();
        if (universe > 1) {
            result.append('$').append(universe).append(':');
        }
        result.append(coordinate);
        if (position != null) {
            result.append('@').append(position.line()).append(',').append(position.column());
            if (offset != null) {
                result.append('+').append(offset.longValue());
            }
        }
        return result.toString();
    }
}

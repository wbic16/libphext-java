package io.phext.libphext;

import java.util.Objects;

/**
 * A phext coordinate: a default-initialized address at 1.1.1/1.1.1/1.1.1.
 *
 * <p>Phext coordinates are formed along a 9-dimensional hierarchy with three
 * arms of the form z3.z2.z1/y3.y2.y1.x3.x2.x1 where:
 * <ul>
 *   <li>Z contains library (z3), shelf (z2), series (z1)</li>
 *   <li>Y contains collection (y3), volume (y2), book (y1)</li>
 *   <li>X contains chapter (x3), section (x2), scroll (x1)</li>
 * </ul>
 *
 * <p>Components are unbounded (the 999 cap was removed in libphext 0.4.0).
 * Rust uses {@code usize}; this port uses {@code long}, so the representable
 * ceiling is {@link Long#MAX_VALUE} instead of 2^64-1.
 *
 * <p>Mirrors {@code Coordinate} in libphext-rs {@code src/phext.rs}.
 */
public final class Coordinate implements Comparable<Coordinate> {
    public ZCoordinate z;
    public YCoordinate y;
    public XCoordinate x;

    public Coordinate() {
        this(new ZCoordinate(), new YCoordinate(), new XCoordinate());
    }

    public Coordinate(ZCoordinate z, YCoordinate y, XCoordinate x) {
        this.z = z;
        this.y = y;
        this.x = x;
    }

    public Coordinate copy() {
        return new Coordinate(z.copy(), y.copy(), x.copy());
    }

    /**
     * The nine components in canonical order (library, shelf, series,
     * collection, volume, book, chapter, section, scroll). The length of
     * this array is the coordinate's dimension count - the branching
     * stores derive their depth from it rather than hardcoding 9.
     */
    public long[] toComponents() {
        return new long[]{z.library, z.shelf, z.series,
            y.collection, y.volume, y.book,
            x.chapter, x.section, x.scroll};
    }

    /** Builds a coordinate from {@link #toComponents()} order. */
    public static Coordinate fromComponents(long[] c) {
        if (c.length != 9) {
            throw new IllegalArgumentException("a phext coordinate has 9 components");
        }
        return new Coordinate(new ZCoordinate(c[0], c[1], c[2]),
            new YCoordinate(c[3], c[4], c[5]), new XCoordinate(c[6], c[7], c[8]));
    }

    // ------------------------------------------------------------------
    // Parsing
    // ------------------------------------------------------------------

    /**
     * The strict parser, mirroring Rust {@code TryFrom<&str> for Coordinate}:
     * exactly three '/'-separated arms of three '.'-separated components,
     * each component a valid non-negative integer (a leading '+' is accepted,
     * as in Rust; whitespace, signs, empty and out-of-range components are
     * errors). Throws {@link PhextParseException} on any malformed input.
     */
    public static Coordinate tryParse(String value) {
        String[] parts = value.split("/", -1);
        if (parts.length != 3) {
            throw new PhextParseException();
        }
        String[] zp = parts[0].split("\\.", -1);
        String[] yp = parts[1].split("\\.", -1);
        String[] xp = parts[2].split("\\.", -1);
        if (zp.length != 3 || yp.length != 3 || xp.length != 3) {
            throw new PhextParseException();
        }
        Coordinate result = new Coordinate();
        result.z.library = parseComponent(zp[0]);
        result.z.shelf = parseComponent(zp[1]);
        result.z.series = parseComponent(zp[2]);
        result.y.collection = parseComponent(yp[0]);
        result.y.volume = parseComponent(yp[1]);
        result.y.book = parseComponent(yp[2]);
        result.x.chapter = parseComponent(xp[0]);
        result.x.section = parseComponent(xp[1]);
        result.x.scroll = parseComponent(xp[2]);
        return result;
    }

    static long parseComponent(String s) {
        // Rust usize::from_str: optional leading '+', then ASCII digits only.
        String digits = s.startsWith("+") ? s.substring(1) : s;
        if (digits.isEmpty()) {
            throw new PhextParseException();
        }
        for (int i = 0; i < digits.length(); i++) {
            char c = digits.charAt(i);
            if (c < '0' || c > '9') {
                throw new PhextParseException();
            }
        }
        try {
            return Long.parseLong(digits);
        } catch (NumberFormatException e) {
            // beyond the range of a Java long (the port's component type)
            throw new PhextParseException();
        }
    }

    // ------------------------------------------------------------------
    // Validation and formatting
    // ------------------------------------------------------------------

    public boolean validateCoordinate() {
        return z.library >= Phext.COORDINATE_MINIMUM
            && z.shelf >= Phext.COORDINATE_MINIMUM
            && z.series >= Phext.COORDINATE_MINIMUM
            && y.collection >= Phext.COORDINATE_MINIMUM
            && y.volume >= Phext.COORDINATE_MINIMUM
            && y.book >= Phext.COORDINATE_MINIMUM
            && x.chapter >= Phext.COORDINATE_MINIMUM
            && x.section >= Phext.COORDINATE_MINIMUM
            && x.scroll >= Phext.COORDINATE_MINIMUM;
    }

    /**
     * Canonical format (z3.z2.z1/y3.y2.y1/x3.x2.x1), mirroring the inherent
     * {@code Coordinate::to_string} in Rust: the empty string when the
     * coordinate is invalid.
     */
    @Override
    public String toString() {
        if (!validateCoordinate()) {
            return "";
        }
        return z + "/" + y + "/" + x;
    }

    /** URL-encoded format: the canonical format with ';' macro breaks. */
    public String toUrlencoded() {
        if (!validateCoordinate()) {
            return "";
        }
        return z + ";" + y + ";" + x;
    }

    // ------------------------------------------------------------------
    // Dimension breaks (components are unbounded: advancing never saturates)
    // ------------------------------------------------------------------

    public void scrollBreak() {
        x.scroll += 1;
    }

    public void sectionBreak() {
        x.section += 1;
        x.scroll = 1;
    }

    public void chapterBreak() {
        x.chapter += 1;
        x.section = 1;
        x.scroll = 1;
    }

    public void bookBreak() {
        y.book += 1;
        x.chapter = 1;
        x.section = 1;
        x.scroll = 1;
    }

    public void volumeBreak() {
        y.volume += 1;
        y.book = 1;
        x.chapter = 1;
        x.section = 1;
        x.scroll = 1;
    }

    public void collectionBreak() {
        y.collection += 1;
        y.volume = 1;
        y.book = 1;
        x.chapter = 1;
        x.section = 1;
        x.scroll = 1;
    }

    public void seriesBreak() {
        z.series += 1;
        y.collection = 1;
        y.volume = 1;
        y.book = 1;
        x.chapter = 1;
        x.section = 1;
        x.scroll = 1;
    }

    public void shelfBreak() {
        z.shelf += 1;
        z.series = 1;
        y.collection = 1;
        y.volume = 1;
        y.book = 1;
        x.chapter = 1;
        x.section = 1;
        x.scroll = 1;
    }

    public void libraryBreak() {
        z.library += 1;
        z.shelf = 1;
        z.series = 1;
        y.collection = 1;
        y.volume = 1;
        y.book = 1;
        x.chapter = 1;
        x.section = 1;
        x.scroll = 1;
    }

    // ------------------------------------------------------------------
    // Value semantics (field order matches Rust's derived Ord/Hash/Eq)
    // ------------------------------------------------------------------

    @Override
    public int compareTo(Coordinate other) {
        int c = z.compareTo(other.z);
        if (c != 0) return c;
        c = y.compareTo(other.y);
        if (c != 0) return c;
        return x.compareTo(other.x);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Coordinate other)) return false;
        return z.equals(other.z) && y.equals(other.y) && x.equals(other.x);
    }

    @Override
    public int hashCode() {
        return Objects.hash(z, y, x);
    }
}

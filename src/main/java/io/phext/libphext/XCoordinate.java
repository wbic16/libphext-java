package io.phext.libphext;

import java.util.Objects;

/**
 * The large-scale X arm of a phext coordinate (see {@link Coordinate}).
 * Mirrors {@code XCoordinate} in libphext-rs {@code src/phext.rs}.
 */
public final class XCoordinate implements Comparable<XCoordinate> {
    public long chapter;
    public long section;
    public long scroll;

    public XCoordinate() {
        this(1, 1, 1);
    }

    public XCoordinate(long chapter, long section, long scroll) {
        this.chapter = chapter;
        this.section = section;
        this.scroll = scroll;
    }

    public XCoordinate copy() {
        return new XCoordinate(chapter, section, scroll);
    }

    @Override
    public int compareTo(XCoordinate other) {
        int c = Long.compare(chapter, other.chapter);
        if (c != 0) return c;
        c = Long.compare(section, other.section);
        if (c != 0) return c;
        return Long.compare(scroll, other.scroll);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof XCoordinate x)) return false;
        return chapter == x.chapter && section == x.section && scroll == x.scroll;
    }

    @Override
    public int hashCode() {
        return Objects.hash(chapter, section, scroll);
    }

    @Override
    public String toString() {
        return chapter + "." + section + "." + scroll;
    }
}

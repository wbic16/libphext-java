package io.phext.libphext;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A chain: an ordered series of complete phexts within one stream, joined
 * by CHAIN_BREAK (0x02) and optionally terminated by CHAIN_CLOSE (0x03).
 * The chain layer is a stream-level layer, one rung above the library
 * break: the nine dimension breaks structure a single phext, while the two
 * chain bytes structure a stream of phexts. Each member is a whole phext,
 * worked with the ordinary phext functions; read in succession, a chain's
 * members are editions in sequence — the chain is how a phext stream
 * encodes time.
 *
 * <p>{@code closed} records how the chain ended: true when a CHAIN_CLOSE
 * terminated it, false when the stream simply ran out (EOF). A chain that
 * ends at EOF is unclosed — possibly truncated — and is never silently
 * promoted to closed. A stream with no chain bytes at all is a single
 * unclosed chain holding one member phext: every pre-0.6 phext reads this
 * way.
 *
 * <p>Mirrors {@code Chain} in libphext-rs {@code src/phext.rs} (0.6.0).
 */
public final class Chain {
    public List<String> members;
    public boolean closed;

    public Chain() {
        this(new ArrayList<>(), false);
    }

    public Chain(List<String> members, boolean closed) {
        this.members = members;
        this.closed = closed;
    }

    /**
     * Serializes this chain alone: its members joined by CHAIN_BREAK, with
     * a trailing CHAIN_CLOSE when the chain is closed. See
     * {@link Phext#serializeChains} for whole streams.
     */
    public String serialize() {
        StringBuilder result = new StringBuilder();
        boolean first = true;
        for (String member : members) {
            if (!first) {
                result.append(Phext.CHAIN_BREAK);
            }
            first = false;
            result.append(member);
        }
        if (closed) {
            result.append(Phext.CHAIN_CLOSE);
        }
        return result.toString();
    }

    /**
     * Fetches a scroll by multiverse address: "$N:coord", or a bare
     * coordinate (universe 1). Out-of-range universes, malformed addresses
     * and missing scrolls yield an empty string, as
     * {@link Phext#fetch} does — the same value as a present-but-empty
     * scroll. There is no error channel in the fetch API.
     */
    public String fetch(String address) {
        Phext.UniverseAddress parsed = Phext.parseUniverseAddress(address);
        if (parsed == null) {
            return "";
        }
        long index = parsed.universe() - 1;
        if (index < 0 || index >= members.size()) {
            return "";
        }
        return Phext.fetch(members.get((int) index), parsed.coordinate());
    }

    // ------------------------------------------------------------------
    // line/column addressing (0.6.1)
    // ------------------------------------------------------------------

    /**
     * A point resolved against a chain: (universe, coordinate, character
     * offset within the scroll), the offset counted in Unicode scalar
     * values. Mirrors the tuple returned by {@code Chain::resolve_point}
     * in libphext-rs.
     */
    public record ResolvedPoint(long universe, Coordinate coordinate, long offset) {
    }

    /**
     * One per-scroll segment of a resolved range: (universe, coordinate,
     * segment text), in ascending order. Mirrors one entry of the vector
     * returned by {@code Chain::resolve_range} in libphext-rs.
     */
    public record RangeSegment(long universe, Coordinate coordinate, String text) {
    }

    /**
     * Resolves a point address against this chain. A point without
     * {@code "@"} resolves to offset 0 of its scroll. Returns {@code null}
     * when the universe or scroll is absent or the position is out of
     * range — never clamps, mirroring Rust's {@code Option}.
     */
    public ResolvedPoint resolvePoint(PointAddress point) {
        if (point.universe() < 1 || point.universe() > members.size()) {
            return null;
        }
        String text = Phext.addressedScrollText(
            members.get((int) point.universe() - 1), point.coordinate());
        if (text == null) {
            return null;
        }
        long offset;
        if (point.position() == null) {
            offset = 0;
        } else {
            Long resolved = Phext.resolveScrollOffset(text, point.position());
            if (resolved == null) {
                return null;
            }
            offset = resolved;
        }
        return new ResolvedPoint(point.universe(), point.coordinate(), offset);
    }

    /**
     * Fetches a point address string. A bare point is a caret: it fetches
     * the empty string. The offset form fetches the selected characters:
     * from the point, forward {@code offset} characters (Unicode scalar
     * values) within the scroll, terminating at the scroll boundary. Any
     * parse or resolution failure fetches the empty string, as
     * {@link #fetch} does — the fetch API never throws.
     */
    public String fetchPoint(String address) {
        PointAddress point = Phext.parsePointAddress(address);
        if (point == null) {
            return "";
        }
        if (point.offset() == null) {
            return "";
        }
        ResolvedPoint resolved = resolvePoint(point);
        if (resolved == null) {
            return "";
        }
        String text = Phext.addressedScrollText(
            members.get((int) point.universe() - 1), point.coordinate());
        if (text == null) {
            return "";
        }
        long total = Phext.scalarCount(text);
        long start = resolved.offset();
        long take = Math.min(point.offset(), total - start);
        return Phext.scalarSlice(text, start, start + take);
    }

    /**
     * Resolves a range against this chain into per-scroll segments in
     * ascending order. Middle scrolls contribute their whole text; the
     * endpoint scrolls contribute from/to their endpoint boundaries (an
     * endpoint without {@code "@"} contributes its whole scroll). Returns
     * {@code null} when either endpoint fails to resolve — an
     * out-of-range endpoint is an error, never a clamp.
     */
    public List<RangeSegment> resolveRange(RangeAddress range) {
        PointAddress start = range.start();
        PointAddress end = range.end();
        if (start.universe() < 1 || end.universe() > members.size()
            || start.universe() > end.universe()) {
            return null;
        }
        // both endpoint scrolls must be present, and both endpoint
        // positions must resolve
        if (resolvePoint(new PointAddress(
                start.universe(), start.coordinate(), start.position(), null)) == null) {
            return null;
        }
        if (resolvePoint(new PointAddress(
                end.universe(), end.coordinate(), end.position(), null)) == null) {
            return null;
        }
        List<RangeSegment> segments = new ArrayList<>();
        for (long universe = start.universe(); universe <= end.universe(); universe++) {
            String member = members.get((int) universe - 1);
            List<PositionedScroll> scrolls = new ArrayList<>(Phext.phokenize(member));
            scrolls.sort(null);
            for (PositionedScroll ps : scrolls) {
                Coordinate coord = ps.coord;
                if (universe == start.universe() && coord.compareTo(start.coordinate()) < 0) {
                    continue;
                }
                if (universe == end.universe() && coord.compareTo(end.coordinate()) > 0) {
                    continue;
                }
                long lo = 0;
                long hi = Phext.scalarCount(ps.scroll);
                if (universe == start.universe() && coord.equals(start.coordinate())
                    && start.position() != null) {
                    Long resolved = Phext.resolveScrollOffset(ps.scroll, start.position());
                    if (resolved == null) {
                        return null;
                    }
                    lo = resolved;
                }
                if (universe == end.universe() && coord.equals(end.coordinate())
                    && end.position() != null) {
                    Long resolved = Phext.resolveScrollOffset(ps.scroll, end.position());
                    if (resolved == null) {
                        return null;
                    }
                    hi = resolved;
                }
                if (lo > hi) {
                    return null;
                }
                segments.add(new RangeSegment(universe, coord, Phext.scalarSlice(ps.scroll, lo, hi)));
            }
        }
        return segments;
    }

    /**
     * Fetches a range address string: the spanned text with phext
     * structure bytes preserved — the covered segments re-assembled at
     * their coordinates into a phext (chain) fragment (scroll breaks
     * between covered scrolls inside a universe, CHAIN_BREAK between
     * universes, never a trailing CHAIN_CLOSE: a fragment is an excerpt,
     * not a terminated chain), so the result round-trips as a phext
     * fragment. Empty segments contribute nothing. Any parse or
     * resolution failure fetches the empty string, as {@link #fetch}
     * does — the fetch API never throws.
     */
    public String fetchRange(String address) {
        RangeAddress range = Phext.parseRangeAddress(address);
        if (range == null) {
            return "";
        }
        List<RangeSegment> segments = resolveRange(range);
        if (segments == null) {
            return "";
        }
        List<String> fragmentMembers = new ArrayList<>();
        for (long universe = range.start().universe(); universe <= range.end().universe(); universe++) {
            String member = "";
            for (RangeSegment segment : segments) {
                if (segment.universe() != universe) {
                    continue;
                }
                if (!segment.text().isEmpty()) {
                    member = Phext.insert(member, segment.coordinate(), segment.text());
                }
            }
            fragmentMembers.add(member);
        }
        return new Chain(fragmentMembers, false).serialize();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Chain other)) return false;
        return closed == other.closed && members.equals(other.members);
    }

    @Override
    public int hashCode() {
        return Objects.hash(members, closed);
    }

    @Override
    public String toString() {
        return "Chain{members=" + members.size() + ", closed=" + closed + "}";
    }
}

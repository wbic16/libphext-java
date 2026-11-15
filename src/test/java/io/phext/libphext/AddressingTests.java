package io.phext.libphext;

import java.util.List;

/**
 * Unit tests for the 0.6.1 line/column addressing layer
 * ({@link Phext#parsePointAddress}, {@link Phext#parseRangeAddress},
 * {@link Chain#resolvePoint}, {@link Chain#fetchPoint},
 * {@link Chain#resolveRange}, {@link Chain#fetchRange}), beyond the
 * shared range fixtures in {@link RangeFixtureTests}: canonical forms,
 * parse rejections, the Unicode-scalar counting trap (Java strings are
 * UTF-16), and fragment shape.
 */
public final class AddressingTests {
    private AddressingTests() {
    }

    private static final String CB = "\u0002";
    private static final String CC = "\u0003";

    public static void register() {
        TestMain.test("AddressingTests", "point_parse_and_canonical_form", () -> {
            A.eq("$3:1.1.1/1.1.1/1.1.1@24,5",
                Phext.parsePointAddress("$3:1.1.1/1.1.1/1.1.1@24,5").toString());
            // $1: is accepted on input and never emitted
            A.eq("1.1.1/1.1.1/1.1.1@24,5",
                Phext.parsePointAddress("$1:1.1.1/1.1.1/1.1.1@24,5").toString());
            // leading zeros parse by value; canonical form is minimal decimal
            A.eq("1.1.1/1.1.1/1.1.1@24,5",
                Phext.parsePointAddress("1.1.1/1.1.1/1.1.1@024,005").toString());
            // bare coordinate: no position, no offset
            PointAddress bare = Phext.parsePointAddress("1.1.1/1.1.1/1.1.2");
            A.eq("1.1.1/1.1.1/1.1.2", bare.toString());
            A.isNull(bare.position(), "bare coordinate has no position");
            A.isNull(bare.offset(), "bare coordinate has no offset");
            A.eq(1, bare.universe());
            // the offset form round-trips, +0 included
            A.eq("1.1.1/1.1.1/1.1.1@24,5+10",
                Phext.parsePointAddress("1.1.1/1.1.1/1.1.1@24,5+10").toString());
            A.eq("1.1.1/1.1.1/1.1.1@1,1+0",
                Phext.parsePointAddress("1.1.1/1.1.1/1.1.1@1,1+0").toString());
        });

        TestMain.test("AddressingTests", "point_parse_rejections", () -> {
            String[] bad = {
                "$0:1.1.1/1.1.1/1.1.1@1,1",      // universe 0
                "1.1.1/1.1.1/1.1.1@0,5",        // line 0
                "1.1.1/1.1.1/1.1.1@24,0",       // column 0
                "1.1.1/1.1.1/0.1.1@1,1",        // coordinate component 0
                "1.1.1/1.1.1/1.1.1@24",         // position without column
                "1.1.1/1.1.1/1.1.1@24,5+",      // offset without digits
                "1.1.1/1.1.1/1.1.1@24,+5",      // column with a sign
                "1.1.1/1.1.1/1.1.1@24,5@1,1",   // second @
                "1.1.1/1.1.1@1,1",              // coordinate with two arms
                "1.1.1/1.1.1/1.1.1@24,5+1+2",   // second +
                "1.1.1/1.1.1/1.1.1@+24,5",      // line with a sign
                "+1.1.1/1.1.1/1.1.1@1,1",       // coordinate with a sign
                "1.1.1/1.1.1/1.1.1 ",           // trailing whitespace
                "",                             // empty
                "$:1.1.1/1.1.1/1.1.1@1,1",      // universe without digits
                "$2:1.1.1/1.1.1/1.1.1@1,-1",    // negative column
            };
            for (String s : bad) {
                A.isNull(Phext.parsePointAddress(s), "must reject " + A.show(s));
            }
        });

        TestMain.test("AddressingTests", "range_parse_normalizes_and_rejects", () -> {
            // reversed input normalizes; direction is discarded
            A.eq("1.1.1/1.1.1/1.1.1@24,5-1.1.1/1.1.1/1.1.1@24,9",
                Phext.parseRangeAddress("1.1.1/1.1.1/1.1.1@24,9-1.1.1/1.1.1/1.1.1@24,5").toString());
            // $1: dropped even when the other endpoint carries $N:
            A.eq("1.1.1/1.1.1/1.1.1@1,1-$3:1.1.1/1.1.1/1.1.1@1,1",
                Phext.parseRangeAddress("$1:1.1.1/1.1.1/1.1.1@1,1-$3:1.1.1/1.1.1/1.1.1@1,1").toString());
            // bare endpoints swap by coordinate
            A.eq("1.1.1/1.1.1/1.1.1-1.1.1/1.1.1/1.1.3",
                Phext.parseRangeAddress("1.1.1/1.1.1/1.1.3-1.1.1/1.1.1/1.1.1").toString());
            // a bare end names its scroll's last boundary, so it sorts
            // after any position in its scroll: neither written order swaps
            A.eq("1.1.1/1.1.1/1.1.1-1.1.1/1.1.1/1.1.1@9,9",
                Phext.parseRangeAddress("1.1.1/1.1.1/1.1.1-1.1.1/1.1.1/1.1.1@9,9").toString());
            A.eq("1.1.1/1.1.1/1.1.1@9,9-1.1.1/1.1.1/1.1.1",
                Phext.parseRangeAddress("1.1.1/1.1.1/1.1.1@9,9-1.1.1/1.1.1/1.1.1").toString());
            String[] bad = {
                "1.1.1/1.1.1/1.1.1@1,1+3-1.1.1/1.1.1/1.1.1@1,5",  // offset endpoint
                "1.1.1/1.1.1/1.1.1@1,1-1.1.1/1.1.1/1.1.1@1,5+2",  // offset endpoint
                "1.1.1/1.1.1/1.1.1",                              // a point is not a range
                "1.1.1/1.1.1/1.1.1-1.1.1/1.1.1/1.1.1-1.1.1/1.1.1/1.1.1", // two hyphens
                "1.1.1/1.1.1/1.1.1-",                             // missing end
                "-1.1.1/1.1.1/1.1.1",                             // missing start
            };
            for (String s : bad) {
                A.isNull(Phext.parseRangeAddress(s), "must reject " + A.show(s));
            }
        });

        TestMain.test("AddressingTests", "resolve_and_fetch_point", () -> {
            Chain chain = Phext.parseChains("alpha bravo\ncharlie delta").get(0);
            Chain.ResolvedPoint at = chain.resolvePoint(Phext.parsePointAddress("1.1.1/1.1.1/1.1.1@2,3"));
            A.notNull(at, "@2,3 resolves");
            A.eq(1, at.universe());
            A.eq("1.1.1/1.1.1/1.1.1", at.coordinate().toString());
            A.eq(14, at.offset());
            // bare point resolves to offset 0
            A.eq(0, chain.resolvePoint(Phext.parsePointAddress("1.1.1/1.1.1/1.1.1")).offset());
            // last valid boundary of a line and of the scroll resolve
            A.notNull(chain.resolvePoint(Phext.parsePointAddress("1.1.1/1.1.1/1.1.1@1,12")), "line end boundary");
            A.notNull(chain.resolvePoint(Phext.parsePointAddress("1.1.1/1.1.1/1.1.1@2,14")), "scroll end boundary");
            // out of range fails — never clamps
            A.isNull(chain.resolvePoint(Phext.parsePointAddress("1.1.1/1.1.1/1.1.1@1,13")), "column past line end");
            A.isNull(chain.resolvePoint(Phext.parsePointAddress("1.1.1/1.1.1/1.1.1@3,1")), "line past scroll end");
            A.isNull(chain.resolvePoint(Phext.parsePointAddress("1.1.1/1.1.1/1.1.9@1,1")), "absent scroll");
            A.isNull(chain.resolvePoint(Phext.parsePointAddress("$2:1.1.1/1.1.1/1.1.1@1,1")), "absent universe");
            // a caret fetches ""; the offset form fetches forward scalars
            A.eq("", chain.fetchPoint("1.1.1/1.1.1/1.1.1@1,1"));
            A.eq("alpha", chain.fetchPoint("1.1.1/1.1.1/1.1.1@1,1+5"));
            A.eq("a b", chain.fetchPoint("1.1.1/1.1.1/1.1.1@1,5+3"));
            // offset crosses line boundaries and clips at the scroll end
            A.eq("o\ncharlie delta", chain.fetchPoint("1.1.1/1.1.1/1.1.1@1,11+100"));
            A.eq("", chain.fetchPoint("1.1.1/1.1.1/1.1.1@1,1+0"));
            // failures fetch "" and never throw
            A.eq("", chain.fetchPoint("1.1.1/1.1.1/1.1.1@9,1+3"));
            A.eq("", chain.fetchPoint("not an address"));
        });

        TestMain.test("AddressingTests", "unicode_scalars_not_utf16_units", () -> {
            // "a" + U+1F600 (one scalar, two UTF-16 units, four UTF-8 bytes) + "b"
            String scroll = "a\uD83D\uDE00b\nxy";
            Chain chain = Phext.parseChains(scroll).get(0);
            // column 3 is the boundary after the emoji: scalar offset 2
            // (a UTF-16 count would say 3, a byte count would say 5)
            A.eq(2, chain.resolvePoint(Phext.parsePointAddress("1.1.1/1.1.1/1.1.1@1,3")).offset());
            A.eq(3, chain.resolvePoint(Phext.parsePointAddress("1.1.1/1.1.1/1.1.1@1,4")).offset());
            // offset fetch counts scalars across the surrogate pair
            A.eq("\uD83D\uDE00", chain.fetchPoint("1.1.1/1.1.1/1.1.1@1,2+1"));
            A.eq("\uD83D\uDE00b", chain.fetchPoint("1.1.1/1.1.1/1.1.1@1,2+2"));
            // range fetch inside the scroll, counted in scalars
            A.eq("\uD83D\uDE00", chain.fetchRange("1.1.1/1.1.1/1.1.1@1,2-1.1.1/1.1.1/1.1.1@1,3"));
        });

        TestMain.test("AddressingTests", "range_fetch_fragments", () -> {
            Chain chain = Phext.parseChains("alpha" + CB + "uni2 home" + CB + "third" + CC).get(0);
            // cross-universe: CHAIN_BREAK between universes, no trailing CHAIN_CLOSE
            String frag = chain.fetchRange("1.1.1/1.1.1/1.1.1@1,2-$2:1.1.1/1.1.1/1.1.1@1,4");
            A.eq("lpha" + CB + "uni", frag);
            A.isFalse(frag.contains(CC), "fragment carries no CHAIN_CLOSE");
            // whole-universe bare range covers the middle universe whole
            A.eq("alpha" + CB + "uni2 home" + CB + "third",
                chain.fetchRange("1.1.1/1.1.1/1.1.1-$3:1.1.1/1.1.1/1.1.1"));
            // an empty range is a caret and fetches ""
            A.eq("", chain.fetchRange("1.1.1/1.1.1/1.1.1@1,3-1.1.1/1.1.1/1.1.1@1,3"));
            List<Chain.RangeSegment> caret = chain.resolveRange(
                Phext.parseRangeAddress("1.1.1/1.1.1/1.1.1@1,3-1.1.1/1.1.1/1.1.1@1,3"));
            A.notNull(caret, "empty range still resolves");
            // reversed input fetches the same extent as canonical input
            A.eq(chain.fetchRange("1.1.1/1.1.1/1.1.1@1,1-1.1.1/1.1.1/1.1.1@1,4"),
                chain.fetchRange("1.1.1/1.1.1/1.1.1@1,4-1.1.1/1.1.1/1.1.1@1,1"));
            // resolution failures fetch "" and never throw
            A.eq("", chain.fetchRange("1.1.1/1.1.1/1.1.1@1,1-$4:1.1.1/1.1.1/1.1.1@1,1"));
            A.eq("", chain.fetchRange("1.1.1/1.1.1/1.1.1@1,1-1.1.1/1.1.1/1.1.9@1,1"));
            A.isNull(chain.resolveRange(Phext.parseRangeAddress("1.1.1/1.1.1/1.1.1@1,1-1.1.1/1.1.1/1.1.1@9,1")),
                "line past scroll end fails resolution");
        });

        TestMain.test("AddressingTests", "range_segments_shape", () -> {
            // two scrolls in one universe: first partial, second partial,
            // segments in ascending coordinate order
            String member = "one\u0017two";
            Chain chain = Phext.parseChains(member).get(0);
            List<Chain.RangeSegment> segments = chain.resolveRange(
                Phext.parseRangeAddress("1.1.1/1.1.1/1.1.1@1,2-1.1.1/1.1.1/1.1.2@1,3"));
            A.notNull(segments, "cross-scroll range resolves");
            A.eq(2, segments.size());
            A.eq("1.1.1/1.1.1/1.1.1", segments.get(0).coordinate().toString());
            A.eq("ne", segments.get(0).text());
            A.eq("1.1.1/1.1.1/1.1.2", segments.get(1).coordinate().toString());
            A.eq("tw", segments.get(1).text());
            // the fragment re-assembles the segments at their coordinates
            A.eq("ne\u0017tw", chain.fetchRange("1.1.1/1.1.1/1.1.1@1,2-1.1.1/1.1.1/1.1.2@1,3"));
        });
    }
}

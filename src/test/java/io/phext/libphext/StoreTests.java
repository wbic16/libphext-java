package io.phext.libphext;

import java.util.List;
import java.util.Random;

/**
 * Tests for the v0.7.0 performance stores ({@link DynamicPhext},
 * {@link TransitivePhext}, {@link BranchingMap}): seeded edit-sequence
 * equivalence against the original string path asserted after every
 * operation, plus the dynamic-table growth/thrash instrumentation
 * checks (CLRS problem 3-5).
 */
public final class StoreTests {
    private StoreTests() {
    }

    private static Coordinate coord(long chapter, long section, long scroll) {
        return new Coordinate(new ZCoordinate(1, 1, 1), new YCoordinate(1, 1, 1),
            new XCoordinate(chapter, section, scroll));
    }

    public static void register() {
        TestMain.test("stores", "equivalenceSeeded600Ops", StoreTests::equivalence);
        TestMain.test("stores", "thrashQuarterVsHalf", StoreTests::thrash);
        TestMain.test("stores", "growthAmortized", StoreTests::growth);
        TestMain.test("stores", "noShrinkAtHalfFull", StoreTests::noShrinkAtHalf);
        TestMain.test("stores", "branchingMapGenericity", StoreTests::genericity);
        TestMain.test("stores", "transitiveDepthDerived", StoreTests::depthDerived);
        TestMain.test("stores", "convenienceConstructors", StoreTests::constructors);
        TestMain.test("stores", "basicSemantics", StoreTests::basicSemantics);
    }

    /** 600 seeded ops over a 180-coordinate pool; after EVERY op both
     *  stores must serialize byte-identically to the normalized string
     *  path, and fetches must agree. */
    private static void equivalence() {
        Random rng = new Random(20261124L);
        List<Coordinate> pool = new java.util.ArrayList<>();
        for (long section = 1; section <= 6; section++) {
            for (long scroll = 1; scroll <= 30; scroll++) {
                pool.add(coord(1, section, scroll));
            }
        }
        String[] texts = {"alpha", "beta gamma", "delta\nline2", "x", "",
            "a longer scroll body with several words in it"};
        String buffer = "";
        DynamicPhext dyn = new DynamicPhext();
        TransitivePhext tr = new TransitivePhext();
        for (int op = 0; op < 600; op++) {
            Coordinate c = pool.get(rng.nextInt(pool.size()));
            String text = texts[rng.nextInt(texts.length)];
            int kind = rng.nextInt(3);
            if (kind == 0) {
                buffer = Phext.insert(buffer, c, text);
                dyn.insert(c, text);
                tr.insert(c, text);
            } else if (kind == 1) {
                buffer = Phext.replace(buffer, c, text);
                dyn.replace(c, text);
                tr.replace(c, text);
            } else {
                buffer = Phext.remove(buffer, c);
                dyn.remove(c);
                tr.remove(c);
            }
            String expected = Phext.normalize(buffer);
            A.eq(expected, dyn.serialize());
            A.eq(expected, tr.serialize());
            Coordinate probe = pool.get(rng.nextInt(pool.size()));
            String want = Phext.fetch(buffer, probe);
            A.eq(want, dyn.fetch(probe));
            A.eq(want, tr.fetch(probe));
        }
    }

    /** At a 64-scroll boundary over 50 alternating append/remove pairs,
     *  the quarter-full store copies 64 elements total; the half-full
     *  variant copies 6,400 — 100x (the textbook thrash). */
    private static void thrash() {
        DynamicPhext quarter = new DynamicPhext("", 4);
        DynamicPhext half = new DynamicPhext("", 2);
        for (int i = 0; i < 64; i++) {
            Coordinate c = coord(1, 1, i + 1);
            quarter.append(c, "s" + i);
            half.append(c, "s" + i);
        }
        quarter.resetStats();
        half.resetStats();
        Coordinate tail = coord(1, 1, 65);
        for (int i = 0; i < 50; i++) {
            quarter.append(tail, "tail");
            quarter.remove(tail);
            half.append(tail, "tail");
            half.remove(tail);
        }
        A.eq(64L, quarter.elementsCopied());
        A.eq(6400L, half.elementsCopied());
    }

    /** 1,000 appends: exactly 10 resizes, 1,023 elements copied (< 2N). */
    private static void growth() {
        DynamicPhext d = new DynamicPhext();
        for (int i = 0; i < 1000; i++) {
            d.append(coord(1, 1 + i / 50, 1 + i % 50), "scroll " + i);
        }
        A.eq(10L, d.resizeCount());
        A.eq(1023L, d.elementsCopied());
        A.eq(1000, d.size());
    }

    private static void noShrinkAtHalf() {
        DynamicPhext d = new DynamicPhext();
        for (int i = 0; i < 64; i++) {
            d.append(coord(1, 1, i + 1), "s" + i);
        }
        A.eq(64, d.capacity());
        for (int i = 64; i > 32; i--) {
            d.remove(coord(1, 1, i));
        }
        A.eq(64, d.capacity()); // half full: no shrink
        for (int i = 32; i > 16; i--) {
            d.remove(coord(1, 1, i));
        }
        A.eq(32, d.capacity()); // quarter full: halved
        DynamicPhext e = new DynamicPhext();
        e.append(coord(1, 1, 1), "only");
        e.remove(coord(1, 1, 1));
        A.eq(1, e.capacity()); // shrink floor is capacity 1
        A.eq(0, e.size());
    }

    private static void genericity() {
        BranchingMap<String> m3 = new BranchingMap<>(3);
        m3.put(new long[]{1, 2, 3}, "a");
        m3.put(new long[]{1, 2, 4}, "b");
        m3.put(new long[]{9, 1, 1}, "c");
        A.eq("a", m3.fetch(new long[]{1, 2, 3}));
        A.eq(null, m3.fetch(new long[]{1, 2, 9}));
        A.eq(3, m3.size());
        A.isTrue(m3.remove(new long[]{1, 2, 3}));
        A.eq(2, m3.size());
        A.eq("b", m3.items().get(0).getValue());
        BranchingMap<String> m1 = new BranchingMap<>(1);
        m1.put(new long[]{7}, "solo");
        A.eq("solo", m1.fetch(new long[]{7}));
        boolean threw = false;
        try {
            m3.fetch(new long[]{1, 2});
        } catch (IllegalArgumentException ex) {
            threw = true;
        }
        A.isTrue(threw);
    }

    private static void depthDerived() {
        A.eq(9, new TransitivePhext().depth());
        A.eq(9, new Coordinate().toComponents().length);
    }

    private static void constructors() {
        String buf = Phext.insert("", coord(1, 1, 1), "hello");
        DynamicPhext d = Phext.dynamicStore(buf);
        TransitivePhext t = Phext.transitiveStore(buf);
        A.eq("hello", d.fetch(coord(1, 1, 1)));
        A.eq("hello", t.fetch(coord(1, 1, 1)));
        A.eq(Phext.normalize(buf), d.serialize());
        A.eq(Phext.normalize(buf), t.serialize());
    }

    private static void basicSemantics() {
        for (int which = 0; which < 2; which++) {
            DynamicPhext d = which == 0 ? new DynamicPhext() : null;
            TransitivePhext t = which == 1 ? new TransitivePhext() : null;
            java.util.function.BiConsumer<Coordinate, String> ins =
                (c, s) -> { if (d != null) d.insert(c, s); else t.insert(c, s); };
            java.util.function.BiConsumer<Coordinate, String> rep =
                (c, s) -> { if (d != null) d.replace(c, s); else t.replace(c, s); };
            java.util.function.Function<Coordinate, String> fet =
                c -> d != null ? d.fetch(c) : t.fetch(c);
            Coordinate c = coord(1, 1, 1);
            ins.accept(c, "one");
            ins.accept(c, "+two"); // insert concatenates old before new
            A.eq("one+two", fet.apply(c));
            rep.accept(c, "three"); // replace overwrites
            A.eq("three", fet.apply(c));
            A.eq("", fet.apply(coord(1, 1, 2))); // miss returns ""
            rep.accept(c, ""); // replace with "" removes
            A.eq("", fet.apply(c));
            ins.accept(c, ""); // insert of "" is a no-op
            A.eq("", d != null ? d.serialize() : t.serialize());
        }
    }
}

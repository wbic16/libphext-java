package io.phext.libphext;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Port of libphext-rs {@code src/test_phokenize.rs}: property-style tests
 * driven by the same deterministic LCG generator and the same seeds, so a
 * failure reproduces exactly as in Rust. (The Rust LCG returns
 * {@code state >> 33} after a wrapping multiply-add; Java long arithmetic
 * wraps identically.)
 */
public final class PhokenizeTests {
    private PhokenizeTests() {
    }

    private static final char[] BREAKS = {
        '\u0017', '\u0018', '\u0019', '\u001A', '\u001C', '\u001D', '\u001E', '\u001F', '\u0001'
    };

    private static final class Lcg {
        private long state;

        Lcg(long seed) {
            this.state = seed;
        }

        long next() {
            state = state * 6364136223846793005L + 1442695040888963407L;
            return state >>> 33;
        }
    }

    /** The earlier phokenize: nextScroll in a loop. The definition of the expected output. */
    private static List<PositionedScroll> reference(String phext) {
        List<PositionedScroll> result = new ArrayList<>();
        Coordinate coord = Phext.defaultCoordinate();
        String temp = phext;
        while (true) {
            Phext.NextScrollResult r = Phext.nextScroll(temp, coord);
            result.add(r.scroll());
            coord = r.next();
            temp = r.remaining();
            if (temp.isEmpty()) {
                break;
            }
        }
        return result;
    }

    private static List<String> pairs(List<PositionedScroll> tokens) {
        List<String> out = new ArrayList<>();
        for (PositionedScroll t : tokens) {
            out.add(t.coord + "=" + t.scroll);
        }
        return out;
    }

    private static List<String> edgeCases() {
        return List.of(
            "",
            "one scroll\nwith two lines",
            "a\u0017b\u0018c\u0019d\u001Ae\u001Cf\u001Dg\u001Eh\u001Fi\u0001j",
            "\u0017\u0017lead\u0017\u0017\u0017gap\u0001\u0001\u0017deep\u0017",
            "trailing one\u0017",
            "trailing two\u0017\u0017",
            "\u0001\u001F\u001E\u001D\u001C\u001A\u0019\u0018\u0017",
            "ünïcödé ✓\u001Fshelf ☃\u001Aboek ü\u0017🜁",
            "\n\n\u0017\n"
        );
    }

    private static String randomDoc(Lcg rng) {
        String[] pieces = {"a", "bc", "ü", "☃", "🜁", "\n", " ", "text"};
        StringBuilder doc = new StringBuilder();
        long n = rng.next() % 60;
        for (long i = 0; i < n; i++) {
            if (rng.next() % 3 == 0) {
                long run = 1 + rng.next() % 3;
                for (long j = 0; j < run; j++) {
                    doc.append(BREAKS[(int) (rng.next() % 9)]);
                }
            } else {
                doc.append(pieces[(int) (rng.next() % pieces.length)]);
            }
        }
        return doc.toString();
    }

    private static List<Coordinate> subspaceTargets(String doc, Lcg rng) {
        List<Coordinate> targets = new ArrayList<>();
        for (PositionedScroll token : Phext.phokenize(doc)) {
            targets.add(token.coord);
            Coordinate after = token.coord.copy();
            after.scrollBreak();
            targets.add(after);
            Coordinate deeper = token.coord.copy();
            deeper.sectionBreak();
            targets.add(deeper);
        }
        for (int i = 0; i < 4; i++) {
            String address = String.format("%d.%d.%d/%d.%d.%d/%d.%d.%d",
                1 + rng.next() % 3, 1 + rng.next() % 3, 1 + rng.next() % 3,
                1 + rng.next() % 3, 1 + rng.next() % 3, 1 + rng.next() % 3,
                1 + rng.next() % 3, 1 + rng.next() % 3, 1 + rng.next() % 5);
            targets.add(Phext.toCoordinate(address));
        }
        return targets;
    }

    /** The non-empty scrolls of a document, as explode sees them. */
    private static Map<String, String> content(String doc) {
        Map<String, String> out = new TreeMap<>();
        for (Map.Entry<Coordinate, String> e : Phext.explode(doc).entrySet()) {
            if (!e.getValue().isEmpty()) {
                out.put(e.getKey().toString(), e.getValue());
            }
        }
        return out;
    }

    private static List<String> documents(long seed, int count) throws Exception {
        Lcg rng = new Lcg(seed);
        List<String> docs = new ArrayList<>(edgeCases());
        docs.add("a\u0018b\u0019c");
        docs.add("a\u0017b\u0017");
        docs.add("x\u0017");
        docs.add("\u0019\u001Aa");
        docs.add("a\u0018b\u0019c\u001Ad\u001Ce\u001Df\u001Eg\u001Fh\u0001i");
        docs.add("\u0001".repeat(1001) + "deep" + "\u001F".repeat(1001) + "deeper");
        for (int i = 0; i < count; i++) {
            docs.add(randomDoc(rng));
        }
        java.nio.file.Path helios = TestMain.repoRoot.resolve("regression-1-helios.phext");
        if (Files.exists(helios)) {
            docs.add(Files.readString(helios, StandardCharsets.UTF_8));
        }
        return docs;
    }

    public static void register() {
        TestMain.test("PhokenizeTests", "phokenize_matches_the_next_scroll_loop", () -> {
            for (String doc : edgeCases()) {
                A.eq(pairs(reference(doc)), pairs(Phext.phokenize(doc)), "doc " + A.show(doc));
            }
            Lcg rng = new Lcg(0x5eed);
            for (int i = 0; i < 5000; i++) {
                String doc = randomDoc(rng);
                A.eq(pairs(reference(doc)), pairs(Phext.phokenize(doc)), "doc " + A.show(doc));
            }
        });

        TestMain.test("PhokenizeTests", "edits_act_on_exactly_the_target_scroll", () -> {
            Lcg rng = new Lcg(0xc0de);
            for (String doc : documents(0xc0de, 1500)) {
                Map<String, String> before = content(doc);
                for (Coordinate target : subspaceTargets(doc, rng)) {
                    String key = target.toString();
                    String current = before.getOrDefault(key, "");
                    A.eq(current, Phext.fetch(doc, target), "fetch at " + key);

                    Map<String, String> want = new TreeMap<>(before);
                    want.put(key, "X");
                    A.eq(want, content(Phext.replace(doc, target, "X")), "replace at " + key);

                    want = new TreeMap<>(before);
                    want.put(key, current + "Y");
                    A.eq(want, content(Phext.insert(doc, target, "Y")), "insert at " + key);

                    want = new TreeMap<>(before);
                    want.remove(key);
                    A.eq(want, content(Phext.remove(doc, target)), "remove at " + key);

                    Phext.offset(doc, target); // must not throw
                }
            }
        });

        TestMain.test("PhokenizeTests", "missing_coordinates_between_scrolls_are_left_alone", () -> {
            String doc = "a\u0018b\u0019c";
            Coordinate between = Phext.toCoordinate("1.1.1/1.1.1/1.1.2");
            A.eq("", Phext.fetch(doc, between));
            A.eq("a\u0017X\u0018b\u0019c", Phext.replace(doc, between, "X"));
            A.eq("a\u0017Y\u0018b\u0019c", Phext.insert(doc, between, "Y"));
            A.eq(doc, Phext.remove(doc, between));
            Coordinate after = Phext.toCoordinate("1.1.1/1.1.1/1.1.2");
            A.eq("x\u0017Y", Phext.insert("x\u0017", after, "Y"));
            A.eq("b", Phext.fetch("a\u0017b\u0017", after));
        });

        TestMain.test("PhokenizeTests", "subtract_removes_only_scrolls_with_content_on_the_right", () -> {
            List<String> docs = documents(0x5b7, 1000);
            for (int index = 0; index < docs.size(); index++) {
                String left = docs.get(index);
                String right = docs.get((index + 1) % docs.size());
                Map<String, String> mask = content(right);
                Map<String, String> want = new TreeMap<>();
                for (Map.Entry<String, String> e : content(left).entrySet()) {
                    if (!mask.containsKey(e.getKey())) {
                        want.put(e.getKey(), e.getValue());
                    }
                }
                A.eq(want, content(Phext.subtract(left, right)), "subtract doc " + index);
            }
            A.eq("\u0017\u0017lead", Phext.subtract("\u0017\u0017lead", "a\u0018b"));
        });

        TestMain.test("PhokenizeTests", "offset_does_not_panic_before_the_first_scroll", () -> {
            A.eq(0, Phext.offset("\u0019\u001Aa", Phext.toCoordinate("1.1.1/1.1.1/1.1.1")));
            A.eq(0, Phext.offset("", Phext.toCoordinate("2.2.2/2.2.2/2.2.2")));
            A.eq(3, Phext.offset("ab\u0017cd", Phext.toCoordinate("1.1.1/1.1.1/1.1.2")));
            A.eq(3, Phext.offset("ab\u0017cd", Phext.toCoordinate("1.1.1/1.1.1/1.1.9")));
        });

        TestMain.test("PhokenizeTests", "summaries_keep_multibyte_characters_whole", () -> {
            A.eq("Ȋbc", Phext.createSummary("Ȋbc"));
            A.eq("日本...", Phext.createSummary("日本\u0017rest"));
            A.eq("one...", Phext.createSummary("one\ntwo"));
        });

        TestMain.test("PhokenizeTests", "explode_and_implode_roundtrip_the_same_scrolls", () -> {
            Lcg rng = new Lcg(0xfab1e);
            for (int i = 0; i < 2000; i++) {
                String doc = randomDoc(rng);
                Map<Coordinate, String> map = Phext.explode(doc);
                String rebuilt = Phext.implode(new java.util.HashMap<>(map));
                Map<Coordinate, String> again = Phext.explode(rebuilt);
                A.eq(filled(map), filled(again), "doc " + A.show(doc));
            }
        });

        TestMain.test("PhokenizeTests", "a_large_phext_explodes_in_linear_time", () -> {
            StringBuilder doc = new StringBuilder();
            for (int index = 0; index < 50_000; index++) {
                if (index > 0) {
                    doc.append(index % 1600 == 0 ? '\u001C' : index % 40 == 0 ? '\u001A' : '\u0017');
                }
                doc.append("kind: message\nauthor: bench\n---\nsome words about the topic here");
            }
            String d = doc.toString();
            long start = System.nanoTime();
            Map<Coordinate, String> map = Phext.explode(d);
            String rebuilt = Phext.implode(new java.util.HashMap<>(map));
            String toc = Phext.textmap(d);
            long millis = (System.nanoTime() - start) / 1_000_000;
            A.eq(50_000, map.size());
            A.eq(d, rebuilt);
            A.eq(50_000, toc.lines().count());
            A.isTrue(millis < 2000, "took " + millis + " ms");
        });
    }

    private static List<String> filled(Map<Coordinate, String> m) {
        List<String> v = new ArrayList<>();
        for (Map.Entry<Coordinate, String> e : m.entrySet()) {
            if (!e.getValue().isEmpty()) {
                v.add(e.getKey() + "=" + e.getValue());
            }
        }
        v.sort(null);
        return v;
    }
}

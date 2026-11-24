package io.phext.libphext;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** v0.7.0 benchmark, string path only (compiles against pristine 0.6.1).
 *  Workloads per DISCIPLINE.md: A 2,000 inserts; B 500 tail insert/remove
 *  pairs on a 256-scroll base; C 5,000 seeded fetches. Corpus and targets
 *  are read from the shared files in ~/v070-java-leg/. */
public final class BenchString {
    static List<String> corpus;
    static List<Coordinate> targets;

    static String unescape(String s) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char n = s.charAt(++i);
                out.append(n == 'n' ? '\n' : n == 't' ? '\t' : n == 'r' ? '\r' : n);
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    static Coordinate coord(long lib, long shelf, long series, long coll, long vol,
                            long book, long chapter, long section, long scroll) {
        return new Coordinate(new ZCoordinate(lib, shelf, series),
            new YCoordinate(coll, vol, book), new XCoordinate(chapter, section, scroll));
    }

    static Coordinate coordA(int i) {
        return coord(1, 1, 1, 1, 1, 1, 1, (i / 50) % 40 + 1, i % 50 + 1);
    }

    static String text(int i) {
        return corpus.get(i % corpus.size());
    }

    public static void main(String[] args) throws Exception {
        Path dir = Path.of(args[0]);
        corpus = new ArrayList<>();
        for (String line : Files.readAllLines(dir.resolve("corpus.tsv"))) {
            corpus.add(unescape(line));
        }
        targets = new ArrayList<>();
        for (String line : Files.readAllLines(dir.resolve("targets.tsv"))) {
            String[] p = line.split(",");
            long[] v = new long[9];
            for (int i = 0; i < 9; i++) v[i] = Long.parseLong(p[i]);
            targets.add(coord(v[0], v[1], v[2], v[3], v[4], v[5], v[6], v[7], v[8]));
        }
        System.out.printf("corpus: %d texts%n", corpus.size());

        // Workload A: 2,000 sequential inserts (string path)
        long t0 = System.nanoTime();
        String buffer = "";
        for (int i = 0; i < 2000; i++) {
            buffer = Phext.insert(buffer, coordA(i), text(i));
        }
        double aSec = (System.nanoTime() - t0) / 1e9;
        System.out.printf("A string: %.3f s (final buffer %d chars)%n", aSec, buffer.length());
        Files.writeString(dir.resolve("bench-a-buffer.txt"), buffer);

        // Workload B: 256-scroll base, 500 tail insert/remove pairs
        String base = "";
        for (int i = 0; i < 256; i++) {
            base = Phext.insert(base, coordA(i), text(i));
        }
        Coordinate tail = coord(1, 1, 1, 1, 1, 1, 2, 1, 1);
        String tailText = "tail boundary scroll\nsecond line\n";
        t0 = System.nanoTime();
        String b = base;
        for (int i = 0; i < 500; i++) {
            b = Phext.insert(b, tail, tailText);
            b = Phext.remove(b, tail);
        }
        double bSec = (System.nanoTime() - t0) / 1e9;
        System.out.printf("B string: %.3f s (end==base: %s)%n", bSec, b.equals(base));

        // Workload C: 5,000 fetches on workload-A end state
        List<String> results = new ArrayList<>();
        t0 = System.nanoTime();
        for (Coordinate c : targets) {
            results.add(Phext.fetch(buffer, c));
        }
        double cSec = (System.nanoTime() - t0) / 1e9;
        System.out.printf("C string: %.3f s (fetches %d)%n", cSec, results.size());
        System.out.printf("C results hash: %d%n", results.hashCode());
    }
}

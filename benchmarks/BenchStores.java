package io.phext.libphext;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** v0.7.0 benchmark, AFTER tree: string path + DynamicPhext +
 *  TransitivePhext on the identical workloads (see BenchString).
 *  Asserts byte-identical serializations and fetch results. */
public final class BenchStores {
    public static void main(String[] args) throws Exception {
        Path dir = Path.of(args[0]);
        BenchString.corpus = new ArrayList<>();
        for (String line : Files.readAllLines(dir.resolve("corpus.tsv"))) {
            BenchString.corpus.add(BenchString.unescape(line));
        }
        List<Coordinate> targets = new ArrayList<>();
        for (String line : Files.readAllLines(dir.resolve("targets.tsv"))) {
            String[] p = line.split(",");
            long[] v = new long[9];
            for (int i = 0; i < 9; i++) v[i] = Long.parseLong(p[i]);
            targets.add(BenchString.coord(v[0], v[1], v[2], v[3], v[4], v[5], v[6], v[7], v[8]));
        }
        System.out.printf("corpus: %d texts%n", BenchString.corpus.size());

        // ---- Workload A ----
        long t0 = System.nanoTime();
        String buffer = "";
        for (int i = 0; i < 2000; i++) {
            buffer = Phext.insert(buffer, BenchString.coordA(i), BenchString.text(i));
        }
        System.out.printf("A string: %.3f s (final buffer %d chars)%n",
            (System.nanoTime() - t0) / 1e9, buffer.length());

        DynamicPhext dyn = new DynamicPhext();
        t0 = System.nanoTime();
        for (int i = 0; i < 2000; i++) {
            dyn.insert(BenchString.coordA(i), BenchString.text(i));
        }
        double dynEdits = (System.nanoTime() - t0) / 1e9;
        t0 = System.nanoTime();
        String dynSer = dyn.serialize();
        double dynSerT = (System.nanoTime() - t0) / 1e9;
        System.out.printf("A dynamic: edits %.3f s + serialize %.3f s = %.3f s (== string: %s; resizes %d, copied %d)%n",
            dynEdits, dynSerT, dynEdits + dynSerT, dynSer.equals(buffer),
            dyn.resizeCount(), dyn.elementsCopied());

        TransitivePhext tr = new TransitivePhext();
        t0 = System.nanoTime();
        for (int i = 0; i < 2000; i++) {
            tr.insert(BenchString.coordA(i), BenchString.text(i));
        }
        double trEdits = (System.nanoTime() - t0) / 1e9;
        t0 = System.nanoTime();
        String trSer = tr.serialize();
        double trSerT = (System.nanoTime() - t0) / 1e9;
        System.out.printf("A transitive: edits %.3f s + serialize %.3f s = %.3f s (== string: %s)%n",
            trEdits, trSerT, trEdits + trSerT, trSer.equals(buffer));

        // ---- Workload B ----
        String base = "";
        for (int i = 0; i < 256; i++) {
            base = Phext.insert(base, BenchString.coordA(i), BenchString.text(i));
        }
        Coordinate tail = BenchString.coord(1, 1, 1, 1, 1, 1, 2, 1, 1);
        String tailText = "tail boundary scroll\nsecond line\n";
        t0 = System.nanoTime();
        String b = base;
        for (int i = 0; i < 500; i++) {
            b = Phext.insert(b, tail, tailText);
            b = Phext.remove(b, tail);
        }
        System.out.printf("B string: %.3f s (end==base: %s)%n", (System.nanoTime() - t0) / 1e9, b.equals(base));

        DynamicPhext dynB = new DynamicPhext(base);
        t0 = System.nanoTime();
        for (int i = 0; i < 500; i++) {
            dynB.insert(tail, tailText);
            dynB.remove(tail);
        }
        System.out.printf("B dynamic: %.3f s (end==base: %s)%n",
            (System.nanoTime() - t0) / 1e9, dynB.serialize().equals(base));

        TransitivePhext trB = new TransitivePhext(base);
        t0 = System.nanoTime();
        for (int i = 0; i < 500; i++) {
            trB.insert(tail, tailText);
            trB.remove(tail);
        }
        System.out.printf("B transitive: %.3f s (end==base: %s)%n",
            (System.nanoTime() - t0) / 1e9, trB.serialize().equals(base));

        // ---- Workload C ----
        List<String> stringResults = new ArrayList<>();
        t0 = System.nanoTime();
        for (Coordinate c : targets) {
            stringResults.add(Phext.fetch(buffer, c));
        }
        System.out.printf("C string: %.3f s%n", (System.nanoTime() - t0) / 1e9);

        t0 = System.nanoTime();
        int dynMismatch = 0;
        for (int i = 0; i < targets.size(); i++) {
            if (!dyn.fetch(targets.get(i)).equals(stringResults.get(i))) dynMismatch++;
        }
        System.out.printf("C dynamic: %.3f s (mismatches %d)%n", (System.nanoTime() - t0) / 1e9, dynMismatch);

        t0 = System.nanoTime();
        int trMismatch = 0;
        for (int i = 0; i < targets.size(); i++) {
            if (!tr.fetch(targets.get(i)).equals(stringResults.get(i))) trMismatch++;
        }
        System.out.printf("C transitive: %.3f s (mismatches %d)%n", (System.nanoTime() - t0) / 1e9, trMismatch);
    }
}

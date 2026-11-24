package io.phext.libphext;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Plain main-driven test harness (no test-framework dependency; the build
 * is plain javac). Each suite registers named tests; a test fails by
 * throwing (typically via {@link A}). Exit code is non-zero on any failure.
 *
 * <p>Usage: {@code TestMain <repo-root>} — the root is used to locate
 * {@code tests/fixtures/} and the regression corpus files.
 */
public final class TestMain {
    public static Path repoRoot;

    private record TestCase(String suite, String name, ThrowingRunnable body) {
    }

    @FunctionalInterface
    public interface ThrowingRunnable {
        void run() throws Exception;
    }

    private static final List<TestCase> TESTS = new ArrayList<>();

    public static void test(String suite, String name, ThrowingRunnable body) {
        TESTS.add(new TestCase(suite, name, body));
    }

    public static void main(String[] args) {
        repoRoot = Path.of(args.length > 0 ? args[0] : ".");
        PhextTests.register();
        PhextTests.registerPart2();
        PhokenizeTests.register();
        BinaryTests.register();
        SealTests.register();
        ChainTests.register();
        ChainFixtureTests.register();
        AddressingTests.register();
        RangeFixtureTests.register();
        StoreTests.register();

        int passed = 0;
        List<String> failures = new ArrayList<>();
        for (TestCase t : TESTS) {
            A.resetCount();
            try {
                t.body().run();
                passed++;
                System.out.printf("PASS %s::%s (%d assertions)%n", t.suite(), t.name(), A.count());
            } catch (Throwable e) {
                failures.add(t.suite() + "::" + t.name() + " -> " + e);
                System.out.printf("FAIL %s::%s -> %s%n", t.suite(), t.name(), e);
            }
        }
        System.out.printf("%n%d tests run, %d passed, %d failed, %d assertions total%n",
            TESTS.size(), passed, failures.size(), A.total());
        if (!failures.isEmpty()) {
            System.out.println("FAILURES:");
            for (String f : failures) {
                System.out.println("  " + f);
            }
            System.exit(1);
        }
    }
}

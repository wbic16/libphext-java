package io.phext.libphext;

import java.util.Arrays;
import java.util.Objects;

/** Assertion helpers for the harness: every check counts, every failure throws. */
public final class A {
    private A() {
    }

    private static int count;
    private static int total;

    static void resetCount() {
        count = 0;
    }

    static int count() {
        return count;
    }

    static int total() {
        return total;
    }

    private static void bump() {
        count++;
        total++;
    }

    public static void eq(Object expected, Object actual) {
        eq(expected, actual, "");
    }

    public static void eq(Object expected, Object actual, String context) {
        bump();
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError("expected " + show(expected) + " but was " + show(actual)
                + (context.isEmpty() ? "" : " [" + context + "]"));
        }
    }

    public static void eq(long expected, long actual) {
        eq(expected, actual, "");
    }

    public static void eq(long expected, long actual, String context) {
        bump();
        if (expected != actual) {
            throw new AssertionError("expected " + expected + " but was " + actual
                + (context.isEmpty() ? "" : " [" + context + "]"));
        }
    }

    public static void eqBytes(byte[] expected, byte[] actual) {
        eqBytes(expected, actual, "");
    }

    public static void eqBytes(byte[] expected, byte[] actual, String context) {
        bump();
        if (!Arrays.equals(expected, actual)) {
            throw new AssertionError("byte arrays differ: expected " + expected.length
                + " bytes, was " + (actual == null ? "null" : actual.length + " bytes")
                + (context.isEmpty() ? "" : " [" + context + "]"));
        }
    }

    public static void isTrue(boolean value) {
        isTrue(value, "");
    }

    public static void isTrue(boolean value, String context) {
        bump();
        if (!value) {
            throw new AssertionError("expected true" + (context.isEmpty() ? "" : " [" + context + "]"));
        }
    }

    public static void isFalse(boolean value) {
        isFalse(value, "");
    }

    public static void isFalse(boolean value, String context) {
        bump();
        if (value) {
            throw new AssertionError("expected false" + (context.isEmpty() ? "" : " [" + context + "]"));
        }
    }

    public static void isNull(Object value, String context) {
        bump();
        if (value != null) {
            throw new AssertionError("expected null but was " + show(value) + " [" + context + "]");
        }
    }

    public static void notNull(Object value, String context) {
        bump();
        if (value == null) {
            throw new AssertionError("expected non-null [" + context + "]");
        }
    }

    /** Escapes control characters so phext strings print legibly in failure messages. */
    static String show(Object o) {
        if (o == null) {
            return "null";
        }
        String s = String.valueOf(o);
        StringBuilder out = new StringBuilder("\"");
        for (int i = 0; i < s.length() && i < 200; i++) {
            char c = s.charAt(i);
            if (c < 0x20 || c == 0x7F) {
                out.append(String.format("\\x%02X", (int) c));
            } else {
                out.append(c);
            }
        }
        if (s.length() > 200) {
            out.append("...(").append(s.length()).append(" chars)");
        }
        return out.append('"').toString();
    }
}

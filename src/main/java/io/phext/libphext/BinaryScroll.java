package io.phext.libphext;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Binary scrolls: the canonical way to store arbitrary bytes inside phext.
 *
 * <p>Phext scrolls are text (valid UTF-8). Arbitrary binary data rides as
 * text via base64. This class defines the one canonical binary scroll
 * format, so that every phext implementation stores and recognises binary
 * payloads byte-for-byte identically:
 *
 * <pre>
 * phext-binary:1:base64
 * &lt;base64 body&gt;
 * </pre>
 *
 * <p>Format specification (version 1):
 * <ul>
 *   <li>Line 1 is exactly {@code phext-binary:1:base64}, followed by a
 *       single line break (0x0A).</li>
 *   <li>The body is RFC 4648 base64: standard alphabet, padding required,
 *       no line wrapping, no whitespace.</li>
 *   <li>An empty payload is the header line alone (header + line break).</li>
 *   <li>A scroll whose content begins with the header line is a binary
 *       scroll by definition. A prefix-matching scroll whose body is not
 *       strict base64 is a <em>malformed</em> binary scroll.</li>
 * </ul>
 *
 * <p>The ingestion rule (canonical behaviour, all ports): when non-UTF-8
 * data is observed in an incoming scroll update, we encode it with base64
 * verbatim. Byte-oriented entry points ({@link #insertBytes},
 * {@link #replaceBytes}, {@link #ingest}) never reject and never throw on
 * non-UTF-8 scroll data.
 *
 * <p>Mirrors libphext-rs {@code src/binary.rs} (feature {@code binary}).
 */
public final class BinaryScroll {
    private BinaryScroll() {
    }

    /** The first (and only header) line of a binary scroll, without its line break. */
    public static final String BINARY_SCROLL_HEADER = "phext-binary:1:base64";

    /** Header line plus its terminating line break: the prefix of every binary scroll. */
    private static final String BINARY_SCROLL_PREFIX = "phext-binary:1:base64\n";

    /**
     * True when {@code scroll} carries the binary scroll header. Decoding
     * success is a separate question — see {@link #decodeScroll}.
     */
    public static boolean isBinaryScroll(String scroll) {
        return scroll.startsWith(BINARY_SCROLL_PREFIX);
    }

    /** Encodes raw bytes as a binary scroll (header line + base64 body). */
    public static String encodeScroll(byte[] payload) {
        return BINARY_SCROLL_PREFIX + java.util.Base64.getEncoder().encodeToString(payload);
    }

    /**
     * Decodes a binary scroll back to its raw bytes. Returns {@code null}
     * when the scroll is not a binary scroll, or when its body is not
     * strict base64 (Rust's {@code Option}).
     *
     * <p>Strictness matches the Rust base64 STANDARD engine: standard
     * alphabet only, padding required, no whitespace, canonical trailing
     * bits (enforced by re-encoding and comparing).
     */
    public static byte[] decodeScroll(String scroll) {
        if (!scroll.startsWith(BINARY_SCROLL_PREFIX)) {
            return null;
        }
        String body = scroll.substring(BINARY_SCROLL_PREFIX.length());
        if (!isStrictBase64(body)) {
            return null;
        }
        try {
            byte[] decoded = java.util.Base64.getDecoder().decode(body);
            if (!java.util.Base64.getEncoder().encodeToString(decoded).equals(body)) {
                return null;
            }
            return decoded;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static boolean isStrictBase64(String body) {
        if (body.length() % 4 != 0) {
            return false;
        }
        int padding = 0;
        for (int i = body.length() - 1; i >= 0 && body.charAt(i) == '='; i--) {
            padding++;
        }
        if (padding > 2) {
            return false;
        }
        for (int i = 0; i < body.length() - padding; i++) {
            char c = body.charAt(i);
            boolean ok = (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')
                || (c >= '0' && c <= '9') || c == '+' || c == '/';
            if (!ok) {
                return false;
            }
        }
        return true;
    }

    /**
     * Stores {@code payload} at {@code location} as a binary scroll,
     * unconditionally: the caller has declared this scroll binary, even if
     * the bytes happen to be valid UTF-8.
     */
    public static String insertBinary(String input, Coordinate location, byte[] payload) {
        return Phext.insert(input, location, encodeScroll(payload));
    }

    /** Replaces the scroll at {@code location} with {@code payload}, stored as a binary scroll. */
    public static String replaceBinary(String input, Coordinate location, byte[] payload) {
        return Phext.replace(input, location, encodeScroll(payload));
    }

    /**
     * Retrieves the bytes held at {@code target}. A well-formed binary
     * scroll decodes to its payload. Any other scroll — plain text, a
     * malformed binary scroll, or no scroll at all — yields the scroll's
     * own UTF-8 bytes (empty when the coordinate is unoccupied). This
     * function is total: it never throws and never loses scroll content.
     */
    public static byte[] fetchBinary(String input, Coordinate target) {
        String scroll = Phext.fetch(input, target);
        byte[] decoded = decodeScroll(scroll);
        if (decoded != null) {
            return decoded;
        }
        return scroll.getBytes(StandardCharsets.UTF_8);
    }

    /** Strict UTF-8 decode; returns null when the bytes are not valid UTF-8. */
    private static String decodeUtf8Strict(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString();
        } catch (CharacterCodingException e) {
            return null;
        }
    }

    /**
     * The stored form of an incoming scroll update, per the ingestion rule:
     * valid UTF-8 stays text; anything else becomes a binary scroll holding
     * the base64 of the exact observed bytes, verbatim.
     */
    private static String storedForm(byte[] payload) {
        String text = decodeUtf8Strict(payload);
        return text != null ? text : encodeScroll(payload);
    }

    /**
     * Byte-oriented insert: stores {@code payload} at {@code location}
     * under the ingestion rule (UTF-8 as text, non-UTF-8 as a
     * base64-verbatim binary scroll).
     */
    public static String insertBytes(String input, Coordinate location, byte[] payload) {
        return Phext.insert(input, location, storedForm(payload));
    }

    /** Byte-oriented replace: see {@link #insertBytes}. */
    public static String replaceBytes(String input, Coordinate location, byte[] payload) {
        return Phext.replace(input, location, storedForm(payload));
    }

    /**
     * The byte-level twin of {@link Phext#phokenize}: walks a raw byte
     * stream and returns each scroll's coordinate and raw bytes, with
     * identical segmentation. Scroll bodies are returned uninterpreted,
     * valid UTF-8 or not.
     */
    public static List<PositionedByteScroll> phokenizeBytes(byte[] input) {
        List<PositionedByteScroll> result = new ArrayList<>();
        int max = input.length;
        Coordinate coord = Phext.defaultCoordinate();
        int pi = 0;
        while (true) {
            Coordinate begin = coord.copy();
            Integer textStart = null;
            int textEnd = max;
            while (pi < max) {
                byte b = input[pi];
                if (Phext.isDimensionBreak(b)) {
                    Phext.applyBreak(coord, b);
                    if (textStart != null) {
                        textEnd = pi;
                        pi += 1;
                        break;
                    }
                } else if (textStart == null) {
                    textStart = pi;
                    begin = coord.copy();
                }
                pi += 1;
            }
            byte[] scroll;
            if (textStart != null) {
                scroll = new byte[textEnd - textStart];
                System.arraycopy(input, textStart, scroll, 0, scroll.length);
            } else {
                scroll = new byte[0];
            }
            result.add(new PositionedByteScroll(begin, scroll));
            if (pi >= max) {
                break;
            }
        }
        return result;
    }

    /**
     * Ingests an externally supplied phext byte stream. Every scroll is
     * observed as raw bytes: valid UTF-8 scrolls pass through verbatim, and
     * any scroll whose bytes are not valid UTF-8 is stored as a binary
     * scroll holding the base64 of the exact observed bytes (the ingestion
     * rule), so {@link #fetchBinary} at its coordinate returns the original
     * bytes exactly. Never rejects, never throws. The result is the
     * canonical serialization (the same normal form as
     * {@code Phext.implode(Phext.explode(...))}).
     */
    public static String ingest(byte[] bytes) {
        List<PositionedByteScroll> parts = phokenizeBytes(bytes);
        Map<Coordinate, String> map = new HashMap<>();
        for (PositionedByteScroll part : parts) {
            String text = decodeUtf8Strict(part.scroll);
            if (text == null) {
                text = encodeScroll(part.scroll);
            }
            map.put(part.coord, text);
        }
        return Phext.implode(map);
    }

    // kept for parity with the Rust module's internal byte plumbing
    static byte[] concat(byte[] a, byte[] b) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(a, 0, a.length);
        out.write(b, 0, b.length);
        return out.toByteArray();
    }
}

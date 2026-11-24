package io.phext.libphext;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The phext core: scrolls inside a single phext, plus the 0.6.0 chain layer.
 *
 * <p>A direct port of libphext-rs {@code src/phext.rs} (0.6.0). Where Rust
 * works on the UTF-8 bytes of a {@code &str}, this port works on the UTF-8
 * bytes of a Java {@link String}; every delimiter is an ASCII byte below
 * 0x20 and UTF-8 multi-byte sequences never contain bytes below 0x80, so
 * byte offsets always fall on character boundaries, exactly as in Rust.
 */
public final class Phext {
    private Phext() {
    }

    // ------------------------------------------------------------------
    // constants (phext.rs)
    // ------------------------------------------------------------------

    /** Human numbering — we start at 1, not 0. */
    public static final long COORDINATE_MINIMUM = 1;

    public static final char LIBRARY_BREAK = '\u0001';    // 11th dimension
    public static final char CHAIN_BREAK = '\u0002';      // stream level: separates whole phexts within a chain
    public static final char CHAIN_CLOSE = '\u0003';      // stream level: terminates a chain
    public static final char MORE_COWBELL = '\u0007';
    public static final char LINE_BREAK = '\n';
    public static final char SCROLL_BREAK = '\u0017';     // 3D break
    public static final char SECTION_BREAK = '\u0018';    // 4D break
    public static final char CHAPTER_BREAK = '\u0019';    // 5D break
    public static final char BOOK_BREAK = '\u001A';       // 6D break
    public static final char VOLUME_BREAK = '\u001C';     // 7D break
    public static final char COLLECTION_BREAK = '\u001D'; // 8D break
    public static final char SERIES_BREAK = '\u001E';     // 9D break
    public static final char SHELF_BREAK = '\u001F';      // 10D break

    public static final byte LIBRARY_BREAK_U8 = 0x01;
    public static final byte CHAIN_BREAK_U8 = 0x02;
    public static final byte CHAIN_CLOSE_U8 = 0x03;
    public static final byte MORE_COWBELL_U8 = 0x07;
    public static final byte LINE_BREAK_U8 = 0x0A;
    public static final byte SCROLL_BREAK_U8 = 0x17;
    public static final byte SECTION_BREAK_U8 = 0x18;
    public static final byte CHAPTER_BREAK_U8 = 0x19;
    public static final byte BOOK_BREAK_U8 = 0x1A;
    public static final byte VOLUME_BREAK_U8 = 0x1C;
    public static final byte COLLECTION_BREAK_U8 = 0x1D;
    public static final byte SERIES_BREAK_U8 = 0x1E;
    public static final byte SHELF_BREAK_U8 = 0x1F;

    public static final byte ADDRESS_MICRO_BREAK = '.';
    public static final byte ADDRESS_MACRO_BREAK = '/';
    public static final byte ADDRESS_MACRO_ALT = ';';

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private static byte[] utf8(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    private static String fromUtf8(byte[] bytes) {
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static String fromUtf8(byte[] bytes, int offset, int length) {
        return new String(bytes, offset, length, StandardCharsets.UTF_8);
    }

    /**
     * Manual equivalent of Rust {@code str::split(char)}, matching its
     * semantics exactly (empty segments and a trailing empty tail are
     * preserved). {@code delim} is an ASCII control character, so it can be
     * matched on UTF-16 code units directly.
     */
    static List<String> splitOn(String s, char delim) {
        List<String> out = new ArrayList<>();
        int start = 0;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == delim) {
                out.add(s.substring(start, i));
                start = i + 1;
            }
        }
        out.add(s.substring(start));
        return out;
    }

    public static Coordinate defaultCoordinate() {
        return new Coordinate();
    }

    // ------------------------------------------------------------------
    // coordinate parsing (the lenient dead-reckoning parser)
    // ------------------------------------------------------------------

    /**
     * Translates a phext address string to a strongly-typed coordinate,
     * mirroring Rust {@code to_coordinate}: digits accumulate (saturating at
     * {@link Long#MAX_VALUE} on overflow, where Rust saturates at u64::MAX),
     * '.', '/' and ';' advance to the next component, and all other bytes
     * are ignored.
     */
    public static Coordinate toCoordinate(String address) {
        Coordinate result = defaultCoordinate();
        byte[] bytes = utf8(address);
        int index = 0;
        long value = 0;
        for (byte raw : bytes) {
            int b = raw & 0xFF;
            if (b == ADDRESS_MICRO_BREAK || b == ADDRESS_MACRO_BREAK || b == ADDRESS_MACRO_ALT) {
                switch (index) {
                    case 1 -> { result.z.library = value; index += 1; }
                    case 2 -> { result.z.shelf = value; index += 1; }
                    case 3 -> { result.z.series = value; index += 1; }
                    case 4 -> { result.y.collection = value; index += 1; }
                    case 5 -> { result.y.volume = value; index += 1; }
                    case 6 -> { result.y.book = value; index += 1; }
                    case 7 -> { result.x.chapter = value; index += 1; }
                    case 8 -> { result.x.section = value; index += 1; }
                    default -> { }
                }
                value = 0;
            }
            if (b >= 0x30 && b <= 0x39) {
                int digit = b - 0x30;
                if (value > (Long.MAX_VALUE - digit) / 10) {
                    value = Long.MAX_VALUE;
                } else {
                    value = value * 10 + digit;
                }
                if (index == 0) {
                    index = 1;
                }
            }
        }
        if (index > 0) {
            result.x.scroll = value;
        }
        return result;
    }

    public static boolean checkForCowbell(String input) {
        for (byte b : utf8(input)) {
            if (b == MORE_COWBELL_U8) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------
    // subspace coordinates
    // ------------------------------------------------------------------

    /** The result of {@link #getSubspaceCoordinates}: (start, end, best). */
    public record SubspaceCoordinates(int start, int end, Coordinate best) {
    }

    /**
     * Finds the start and end offsets for the given coordinate.
     *
     * <ul>
     *   <li>when the document reaches {@code target}, start..end is that
     *       scroll's text (empty if it has none) and best is target;</li>
     *   <li>otherwise start == end is where the scroll would go: just before
     *       the break that steps past target, or the end of the document.
     *       best is the coordinate in effect there.</li>
     * </ul>
     */
    public static SubspaceCoordinates getSubspaceCoordinates(byte[] subspace, Coordinate target) {
        Coordinate walker = defaultCoordinate();
        Coordinate best = defaultCoordinate();
        Integer found = null;
        int max = subspace.length;
        int position = 0;
        boolean moved = true;

        while (true) {
            if (moved && found == null) {
                int cmp = walker.compareTo(target);
                if (cmp == 0) {
                    found = position;
                    best = walker.copy();
                } else if (cmp < 0) {
                    best = walker.copy();
                } else {
                    // before the first scroll: target sorts ahead of 1.1.1/1.1.1/1.1.1
                    return new SubspaceCoordinates(position, position, best);
                }
                moved = false;
            }
            if (position == max) {
                break;
            }
            byte next = subspace[position];
            if (isDimensionBreak(next)) {
                applyBreak(walker, next);
                moved = true;
                if (walker.compareTo(target) > 0) {
                    if (found != null) {
                        return new SubspaceCoordinates(found, position, best);
                    }
                    return new SubspaceCoordinates(position, position, best);
                }
                if (found != null && found == position) {
                    found = position + 1;
                }
            }
            position += 1;
        }

        if (found != null) {
            return new SubspaceCoordinates(found, max, best);
        }
        return new SubspaceCoordinates(max, max, best);
    }

    // ------------------------------------------------------------------
    // breaks
    // ------------------------------------------------------------------

    /** True for the nine delimiters that move to a new scroll (line breaks stay inside a scroll). */
    public static boolean isDimensionBreak(byte b) {
        return b == SCROLL_BREAK_U8
            || b == SECTION_BREAK_U8
            || b == CHAPTER_BREAK_U8
            || b == BOOK_BREAK_U8
            || b == VOLUME_BREAK_U8
            || b == COLLECTION_BREAK_U8
            || b == SERIES_BREAK_U8
            || b == SHELF_BREAK_U8
            || b == LIBRARY_BREAK_U8;
    }

    /** True if the byte is one of the phext delimiters (line breaks included). */
    public static boolean isPhextBreak(byte b) {
        return b == LINE_BREAK_U8 || isDimensionBreak(b);
    }

    static void applyBreak(Coordinate location, byte b) {
        if (b == SCROLL_BREAK_U8) location.scrollBreak();
        if (b == SECTION_BREAK_U8) location.sectionBreak();
        if (b == CHAPTER_BREAK_U8) location.chapterBreak();
        if (b == BOOK_BREAK_U8) location.bookBreak();
        if (b == VOLUME_BREAK_U8) location.volumeBreak();
        if (b == COLLECTION_BREAK_U8) location.collectionBreak();
        if (b == SERIES_BREAK_U8) location.seriesBreak();
        if (b == SHELF_BREAK_U8) location.shelfBreak();
        if (b == LIBRARY_BREAK_U8) location.libraryBreak();
    }

    // ------------------------------------------------------------------
    // fetch / insert / replace / remove / rangeReplace
    // ------------------------------------------------------------------

    /** Retrieves the scroll located at the given coordinates. */
    public static String fetch(String input, Coordinate target) {
        byte[] bytes = utf8(input);
        SubspaceCoordinates parts = getSubspaceCoordinates(bytes, target);
        int start = parts.start();
        int end = parts.end();
        if (end > start) {
            int stop = Math.min(start + (end - start), bytes.length);
            return fromUtf8(bytes, start, stop - start);
        }
        return "";
    }

    /** The fixup breaks that walk {@code from} up to {@code location}, as bytes. */
    private static byte[] fixupBreaks(Coordinate from, Coordinate location) {
        java.io.ByteArrayOutputStream fixup = new java.io.ByteArrayOutputStream();
        Coordinate c = from.copy();
        while (c.z.library < location.z.library) { fixup.write(LIBRARY_BREAK_U8); c.libraryBreak(); }
        while (c.z.shelf < location.z.shelf) { fixup.write(SHELF_BREAK_U8); c.shelfBreak(); }
        while (c.z.series < location.z.series) { fixup.write(SERIES_BREAK_U8); c.seriesBreak(); }
        while (c.y.collection < location.y.collection) { fixup.write(COLLECTION_BREAK_U8); c.collectionBreak(); }
        while (c.y.volume < location.y.volume) { fixup.write(VOLUME_BREAK_U8); c.volumeBreak(); }
        while (c.y.book < location.y.book) { fixup.write(BOOK_BREAK_U8); c.bookBreak(); }
        while (c.x.chapter < location.x.chapter) { fixup.write(CHAPTER_BREAK_U8); c.chapterBreak(); }
        while (c.x.section < location.x.section) { fixup.write(SECTION_BREAK_U8); c.sectionBreak(); }
        while (c.x.scroll < location.x.scroll) { fixup.write(SCROLL_BREAK_U8); c.scrollBreak(); }
        return fixup.toByteArray();
    }

    public static String replace(String input, Coordinate location, String scroll) {
        byte[] bytes = utf8(input);
        SubspaceCoordinates parts = getSubspaceCoordinates(bytes, location);
        int start = parts.start();
        int end = Math.min(parts.end(), bytes.length);
        byte[] fixup = fixupBreaks(parts.best(), location);
        byte[] text = utf8(scroll);
        java.io.ByteArrayOutputStream temp = new java.io.ByteArrayOutputStream();
        temp.write(bytes, 0, start);
        temp.write(fixup, 0, fixup.length);
        temp.write(text, 0, text.length);
        temp.write(bytes, end, bytes.length - end);
        return fromUtf8(temp.toByteArray());
    }

    /** Inserts the content in {@code scroll} at {@code location} within {@code input}. */
    public static String insert(String input, Coordinate location, String scroll) {
        byte[] bytes = utf8(input);
        SubspaceCoordinates parts = getSubspaceCoordinates(bytes, location);
        int end = parts.end();
        byte[] fixup = fixupBreaks(parts.best(), location);
        byte[] text = utf8(scroll);
        java.io.ByteArrayOutputStream temp = new java.io.ByteArrayOutputStream();
        temp.write(bytes, 0, end);
        temp.write(fixup, 0, fixup.length);
        temp.write(text, 0, text.length);
        temp.write(bytes, end, bytes.length - end);
        return fromUtf8(temp.toByteArray());
    }

    public static String remove(String input, Coordinate location) {
        String phase1 = replace(input, location, "");
        return normalize(phase1);
    }

    public static String rangeReplace(String input, Range location, String scroll) {
        byte[] bytes = utf8(input);
        SubspaceCoordinates partsStart = getSubspaceCoordinates(bytes, location.start);
        SubspaceCoordinates partsEnd = getSubspaceCoordinates(bytes, location.end);
        int start = partsStart.start();
        int end = Math.min(partsEnd.end(), bytes.length);
        byte[] text = utf8(scroll);
        java.io.ByteArrayOutputStream temp = new java.io.ByteArrayOutputStream();
        temp.write(bytes, 0, start);
        temp.write(text, 0, text.length);
        temp.write(bytes, end, bytes.length - end);
        return fromUtf8(temp.toByteArray());
    }

    // ------------------------------------------------------------------
    // nextScroll / phokenize / explode / implode
    // ------------------------------------------------------------------

    /** The result of {@link #nextScroll}: (scroll, next coordinate, remaining text). */
    public record NextScrollResult(PositionedScroll scroll, Coordinate next, String remaining) {
    }

    /** Retrieves the next scroll from the given string, assuming an arbitrary starting point. */
    public static NextScrollResult nextScroll(String input, Coordinate start) {
        Coordinate location = start.copy();
        byte[] p = utf8(input);
        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
        int pi = 0;
        Coordinate begin = start.copy();
        int pmax = p.length;
        while (pi < pmax) {
            boolean dimensionBreak = false;
            byte b = p[pi];
            if (b == SCROLL_BREAK_U8) { location.scrollBreak(); dimensionBreak = true; }
            if (b == SECTION_BREAK_U8) { location.sectionBreak(); dimensionBreak = true; }
            if (b == CHAPTER_BREAK_U8) { location.chapterBreak(); dimensionBreak = true; }
            if (b == BOOK_BREAK_U8) { location.bookBreak(); dimensionBreak = true; }
            if (b == VOLUME_BREAK_U8) { location.volumeBreak(); dimensionBreak = true; }
            if (b == COLLECTION_BREAK_U8) { location.collectionBreak(); dimensionBreak = true; }
            if (b == SERIES_BREAK_U8) { location.seriesBreak(); dimensionBreak = true; }
            if (b == SHELF_BREAK_U8) { location.shelfBreak(); dimensionBreak = true; }
            if (b == LIBRARY_BREAK_U8) { location.libraryBreak(); dimensionBreak = true; }

            if (dimensionBreak) {
                if (output.size() > 0) {
                    pi += 1;
                    break;
                }
            } else {
                begin = location.copy();
                output.write(b);
            }
            pi += 1;
        }
        String remaining = fromUtf8(p, pi, pmax - pi);
        PositionedScroll outScroll = new PositionedScroll(begin, fromUtf8(output.toByteArray()));
        return new NextScrollResult(outScroll, location, remaining);
    }

    /** Transforms a packed phext buffer into a phext token (phoken) stream. */
    public static List<PositionedScroll> phokenize(String input) {
        List<PositionedScroll> result = new ArrayList<>();
        byte[] bytes = utf8(input);
        int max = bytes.length;
        Coordinate coord = defaultCoordinate();
        int pi = 0;
        while (true) {
            Coordinate begin = coord.copy();
            Integer textStart = null;
            int textEnd = max;
            while (pi < max) {
                byte b = bytes[pi];
                if (isDimensionBreak(b)) {
                    applyBreak(coord, b);
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
            String scroll = textStart != null ? fromUtf8(bytes, textStart, textEnd - textStart) : "";
            result.add(new PositionedScroll(begin, scroll));
            if (pi >= max) {
                break;
            }
        }
        return result;
    }

    /** Explodes an input phext into a hash map, suitable for fast access and updates. */
    public static Map<Coordinate, String> explode(String input) {
        Map<Coordinate, String> hash = new HashMap<>();
        for (PositionedScroll row : phokenize(input)) {
            hash.put(row.coord, row.scroll);
        }
        return hash;
    }

    /** Serializes a hash of scrolls into a monolithic phext document. */
    public static String implode(Map<Coordinate, String> map) {
        List<PositionedScroll> vec = new ArrayList<>();
        for (Map.Entry<Coordinate, String> e : map.entrySet()) {
            vec.add(new PositionedScroll(e.getKey(), e.getValue()));
        }
        vec.sort(null);
        return dephokenize(vec);
    }

    /** Serializes a phoken stream back into a monolithic phext document. */
    public static String dephokenize(List<PositionedScroll> tokens) {
        StringBuilder result = new StringBuilder();
        Coordinate coord = defaultCoordinate();
        for (PositionedScroll ps : tokens) {
            if (!ps.scroll.isEmpty()) {
                appendScroll(result, ps, coord);
            }
            coord = ps.coord;
        }
        return result.toString();
    }

    // ------------------------------------------------------------------
    // v0.7.0 performance stores (additive)
    // ------------------------------------------------------------------

    /** A capacity-managed dynamic-array store loaded from {@code buffer}. */
    public static DynamicPhext dynamicStore(String buffer) {
        return new DynamicPhext(buffer);
    }

    /** A transitive branching hash-map store loaded from {@code buffer}. */
    public static TransitivePhext transitiveStore(String buffer) {
        return new TransitivePhext(buffer);
    }

    private static void appendScroll(StringBuilder output, PositionedScroll token, Coordinate coordIn) {
        Coordinate coord = coordIn.copy();
        while (coord.compareTo(token.coord) < 0) {
            if (coord.z.library < token.coord.z.library) { output.append(LIBRARY_BREAK); coord.libraryBreak(); continue; }
            if (coord.z.shelf < token.coord.z.shelf) { output.append(SHELF_BREAK); coord.shelfBreak(); continue; }
            if (coord.z.series < token.coord.z.series) { output.append(SERIES_BREAK); coord.seriesBreak(); continue; }
            if (coord.y.collection < token.coord.y.collection) { output.append(COLLECTION_BREAK); coord.collectionBreak(); continue; }
            if (coord.y.volume < token.coord.y.volume) { output.append(VOLUME_BREAK); coord.volumeBreak(); continue; }
            if (coord.y.book < token.coord.y.book) { output.append(BOOK_BREAK); coord.bookBreak(); continue; }
            if (coord.x.chapter < token.coord.x.chapter) { output.append(CHAPTER_BREAK); coord.chapterBreak(); continue; }
            if (coord.x.section < token.coord.x.section) { output.append(SECTION_BREAK); coord.sectionBreak(); continue; }
            if (coord.x.scroll < token.coord.x.scroll) { output.append(SCROLL_BREAK); coord.scrollBreak(); continue; }
        }
        output.append(token.scroll);
    }

    // ------------------------------------------------------------------
    // merge / subtract / normalize / expand / contract
    // ------------------------------------------------------------------

    /** Combines left and right into a new phext document, merged per-scroll. */
    public static String merge(String left, String right) {
        List<PositionedScroll> tl = phokenize(left);
        List<PositionedScroll> tr = phokenize(right);
        int tli = 0;
        int tri = 0;
        StringBuilder result = new StringBuilder();
        Coordinate coord = defaultCoordinate();
        while (true) {
            boolean haveLeft = tli < tl.size();
            boolean haveRight = tri < tr.size();
            boolean pickLeft = haveLeft && (!haveRight || tl.get(tli).coord.compareTo(tr.get(tri).coord) <= 0);
            boolean pickRight = haveRight && (!haveLeft || tr.get(tri).coord.compareTo(tl.get(tli).coord) <= 0);
            if (pickLeft) {
                appendScroll(result, tl.get(tli), coord);
                coord = tl.get(tli).coord;
                tli += 1;
            }
            if (pickRight) {
                appendScroll(result, tr.get(tri), coord);
                coord = tr.get(tri).coord;
                tri += 1;
            }
            if (!pickLeft && !pickRight) {
                break;
            }
        }
        return result.toString();
    }

    /** Subtracts the scrolls from left where there is also content at those coordinates in right. */
    public static String subtract(String left, String right) {
        List<PositionedScroll> pl = phokenize(left);
        List<PositionedScroll> pr = phokenize(right);
        StringBuilder result = new StringBuilder();
        int pri = 0;
        Coordinate coord = defaultCoordinate();
        for (PositionedScroll token : pl) {
            if (token.scroll.isEmpty()) {
                continue;
            }
            while (pri < pr.size() && pr.get(pri).coord.compareTo(token.coord) < 0) {
                pri += 1;
            }
            boolean masked = pri < pr.size()
                && pr.get(pri).coord.equals(token.coord)
                && !pr.get(pri).scroll.isEmpty();
            if (!masked) {
                appendScroll(result, token, coord);
                coord = token.coord;
            }
        }
        return result.toString();
    }

    public static String normalize(String input) {
        List<PositionedScroll> arr = phokenize(input);
        return dephokenize(arr);
    }

    public static String expand(String input) {
        byte[] buffer = utf8(input);
        for (int i = 0; i < buffer.length; i++) {
            byte b = buffer[i];
            if (b == LINE_BREAK_U8) buffer[i] = SCROLL_BREAK_U8;
            else if (b == SCROLL_BREAK_U8) buffer[i] = SECTION_BREAK_U8;
            else if (b == SECTION_BREAK_U8) buffer[i] = CHAPTER_BREAK_U8;
            else if (b == CHAPTER_BREAK_U8) buffer[i] = BOOK_BREAK_U8;
            else if (b == BOOK_BREAK_U8) buffer[i] = VOLUME_BREAK_U8;
            else if (b == VOLUME_BREAK_U8) buffer[i] = COLLECTION_BREAK_U8;
            else if (b == COLLECTION_BREAK_U8) buffer[i] = SERIES_BREAK_U8;
            else if (b == SERIES_BREAK_U8) buffer[i] = SHELF_BREAK_U8;
            else if (b == SHELF_BREAK_U8) buffer[i] = LIBRARY_BREAK_U8;
        }
        return fromUtf8(buffer);
    }

    public static String contract(String input) {
        byte[] buffer = utf8(input);
        for (int i = 0; i < buffer.length; i++) {
            byte b = buffer[i];
            if (b == LIBRARY_BREAK_U8) buffer[i] = SHELF_BREAK_U8;
            else if (b == SHELF_BREAK_U8) buffer[i] = SERIES_BREAK_U8;
            else if (b == SERIES_BREAK_U8) buffer[i] = COLLECTION_BREAK_U8;
            else if (b == COLLECTION_BREAK_U8) buffer[i] = VOLUME_BREAK_U8;
            else if (b == VOLUME_BREAK_U8) buffer[i] = BOOK_BREAK_U8;
            else if (b == BOOK_BREAK_U8) buffer[i] = CHAPTER_BREAK_U8;
            else if (b == CHAPTER_BREAK_U8) buffer[i] = SECTION_BREAK_U8;
            else if (b == SECTION_BREAK_U8) buffer[i] = SCROLL_BREAK_U8;
            else if (b == SCROLL_BREAK_U8) buffer[i] = LINE_BREAK_U8;
        }
        return fromUtf8(buffer);
    }

    // ------------------------------------------------------------------
    // summary / maps / checksum / manifest / soundex / index
    // ------------------------------------------------------------------

    public static String createSummary(String input) {
        byte[] bytes = utf8(input);
        int end = 0;
        while (end < bytes.length) {
            if (isPhextBreak(bytes[end])) {
                break;
            }
            end += 1;
        }
        String summary = fromUtf8(bytes, 0, end);
        if (end < bytes.length) {
            summary += "...";
        }
        return summary;
    }

    public static String navmap(String urlbase, String input) {
        List<PositionedScroll> phokens = phokenize(input);
        StringBuilder result = new StringBuilder();
        if (!phokens.isEmpty()) {
            result.append("<ul>\n");
        }
        for (PositionedScroll phoken : phokens) {
            result.append("<li><a href=\"")
                .append(urlbase).append(phoken.coord.toUrlencoded())
                .append("\">").append(phoken.coord).append(' ')
                .append(createSummary(phoken.scroll)).append("</a></li>\n");
        }
        if (!phokens.isEmpty()) {
            result.append("</ul>\n");
        }
        return result.toString();
    }

    public static String textmap(String input) {
        StringBuilder result = new StringBuilder();
        for (PositionedScroll phoken : phokenize(input)) {
            result.append("* ").append(phoken.coord).append(": ")
                .append(createSummary(phoken.scroll)).append('\n');
        }
        return result.toString();
    }

    /** A 128-bit content checksum (xxh3), as 32 lowercase hex digits. */
    public static String checksum(String input) {
        long[] hash = Xxh3.xxh3_128(utf8(input));
        return String.format("%016x%016x", hash[1], hash[0]);
    }

    public static String manifest(String input) {
        List<PositionedScroll> phokens = phokenize(input);
        for (PositionedScroll ps : phokens) {
            ps.scroll = checksum(ps.scroll);
        }
        return dephokenize(phokens);
    }

    private static String soundexInternal(String scroll) {
        String letter1 = "bpfv";
        String letter2 = "cskgjqxz";
        String letter3 = "dt";
        String letter4 = "l";
        String letter5 = "mn";
        String letter6 = "r";
        long value = 1; // 1-100
        for (byte raw : utf8(scroll)) {
            int c = raw & 0xFF;
            if (letter1.indexOf(c) >= 0) { value += 1; continue; }
            if (letter2.indexOf(c) >= 0) { value += 2; continue; }
            if (letter3.indexOf(c) >= 0) { value += 3; continue; }
            if (letter4.indexOf(c) >= 0) { value += 4; continue; }
            if (letter5.indexOf(c) >= 0) { value += 5; continue; }
            if (letter6.indexOf(c) >= 0) { value += 6; continue; }
        }
        return Long.toString(value % 99);
    }

    public static String soundexV1(String input) {
        List<PositionedScroll> phokens = phokenize(input);
        for (PositionedScroll ps : phokens) {
            ps.scroll = soundexInternal(ps.scroll);
        }
        return dephokenize(phokens);
    }

    private static List<PositionedScroll> indexPhokens(String input) {
        List<PositionedScroll> phokens = phokenize(input);
        long offset = 0;
        Coordinate coord = defaultCoordinate();
        List<PositionedScroll> output = new ArrayList<>();
        for (PositionedScroll phoken : phokens) {
            Coordinate reference = phoken.coord;
            while (coord.z.library < reference.z.library) { coord.libraryBreak(); offset += 1; }
            while (coord.z.shelf < reference.z.shelf) { coord.shelfBreak(); offset += 1; }
            while (coord.z.series < reference.z.series) { coord.seriesBreak(); offset += 1; }
            while (coord.y.collection < reference.y.collection) { coord.collectionBreak(); offset += 1; }
            while (coord.y.volume < reference.y.volume) { coord.volumeBreak(); offset += 1; }
            while (coord.y.book < reference.y.book) { coord.bookBreak(); offset += 1; }
            while (coord.x.chapter < reference.x.chapter) { coord.chapterBreak(); offset += 1; }
            while (coord.x.section < reference.x.section) { coord.sectionBreak(); offset += 1; }
            while (coord.x.scroll < reference.x.scroll) { coord.scrollBreak(); offset += 1; }
            output.add(new PositionedScroll(coord.copy(), Long.toString(offset)));
            offset += utf8(phoken.scroll).length;
        }
        return output;
    }

    public static String index(String input) {
        List<PositionedScroll> output = indexPhokens(input);
        return dephokenize(output);
    }

    /**
     * The offset of the scroll at {@code coord}, or of the last scroll before
     * it. 0 when no scroll comes at or before {@code coord}.
     */
    public static long offset(String input, Coordinate coord) {
        List<PositionedScroll> output = indexPhokens(input);
        long result = 0;
        for (PositionedScroll phoken : output) {
            if (phoken.coord.compareTo(coord) > 0) {
                break;
            }
            try {
                result = Long.parseLong(phoken.scroll);
            } catch (NumberFormatException ignored) {
                // keep the previous result, as Rust's unwrap_or(result)
            }
        }
        return result;
    }

    // ------------------------------------------------------------------
    // chains (0.6.0)
    // ------------------------------------------------------------------

    /**
     * Splits a stream into its chains. CHAIN_BREAK separates the member
     * phexts of a chain; CHAIN_CLOSE terminates a chain, and whatever
     * follows it opens the next one. The stream's final chain is closed only
     * when the stream ends with CHAIN_CLOSE. Splitting follows
     * {@link #splitOn} semantics, so empty members and empty chains are
     * preserved exactly: parse and serialize are byte-exact inverses for
     * every stream.
     */
    public static List<Chain> parseChains(String input) {
        boolean endsClosed = !input.isEmpty() && input.charAt(input.length() - 1) == CHAIN_CLOSE;
        List<String> segments = new ArrayList<>(splitOn(input, CHAIN_CLOSE));
        if (endsClosed) {
            // splitOn preserves the empty tail after a trailing delimiter;
            // that tail is the end of the stream, not another chain
            segments.remove(segments.size() - 1);
        }
        int count = segments.size();
        List<Chain> chains = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            boolean closed = endsClosed || i + 1 < count;
            chains.add(new Chain(splitOn(segments.get(i), CHAIN_BREAK), closed));
        }
        return chains;
    }

    /** Serializes a stream of chains back into stream bytes: the inverse of {@link #parseChains}. */
    public static String serializeChains(List<Chain> chains) {
        StringBuilder result = new StringBuilder();
        for (Chain chain : chains) {
            result.append(chain.serialize());
        }
        return result.toString();
    }

    // ------------------------------------------------------------------
    // multiverse addressing (0.6.0)
    // ------------------------------------------------------------------

    /** A parsed multiverse address: (universe, coordinate). */
    public record UniverseAddress(long universe, Coordinate coordinate) {
    }

    /**
     * Parses a multiverse address: {@code "$N:<address>"} names universe N;
     * a bare address means universe 1. Returns {@code null} for a malformed
     * universe prefix or universe 0, mirroring Rust's {@code Option}.
     */
    public static UniverseAddress parseUniverseAddress(String address) {
        long universe;
        String coordPart;
        if (address.startsWith("$")) {
            String rest = address.substring(1);
            int colon = rest.indexOf(':');
            if (colon < 0) {
                return null;
            }
            String num = rest.substring(0, colon);
            long n;
            try {
                n = Coordinate.parseComponent(num);
            } catch (PhextParseException e) {
                return null;
            }
            if (n < 1) {
                return null;
            }
            universe = n;
            coordPart = rest.substring(colon + 1);
        } else {
            universe = 1;
            coordPart = address;
        }
        return new UniverseAddress(universe, toCoordinate(coordPart));
    }

    // ------------------------------------------------------------------
    // line/column addressing (0.6.1)
    // ------------------------------------------------------------------
    //
    // A point address is "[$N:]<9D coordinate>@<line>,<column>": the
    // multiverse prefix and the coordinate exactly as in
    // parseUniverseAddress, plus a within-scroll caret position. Line and
    // column are 1-based; a position names a caret boundary (column 1 is
    // the boundary before the line's first character; a line of n
    // characters has valid columns 1..n+1), and columns count Unicode
    // scalar values. A point may carry "+<offsetChars>": select forward
    // that many characters (scalars) from the point, counted within the
    // scroll, terminating at the scroll boundary.
    //
    // A range address is two point addresses joined by one hyphen:
    // "<point>-<point>". The endpoints may span scrolls and universes; an
    // endpoint without "@" names its whole scroll (as start: its first
    // boundary; as end: its last). Canonical form: endpoints in ascending
    // (universe, coordinate, line, column) order — direction is
    // discarded — "$1:" never written, integers in minimal decimal form.
    // Out-of-range endpoints fail at resolution, never clamp.
    //
    // Mirrors the addressing section of libphext-rs src/phext.rs (0.6.1).
    // Java trap, handled throughout this section: String is UTF-16, so
    // every line/column/offset count runs over code points
    // (codePointAt / offsetByCodePoints / codePointCount), never char
    // indices and never UTF-8 bytes.

    /**
     * The addressing grammar's unsigned integer: ASCII digits only, no
     * sign, no whitespace, no empty string. Returns {@code null} on any
     * malformed or out-of-{@code long}-range input. (Deliberately stricter
     * than {@link Coordinate#parseComponent}, which accepts a leading
     * {@code +} as Rust's {@code usize} parser does — the address grammar
     * accepts no sign, ever.)
     */
    private static Long parseAddressUint(String text) {
        if (text.isEmpty()) {
            return null;
        }
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c < '0' || c > '9') {
                return null;
            }
        }
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException e) {
            // beyond the range of a Java long (the port's component type)
            return null;
        }
    }

    /**
     * The addressing grammar's coordinate: exactly three '/'-separated
     * arms of three '.'-separated unsigned components, each at least 1.
     * Returns {@code null} on any malformed input.
     */
    private static Coordinate parseAddressCoordinate(String text) {
        String[] arms = text.split("/", -1);
        if (arms.length != 3) {
            return null;
        }
        long[] values = new long[9];
        int vi = 0;
        for (String arm : arms) {
            String[] parts = arm.split("\\.", -1);
            if (parts.length != 3) {
                return null;
            }
            for (String part : parts) {
                Long value = parseAddressUint(part);
                if (value == null || value < 1) {
                    return null;
                }
                values[vi++] = value;
            }
        }
        return new Coordinate(
            new ZCoordinate(values[0], values[1], values[2]),
            new YCoordinate(values[3], values[4], values[5]),
            new XCoordinate(values[6], values[7], values[8]));
    }

    /**
     * Parses a 0.6.1 point address:
     * {@code "[$N:]<coordinate>[@<line>,<column>[+<offsetChars>]]"}.
     * Returns {@code null} on any malformed input: universe 0, a
     * coordinate component below 1, a line or column below 1, a sign
     * anywhere, or trailing structure. {@code "$1:"} is accepted on input;
     * the canonical {@link PointAddress#toString()} never emits it.
     */
    public static PointAddress parsePointAddress(String address) {
        long universe;
        String rest;
        if (address.startsWith("$")) {
            String stripped = address.substring(1);
            int colon = stripped.indexOf(':');
            if (colon < 0) {
                return null;
            }
            Long n = parseAddressUint(stripped.substring(0, colon));
            if (n == null || n < 1) {
                return null;
            }
            universe = n;
            rest = stripped.substring(colon + 1);
        } else {
            universe = 1;
            rest = address;
        }
        String coordPart;
        String posPart = null;
        int at = rest.indexOf('@');
        if (at >= 0) {
            coordPart = rest.substring(0, at);
            posPart = rest.substring(at + 1);
            if (posPart.contains("@")) {
                return null;
            }
        } else {
            coordPart = rest;
        }
        Coordinate coordinate = parseAddressCoordinate(coordPart);
        if (coordinate == null) {
            return null;
        }
        ScrollPosition position = null;
        Long offset = null;
        if (posPart != null) {
            int comma = posPart.indexOf(',');
            if (comma < 0) {
                return null;
            }
            Long line = parseAddressUint(posPart.substring(0, comma));
            if (line == null) {
                return null;
            }
            String colPart = posPart.substring(comma + 1);
            String colText = colPart;
            Long offsetValue = null;
            int plus = colPart.indexOf('+');
            if (plus >= 0) {
                colText = colPart.substring(0, plus);
                offsetValue = parseAddressUint(colPart.substring(plus + 1));
                if (offsetValue == null) {
                    return null;
                }
            }
            Long column = parseAddressUint(colText);
            if (column == null) {
                return null;
            }
            if (line < 1 || column < 1) {
                return null;
            }
            position = new ScrollPosition(line, column);
            offset = offsetValue;
        }
        return new PointAddress(universe, coordinate, position, offset);
    }

    /**
     * The ordering key of a range endpoint: its position when it carries
     * one; a position-less endpoint sorts by role — as start, its scroll's
     * first boundary (1,1); as end, its scroll's last boundary.
     */
    private static ScrollPosition endpointPositionKey(PointAddress point, boolean isStart) {
        if (point.position() != null) {
            return point.position();
        }
        return isStart
            ? new ScrollPosition(1, 1)
            : new ScrollPosition(Long.MAX_VALUE, Long.MAX_VALUE);
    }

    /** Total order on range endpoints: (universe, coordinate, line, column). */
    private static int endpointCmp(PointAddress a, boolean aIsStart, PointAddress b, boolean bIsStart) {
        if (a.universe() != b.universe()) {
            return Long.compare(a.universe(), b.universe());
        }
        int coord = a.coordinate().compareTo(b.coordinate());
        if (coord != 0) {
            return coord;
        }
        return endpointPositionKey(a, aIsStart).compareTo(endpointPositionKey(b, bIsStart));
    }

    /**
     * Parses a 0.6.1 range address: exactly two point addresses joined by
     * one hyphen. The endpoints are normalized to ascending order
     * (direction is discarded, per the ratified spec). Range endpoints may
     * not carry a {@code "+offset"} — the offset form never takes a second
     * endpoint. Returns {@code null} on malformed input.
     */
    public static RangeAddress parseRangeAddress(String address) {
        int hyphen = address.indexOf('-');
        if (hyphen < 0) {
            return null;
        }
        String first = address.substring(0, hyphen);
        String second = address.substring(hyphen + 1);
        if (second.contains("-")) {
            return null;
        }
        PointAddress a = parsePointAddress(first);
        PointAddress b = parsePointAddress(second);
        if (a == null || b == null) {
            return null;
        }
        if (a.offset() != null || b.offset() != null) {
            return null;
        }
        if (endpointCmp(a, true, b, false) > 0) {
            return new RangeAddress(b, a);
        }
        return new RangeAddress(a, b);
    }

    /**
     * Resolves a caret position inside one scroll to a character (Unicode
     * scalar) offset. Returns {@code null} when the line or column is past
     * the scroll's extent — never clamps.
     */
    static Long resolveScrollOffset(String text, ScrollPosition pos) {
        if (pos.line() < 1 || pos.column() < 1) {
            return null;
        }
        long line = 1;
        long column = 1;
        long offset = 0;
        int i = 0;
        while (i < text.length()) {
            int cp = text.codePointAt(i);
            if (line == pos.line() && column == pos.column()) {
                return offset;
            }
            if (cp == '\n') {
                line += 1;
                column = 1;
            } else {
                column += 1;
            }
            offset += 1;
            i += Character.charCount(cp);
        }
        if (line == pos.line() && column == pos.column()) {
            return offset;
        }
        return null;
    }

    /**
     * The scroll text at a coordinate in one member phext, in the
     * phokenize sense of presence: a coordinate is present exactly when
     * {@link #phokenize} enumerates it. Returns {@code null} when absent.
     */
    static String addressedScrollText(String member, Coordinate target) {
        for (PositionedScroll ps : phokenize(member)) {
            if (ps.coord.equals(target)) {
                return ps.scroll;
            }
        }
        return null;
    }

    /** The number of Unicode scalar values in {@code text}. */
    static long scalarCount(String text) {
        return text.codePointCount(0, text.length());
    }

    /**
     * The substring of {@code text} between scalar offsets {@code lo}
     * (inclusive) and {@code hi} (exclusive). Callers guarantee
     * {@code 0 <= lo <= hi <= scalarCount(text)}.
     */
    static String scalarSlice(String text, long lo, long hi) {
        int start = text.offsetByCodePoints(0, (int) lo);
        int end = text.offsetByCodePoints(0, (int) hi);
        return text.substring(start, end);
    }
}

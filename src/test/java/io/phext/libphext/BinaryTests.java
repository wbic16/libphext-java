package io.phext.libphext;

import java.nio.charset.StandardCharsets;
import java.util.List;

/** Port of libphext-rs {@code src/test_binary.rs}. */
public final class BinaryTests {
    private BinaryTests() {
    }

    private static Coordinate coord(String address) {
        return Phext.toCoordinate(address);
    }

    private static byte[] bytes(int... values) {
        byte[] out = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            out[i] = (byte) values[i];
        }
        return out;
    }

    public static void register() {
        TestMain.test("BinaryTests", "test_encode_decode_round_trip", () -> {
            byte[] empty = new byte[0];
            String scroll = BinaryScroll.encodeScroll(empty);
            A.eq("phext-binary:1:base64\n", scroll);
            A.eqBytes(empty, BinaryScroll.decodeScroll(scroll));

            byte[] allBytes = new byte[256];
            for (int i = 0; i < 256; i++) {
                allBytes[i] = (byte) i;
            }
            scroll = BinaryScroll.encodeScroll(allBytes);
            A.isTrue(BinaryScroll.isBinaryScroll(scroll));
            A.eqBytes(allBytes, BinaryScroll.decodeScroll(scroll));

            byte[] foreign = bytes(0xFF, 0xFE, 0x80, 0x00, 0x61, 0xC3, 0x28);
            scroll = BinaryScroll.encodeScroll(foreign);
            A.eqBytes(foreign, BinaryScroll.decodeScroll(scroll));

            byte[] big = new byte[1024 * 1024 + 13];
            for (int i = 0; i < big.length; i++) {
                big[i] = (byte) (i % 256);
            }
            scroll = BinaryScroll.encodeScroll(big);
            A.eqBytes(big, BinaryScroll.decodeScroll(scroll));
        });

        TestMain.test("BinaryTests", "test_decode_scroll_rejects_non_binary", () -> {
            A.isNull(BinaryScroll.decodeScroll("just some text"), "plain text");
            A.isNull(BinaryScroll.decodeScroll(""), "empty");
            A.isNull(BinaryScroll.decodeScroll("phext-binary:1:base64"), "header without line break");
            A.isFalse(BinaryScroll.isBinaryScroll("phext-binary:1:base64"));
            String malformed = "phext-binary:1:base64\n!!! not base64 !!!";
            A.isTrue(BinaryScroll.isBinaryScroll(malformed));
            A.isNull(BinaryScroll.decodeScroll(malformed), "malformed base64 body");
        });

        TestMain.test("BinaryTests", "test_insert_and_fetch_binary", () -> {
            Coordinate target = coord("1.1.1/1.1.1/1.1.2");
            byte[] payload = bytes(0x00, 0x01, 0x02, 0xFE, 0xFF, 0x7F, 0x80);
            String doc = Phext.insert("", coord("1.1.1/1.1.1/1.1.1"), "plain text scroll");
            doc = BinaryScroll.insertBinary(doc, target, payload);
            A.eq("plain text scroll", Phext.fetch(doc, coord("1.1.1/1.1.1/1.1.1")));
            A.eqBytes(payload, BinaryScroll.fetchBinary(doc, target));
            String stored = Phext.fetch(doc, target);
            A.isTrue(BinaryScroll.isBinaryScroll(stored));
            A.eqBytes(payload, BinaryScroll.decodeScroll(stored));
        });

        TestMain.test("BinaryTests", "test_fetch_binary_fallbacks", () -> {
            String doc = Phext.insert("", coord("1.1.1/1.1.1/1.1.1"), "hello");
            A.eqBytes("hello".getBytes(StandardCharsets.UTF_8),
                BinaryScroll.fetchBinary(doc, coord("1.1.1/1.1.1/1.1.1")));
            A.eqBytes(new byte[0], BinaryScroll.fetchBinary(doc, coord("9.9.9/9.9.9/9.9.9")));
            String doc2 = Phext.insert("", coord("1.1.1/1.1.1/1.1.1"), "phext-binary:1:base64\n!!!");
            A.eqBytes("phext-binary:1:base64\n!!!".getBytes(StandardCharsets.UTF_8),
                BinaryScroll.fetchBinary(doc2, coord("1.1.1/1.1.1/1.1.1")));
        });

        TestMain.test("BinaryTests", "test_update_rule_non_utf8_is_base64_verbatim", () -> {
            Coordinate target = coord("1.1.1/1.1.1/1.1.1");
            byte[] payload = bytes(0xFF, 0x17, 0x1C, 0x80, 0x00);
            String doc = BinaryScroll.replaceBytes("", target, payload);
            String stored = Phext.fetch(doc, target);
            A.isTrue(BinaryScroll.isBinaryScroll(stored), "non-UTF-8 update must be stored as a binary scroll");
            A.eqBytes(payload, BinaryScroll.fetchBinary(doc, target));
            String doc2 = BinaryScroll.insertBytes("", target, payload);
            A.eqBytes(payload, BinaryScroll.fetchBinary(doc2, target));
        });

        TestMain.test("BinaryTests", "test_update_rule_utf8_stays_text", () -> {
            Coordinate target = coord("1.1.1/1.1.1/1.1.1");
            String doc = BinaryScroll.replaceBytes("", target, "ordinary text ✓".getBytes(StandardCharsets.UTF_8));
            A.eq("ordinary text ✓", Phext.fetch(doc, target));
            A.isFalse(BinaryScroll.isBinaryScroll(Phext.fetch(doc, target)));
            A.eqBytes("ordinary text ✓".getBytes(StandardCharsets.UTF_8), BinaryScroll.fetchBinary(doc, target));
        });

        TestMain.test("BinaryTests", "test_ingest_foreign_byte_stream", () -> {
            java.io.ByteArrayOutputStream stream = new java.io.ByteArrayOutputStream();
            stream.write("hello".getBytes(StandardCharsets.UTF_8));
            stream.write(Phext.SCROLL_BREAK_U8);
            stream.write(bytes(0xFF, 0xFE, 0x41, 0x80));
            String doc = BinaryScroll.ingest(stream.toByteArray());
            A.eq("hello", Phext.fetch(doc, coord("1.1.1/1.1.1/1.1.1")));
            Coordinate foreignAt = coord("1.1.1/1.1.1/1.1.2");
            A.isTrue(BinaryScroll.isBinaryScroll(Phext.fetch(doc, foreignAt)));
            A.eqBytes(bytes(0xFF, 0xFE, 0x41, 0x80), BinaryScroll.fetchBinary(doc, foreignAt));
        });

        TestMain.test("BinaryTests", "test_ingest_canonical_phext_is_identity", () -> {
            String doc = Phext.insert("", coord("1.1.1/1.1.1/1.1.1"), "first");
            doc = Phext.insert(doc, coord("1.1.1/1.1.1/1.1.2"), "second ✓ scroll");
            doc = Phext.insert(doc, coord("2.1.1/1.3.1/1.1.1"), "far away");
            A.eq(doc, BinaryScroll.ingest(doc.getBytes(StandardCharsets.UTF_8)));
        });

        TestMain.test("BinaryTests", "test_ingest_empty_and_break_only_streams", () -> {
            String doc = BinaryScroll.ingest(new byte[0]);
            A.eq("", Phext.fetch(doc, coord("1.1.1/1.1.1/1.1.1")));
            String doc2 = BinaryScroll.ingest(bytes(Phext.SCROLL_BREAK_U8, Phext.SECTION_BREAK_U8));
            A.eq("", Phext.fetch(doc2, coord("1.1.1/1.1.1/1.1.1")));
        });

        TestMain.test("BinaryTests", "test_phokenize_bytes_matches_phokenize", () -> {
            String doc = "alpha\u0017beta\u0018gamma";
            List<PositionedScroll> textParts = Phext.phokenize(doc);
            List<PositionedByteScroll> byteParts = BinaryScroll.phokenizeBytes(doc.getBytes(StandardCharsets.UTF_8));
            A.eq(textParts.size(), byteParts.size());
            for (int i = 0; i < textParts.size(); i++) {
                A.eq(textParts.get(i).coord, byteParts.get(i).coord);
                A.eqBytes(textParts.get(i).scroll.getBytes(StandardCharsets.UTF_8), byteParts.get(i).scroll);
            }
        });
    }
}

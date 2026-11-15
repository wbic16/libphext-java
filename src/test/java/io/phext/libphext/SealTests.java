package io.phext.libphext;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Port of libphext-rs {@code src/test_seal.rs}. The Rust suite seals the
 * crate's {@code unit-test.phext} corpus (a file another test generates);
 * here the same multi-scroll corpus text is used directly.
 */
public final class SealTests {
    private SealTests() {
    }

    private static final String CORPUS =
        "a simple phext doc with three scrolls\u0017we just want to verify\u0017that all of our breaks are making it through the seal.\u0018section 2\u0019chapter 2\u001Abook 2\u001Cvolume 2\u001Dcollection 2\u001Eseries 2\u001Fshelf 2\u0001library 2";

    public static void register() {
        TestMain.test("SealTests", "test_seal_round_trip_real_phext", () -> {
            byte[] sealed = PhextSeal.seal(CORPUS);
            A.isTrue(sealed.length > 0);
            // an xz stream starts with the xz magic bytes
            A.eqBytes(new byte[]{(byte) 0xFD, '7', 'z', 'X', 'Z', 0}, Arrays.copyOf(sealed, 6));
            A.eq(CORPUS, PhextSeal.unsealString(sealed));
        });

        TestMain.test("SealTests", "test_seal_round_trip_empty_and_small", () -> {
            A.eq("", PhextSeal.unsealString(PhextSeal.seal("")));
            A.eqBytes("hello phext".getBytes(StandardCharsets.UTF_8),
                PhextSeal.unseal(PhextSeal.seal("hello phext")));
        });

        TestMain.test("SealTests", "test_seal_carrying_binary_scrolls", () -> {
            String doc = "cover text\u0017phext-binary:1:base64\nAAEC/f7/fw==\u0017after";
            A.eq(doc, PhextSeal.unsealString(PhextSeal.seal(doc)));
        });

        TestMain.test("SealTests", "test_seal_is_deterministic", () -> {
            String doc = "the same phext, sealed twice\u0017the same phext, sealed twice";
            A.eqBytes(PhextSeal.seal(doc), PhextSeal.seal(doc));
        });

        TestMain.test("SealTests", "test_unseal_rejects_non_xz_data", () -> {
            A.isTrue(throwsIo(() -> PhextSeal.unseal("this is not an xz stream".getBytes(StandardCharsets.UTF_8))), "non-xz");
            A.isTrue(throwsIo(() -> PhextSeal.unseal(new byte[0])), "empty");
            A.isTrue(throwsIo(() -> PhextSeal.unsealString("phext, but unsealed".getBytes(StandardCharsets.UTF_8))), "unsealed text");
        });
    }

    private static boolean throwsIo(TestMain.ThrowingRunnable r) {
        try {
            r.run();
            return false;
        } catch (IOException e) {
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}

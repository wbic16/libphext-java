package io.phext.libphext;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

import org.tukaani.xz.LZMA2Options;
import org.tukaani.xz.SingleXZInputStream;
import org.tukaani.xz.XZ;
import org.tukaani.xz.XZOutputStream;

/**
 * The xz seal: canonical compression for a finished phext.
 *
 * <p>Compression is a seal, not a working format. A phext is authored,
 * edited, and queried as plain text; when a phext is finished — archived,
 * published, or transported — it may be sealed: xz-compressed as a whole.
 * Unsealing restores the exact original bytes.
 *
 * <p>Seal specification:
 * <ul>
 *   <li>The seal is a standard xz stream over the phext's exact bytes.
 *       Decoders in any implementation must accept any valid xz stream.</li>
 *   <li>This implementation encodes at preset 6 with the CRC64 stream
 *       check, matching libphext-rs (xz2 defaults). The preset is an
 *       encoder choice, not a format requirement; seals produced at other
 *       presets unseal identically.</li>
 *   <li>Sealing is deterministic for a given encoder and preset.</li>
 * </ul>
 *
 * <p>Java has no standard-library lzma, so the seal uses XZ for Java
 * (org.tukaani:xz), the pure-Java XZ implementation — mirroring how
 * libphext-rs gates the seal behind its {@code xz} feature and how
 * libphext-cs keeps it in a separate assembly.
 *
 * <p>Mirrors libphext-rs {@code src/seal.rs} (feature {@code xz}).
 */
public final class PhextSeal {
    private PhextSeal() {
    }

    /** The xz preset this implementation seals with. */
    public static final int SEAL_PRESET = 6;

    /** Seals a phext: xz-compresses its full UTF-8 bytes. */
    public static byte[] seal(String input) throws IOException {
        return sealBytes(input.getBytes(StandardCharsets.UTF_8));
    }

    /** Seals raw phext bytes (see {@link #seal}). */
    public static byte[] sealBytes(byte[] input) throws IOException {
        LZMA2Options options = new LZMA2Options();
        options.setPreset(SEAL_PRESET);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (XZOutputStream xz = new XZOutputStream(out, options, XZ.CHECK_CRC64)) {
            xz.write(input);
        }
        return out.toByteArray();
    }

    /**
     * Unseals a sealed phext back to its exact original bytes. Throws
     * {@link IOException} when the data is not a valid xz stream. A single
     * xz stream is read, matching the xz2 decoder used by libphext-rs.
     */
    public static byte[] unseal(byte[] data) throws IOException {
        if (data.length == 0) {
            throw new IOException("Cannot unseal an empty stream: not an xz stream.");
        }
        try (SingleXZInputStream xz = new SingleXZInputStream(new ByteArrayInputStream(data))) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int n;
            while ((n = xz.read(buffer)) != -1) {
                out.write(buffer, 0, n);
            }
            return out.toByteArray();
        }
    }

    /**
     * Unseals directly to text. Throws {@link IOException} when the
     * unsealed bytes are not valid UTF-8; to unseal foreign content that
     * may carry non-UTF-8 scrolls, {@link #unseal} to bytes and pass them
     * through {@link BinaryScroll#ingest}, which applies the
     * base64-verbatim ingestion rule instead of failing.
     */
    public static String unsealString(byte[] data) throws IOException {
        byte[] bytes = unseal(data);
        try {
            return StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString();
        } catch (CharacterCodingException e) {
            throw new IOException("Unsealed bytes are not valid UTF-8", e);
        }
    }
}

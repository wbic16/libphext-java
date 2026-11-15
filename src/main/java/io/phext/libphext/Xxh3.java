package io.phext.libphext;

/**
 * Pure-Java XXH3-128 (default secret, seed 0), translated from the
 * xxhash-rust 0.8.15 scalar implementation ({@code xxh3.rs} /
 * {@code xxh3_common.rs}) — the same algorithm libphext-rs's
 * {@code checksum} uses via {@code xxh3_128}. All arithmetic is on Java
 * {@code long}s, which wrap modulo 2^64 exactly like Rust {@code u64}
 * wrapping arithmetic; multiplications that need 128 bits use
 * {@link Math#unsignedMultiplyHigh}.
 */
final class Xxh3 {
    private Xxh3() {
    }

    // XXH64 primes
    private static final long P64_1 = 0x9E3779B185EBCA87L;
    private static final long P64_2 = 0xC2B2AE3D27D4EB4FL;
    private static final long P64_3 = 0x165667B19E3779F9L;
    private static final long P64_4 = 0x85EBCA77C2B2AE63L;
    private static final long P64_5 = 0x27D4EB2F165667C5L;
    // XXH32 primes (zero-extended)
    private static final long P32_1 = 0x9E3779B1L;
    private static final long P32_2 = 0x85EBCA77L;
    private static final long P32_3 = 0xC2B2AE3DL;

    private static final int SECRET_SIZE_MIN = 136;
    private static final int SECRET_MERGEACCS_START = 11;
    private static final int SECRET_LASTACC_START = 7;
    private static final int STRIPE_LEN = 64;
    private static final int SECRET_CONSUME_RATE = 8;
    private static final int MID_SIZE_MAX = 240;

    private static final byte[] DEFAULT_SECRET = {
        (byte) 0xb8, (byte) 0xfe, (byte) 0x6c, (byte) 0x39, (byte) 0x23, (byte) 0xa4, (byte) 0x4b, (byte) 0xbe, (byte) 0x7c, (byte) 0x01, (byte) 0x81, (byte) 0x2c, (byte) 0xf7, (byte) 0x21, (byte) 0xad, (byte) 0x1c,
        (byte) 0xde, (byte) 0xd4, (byte) 0x6d, (byte) 0xe9, (byte) 0x83, (byte) 0x90, (byte) 0x97, (byte) 0xdb, (byte) 0x72, (byte) 0x40, (byte) 0xa4, (byte) 0xa4, (byte) 0xb7, (byte) 0xb3, (byte) 0x67, (byte) 0x1f,
        (byte) 0xcb, (byte) 0x79, (byte) 0xe6, (byte) 0x4e, (byte) 0xcc, (byte) 0xc0, (byte) 0xe5, (byte) 0x78, (byte) 0x82, (byte) 0x5a, (byte) 0xd0, (byte) 0x7d, (byte) 0xcc, (byte) 0xff, (byte) 0x72, (byte) 0x21,
        (byte) 0xb8, (byte) 0x08, (byte) 0x46, (byte) 0x74, (byte) 0xf7, (byte) 0x43, (byte) 0x24, (byte) 0x8e, (byte) 0xe0, (byte) 0x35, (byte) 0x90, (byte) 0xe6, (byte) 0x81, (byte) 0x3a, (byte) 0x26, (byte) 0x4c,
        (byte) 0x3c, (byte) 0x28, (byte) 0x52, (byte) 0xbb, (byte) 0x91, (byte) 0xc3, (byte) 0x00, (byte) 0xcb, (byte) 0x88, (byte) 0xd0, (byte) 0x65, (byte) 0x8b, (byte) 0x1b, (byte) 0x53, (byte) 0x2e, (byte) 0xa3,
        (byte) 0x71, (byte) 0x64, (byte) 0x48, (byte) 0x97, (byte) 0xa2, (byte) 0x0d, (byte) 0xf9, (byte) 0x4e, (byte) 0x38, (byte) 0x19, (byte) 0xef, (byte) 0x46, (byte) 0xa9, (byte) 0xde, (byte) 0xac, (byte) 0xd8,
        (byte) 0xa8, (byte) 0xfa, (byte) 0x76, (byte) 0x3f, (byte) 0xe3, (byte) 0x9c, (byte) 0x34, (byte) 0x3f, (byte) 0xf9, (byte) 0xdc, (byte) 0xbb, (byte) 0xc7, (byte) 0xc7, (byte) 0x0b, (byte) 0x4f, (byte) 0x1d,
        (byte) 0x8a, (byte) 0x51, (byte) 0xe0, (byte) 0x4b, (byte) 0xcd, (byte) 0xb4, (byte) 0x59, (byte) 0x31, (byte) 0xc8, (byte) 0x9f, (byte) 0x7e, (byte) 0xc9, (byte) 0xd9, (byte) 0x78, (byte) 0x73, (byte) 0x64,
        (byte) 0xea, (byte) 0xc5, (byte) 0xac, (byte) 0x83, (byte) 0x34, (byte) 0xd3, (byte) 0xeb, (byte) 0xc3, (byte) 0xc5, (byte) 0x81, (byte) 0xa0, (byte) 0xff, (byte) 0xfa, (byte) 0x13, (byte) 0x63, (byte) 0xeb,
        (byte) 0x17, (byte) 0x0d, (byte) 0xdd, (byte) 0x51, (byte) 0xb7, (byte) 0xf0, (byte) 0xda, (byte) 0x49, (byte) 0xd3, (byte) 0x16, (byte) 0x55, (byte) 0x26, (byte) 0x29, (byte) 0xd4, (byte) 0x68, (byte) 0x9e,
        (byte) 0x2b, (byte) 0x16, (byte) 0xbe, (byte) 0x58, (byte) 0x7d, (byte) 0x47, (byte) 0xa1, (byte) 0xfc, (byte) 0x8f, (byte) 0xf8, (byte) 0xb8, (byte) 0xd1, (byte) 0x7a, (byte) 0xd0, (byte) 0x31, (byte) 0xce,
        (byte) 0x45, (byte) 0xcb, (byte) 0x3a, (byte) 0x8f, (byte) 0x95, (byte) 0x16, (byte) 0x04, (byte) 0x28, (byte) 0xaf, (byte) 0xd7, (byte) 0xfb, (byte) 0xca, (byte) 0xbb, (byte) 0x4b, (byte) 0x40, (byte) 0x7e,
    };

    // ------------------------------------------------------------------
    // primitives
    // ------------------------------------------------------------------

    private static long read64(byte[] data, int offset) {
        return ((long) data[offset] & 0xFF)
            | (((long) data[offset + 1] & 0xFF) << 8)
            | (((long) data[offset + 2] & 0xFF) << 16)
            | (((long) data[offset + 3] & 0xFF) << 24)
            | (((long) data[offset + 4] & 0xFF) << 32)
            | (((long) data[offset + 5] & 0xFF) << 40)
            | (((long) data[offset + 6] & 0xFF) << 48)
            | (((long) data[offset + 7] & 0xFF) << 56);
    }

    /** Unsigned 32-bit little-endian read, zero-extended. */
    private static long read32(byte[] data, int offset) {
        return ((long) data[offset] & 0xFF)
            | (((long) data[offset + 1] & 0xFF) << 8)
            | (((long) data[offset + 2] & 0xFF) << 16)
            | (((long) data[offset + 3] & 0xFF) << 24);
    }

    private static long xorshift64(long value, int shift) {
        return value ^ (value >>> shift);
    }

    /** XXH3 avalanche. */
    private static long avalanche(long value) {
        value = xorshift64(value, 37);
        value = value * 0x165667919E3779F9L;
        return xorshift64(value, 32);
    }

    /** XXH64 avalanche. */
    private static long xxh64Avalanche(long value) {
        value ^= value >>> 33;
        value *= P64_2;
        value ^= value >>> 29;
        value *= P64_3;
        value ^= value >>> 32;
        return value;
    }

    /** Low 64 bits of the 128-bit product. */
    private static long mulLow(long a, long b) {
        return a * b;
    }

    /** High 64 bits of the unsigned 128-bit product. */
    private static long mulHigh(long a, long b) {
        return Math.unsignedMultiplyHigh(a, b);
    }

    private static long mul128Fold64(long a, long b) {
        return mulLow(a, b) ^ mulHigh(a, b);
    }

    /** (u32, u32) -> u64 multiplication of the two zero-extended halves. */
    private static long mult32To64(long a, long b) {
        return (a & 0xFFFFFFFFL) * (b & 0xFFFFFFFFL);
    }

    // ------------------------------------------------------------------
    // entry point: returns {low, high}
    // ------------------------------------------------------------------

    static long[] xxh3_128(byte[] input) {
        int len = input.length;
        if (len <= 16) {
            return hash0To16(input);
        } else if (len <= 128) {
            return hash17To128(input);
        } else if (len <= MID_SIZE_MAX) {
            return hash129To240(input);
        } else {
            return hashLong(input);
        }
    }

    // ------------------------------------------------------------------
    // 0..16
    // ------------------------------------------------------------------

    private static long[] hash0To16(byte[] input) {
        int len = input.length;
        if (len > 8) {
            return hash9To16(input);
        } else if (len >= 4) {
            return hash4To8(input);
        } else if (len > 0) {
            return hash1To3(input);
        } else {
            long flipLo = read64(DEFAULT_SECRET, 64) ^ read64(DEFAULT_SECRET, 72);
            long flipHi = read64(DEFAULT_SECRET, 80) ^ read64(DEFAULT_SECRET, 88);
            return new long[]{xxh64Avalanche(flipLo), xxh64Avalanche(flipHi)};
        }
    }

    private static long[] hash1To3(byte[] input) {
        int len = input.length;
        int c1 = input[0] & 0xFF;
        int c2 = input[len >> 1] & 0xFF;
        int c3 = input[len - 1] & 0xFF;
        int inputLo = (c1 << 16) | (c2 << 24) | c3 | (len << 8);
        int inputHi = Integer.rotateLeft(Integer.reverseBytes(inputLo), 13);
        long flipLo = (read32(DEFAULT_SECRET, 0) ^ read32(DEFAULT_SECRET, 4));
        long flipHi = (read32(DEFAULT_SECRET, 8) ^ read32(DEFAULT_SECRET, 12));
        long keyedLo = (inputLo & 0xFFFFFFFFL) ^ flipLo;
        long keyedHi = (inputHi & 0xFFFFFFFFL) ^ flipHi;
        return new long[]{xxh64Avalanche(keyedLo), xxh64Avalanche(keyedHi)};
    }

    private static long[] hash4To8(byte[] input) {
        int len = input.length;
        long seed = 0;
        seed ^= (long) Integer.reverseBytes((int) seed) << 32;
        long lo32 = read32(input, 0);
        long hi32 = read32(input, len - 4);
        long input64 = lo32 + (hi32 << 32);
        long flip = (read64(DEFAULT_SECRET, 16) ^ read64(DEFAULT_SECRET, 24)) + seed;
        long keyed = input64 ^ flip;
        long lo = mulLow(keyed, P64_1 + ((long) len << 2));
        long hi = mulHigh(keyed, P64_1 + ((long) len << 2));
        hi += lo << 1;
        lo ^= hi >>> 3;
        lo = xorshift64(lo, 35) * 0x9FB21C651E98DF25L;
        lo = xorshift64(lo, 28);
        hi = avalanche(hi);
        return new long[]{lo, hi};
    }

    private static long[] hash9To16(byte[] input) {
        int len = input.length;
        long flipLo = (read64(DEFAULT_SECRET, 32) ^ read64(DEFAULT_SECRET, 40));
        long flipHi = (read64(DEFAULT_SECRET, 48) ^ read64(DEFAULT_SECRET, 56));
        long inputLo = read64(input, 0);
        long inputHi = read64(input, len - 8);
        long mulLow = mulLow(inputLo ^ inputHi ^ flipLo, P64_1);
        long mulHigh = mulHigh(inputLo ^ inputHi ^ flipLo, P64_1);
        mulLow += ((long) (len - 1)) << 54;
        inputHi ^= flipHi;
        mulHigh += inputHi + mult32To64(inputHi, P32_2 - 1);
        mulLow ^= Long.reverseBytes(mulHigh);
        long resultLow = mulLow(mulLow, P64_2);
        long resultHi = mulHigh(mulLow, P64_2);
        resultHi += mulHigh * P64_2;
        return new long[]{avalanche(resultLow), avalanche(resultHi)};
    }

    // ------------------------------------------------------------------
    // 17..128 and 129..240 (mix16/mix32)
    // ------------------------------------------------------------------

    private static long mix16B(byte[] input, int inputOff, int secretOff, long seed) {
        long inputLo = read64(input, inputOff) ^ (read64(DEFAULT_SECRET, secretOff) + seed);
        long inputHi = read64(input, inputOff + 8) ^ (read64(DEFAULT_SECRET, secretOff + 8) - seed);
        return mul128Fold64(inputLo, inputHi);
    }

    /** acc = {lo, hi}; mirrors mix32_b. */
    private static void mix32B(long[] acc, byte[] input, int in1Off, int in2Off, int secretOff, long seed) {
        acc[0] += mix16B(input, in1Off, secretOff, seed);
        acc[0] ^= read64(input, in2Off) + read64(input, in2Off + 8);
        acc[1] += mix16B(input, in2Off, secretOff + 16, seed);
        acc[1] ^= read64(input, in1Off) + read64(input, in1Off + 8);
    }

    private static long[] combineMid(long[] acc, int len, long seed) {
        long lo = avalanche(acc[0] + acc[1]);
        long hi = -avalanche(acc[0] * P64_1 + acc[1] * P64_4 + ((long) len - seed) * P64_2);
        return new long[]{lo, hi};
    }

    private static long[] hash17To128(byte[] input) {
        int len = input.length;
        long[] acc = new long[]{len * P64_1, 0};
        if (len > 32) {
            if (len > 64) {
                if (len > 96) {
                    mix32B(acc, input, 48, len - 64, 96, 0);
                }
                mix32B(acc, input, 32, len - 48, 64, 0);
            }
            mix32B(acc, input, 16, len - 32, 32, 0);
        }
        mix32B(acc, input, 0, len - 16, 0, 0);
        return combineMid(acc, len, 0);
    }

    private static long[] hash129To240(byte[] input) {
        int len = input.length;
        int nbRounds = len / 32;
        long[] acc = new long[]{len * P64_1, 0};
        int idx = 0;
        while (idx < 4) {
            mix32B(acc, input, 32 * idx, 32 * idx + 16, 32 * idx, 0);
            idx++;
        }
        acc[0] = avalanche(acc[0]);
        acc[1] = avalanche(acc[1]);
        while (idx < nbRounds) {
            mix32B(acc, input, 32 * idx, 32 * idx + 16, 3 + 32 * (idx - 4), 0);
            idx++;
        }
        // last bytes, seed negated (0 - seed with seed = 0)
        mix32B(acc, input, len - 16, len - 32, SECRET_SIZE_MIN - 17 - 16, 0);
        return combineMid(acc, len, 0);
    }

    // ------------------------------------------------------------------
    // > 240 (long)
    // ------------------------------------------------------------------

    private static void accumulate512(long[] acc, byte[] input, int inputOff, byte[] secret, int secretOff) {
        for (int i = 0; i < 8; i++) {
            long dataVal = read64(input, inputOff + i * 8);
            long dataKey = dataVal ^ read64(secret, secretOff + i * 8);
            acc[i ^ 1] += dataVal;
            acc[i] += mult32To64(dataKey, dataKey >>> 32);
        }
    }

    private static void scrambleAcc(long[] acc, byte[] secret, int secretOff) {
        for (int i = 0; i < 8; i++) {
            long accVal = xorshift64(acc[i], 47);
            accVal ^= read64(secret, secretOff + i * 8);
            acc[i] = accVal * P32_1;
        }
    }

    private static void accumulateLoop(long[] acc, byte[] input, int inputOff, byte[] secret, int secretOff, int nbStripes) {
        for (int i = 0; i < nbStripes; i++) {
            accumulate512(acc, input, inputOff + i * STRIPE_LEN, secret, secretOff + i * SECRET_CONSUME_RATE);
        }
    }

    private static long mergeAccs(long[] acc, byte[] secret, int secretOff, long result) {
        for (int i = 0; i < 4; i++) {
            result += mul128Fold64(
                acc[2 * i] ^ read64(secret, secretOff + 16 * i),
                acc[2 * i + 1] ^ read64(secret, secretOff + 16 * i + 8));
        }
        return avalanche(result);
    }

    private static long[] hashLong(byte[] input) {
        int len = input.length;
        long[] acc = new long[]{P32_3, P64_1, P64_2, P64_3, P64_4, P32_2, P64_5, P32_1};
        int nbStripesPerBlock = (DEFAULT_SECRET.length - STRIPE_LEN) / SECRET_CONSUME_RATE;
        int blockLen = STRIPE_LEN * nbStripesPerBlock;
        int nbBlocks = (len - 1) / blockLen;
        for (int b = 0; b < nbBlocks; b++) {
            accumulateLoop(acc, input, b * blockLen, DEFAULT_SECRET, 0, nbStripesPerBlock);
            scrambleAcc(acc, DEFAULT_SECRET, DEFAULT_SECRET.length - STRIPE_LEN);
        }
        int nbStripes = ((len - 1) - blockLen * nbBlocks) / STRIPE_LEN;
        accumulateLoop(acc, input, nbBlocks * blockLen, DEFAULT_SECRET, 0, nbStripes);
        accumulate512(acc, input, len - STRIPE_LEN, DEFAULT_SECRET,
            DEFAULT_SECRET.length - STRIPE_LEN - SECRET_LASTACC_START);
        long lo = mergeAccs(acc, DEFAULT_SECRET, SECRET_MERGEACCS_START, len * P64_1);
        long hi = mergeAccs(acc, DEFAULT_SECRET,
            DEFAULT_SECRET.length - 64 - SECRET_MERGEACCS_START, ~(len * P64_2));
        return new long[]{lo, hi};
    }
}

# libphext-java

The Java implementation of phext (11-dimensional plain hypertext), at full
parity with the reference implementation,
[libphext-rs](https://github.com/wbic16/libphext-rs) **0.6.1**.
For more information about the phext format, head over to https://phext.io.

License: MIT (as declared by the family repos).

## What's here

Package `io.phext.libphext`:

| Class | Mirrors (libphext-rs) |
|---|---|
| `Coordinate`, `ZCoordinate`, `YCoordinate`, `XCoordinate` | `src/phext.rs` coordinate types — unbounded components (`long`), canonical + URL-encoded forms, strict `tryParse` (the `TryFrom` port) and the lenient `Phext.toCoordinate` |
| `Phext` | `src/phext.rs` — insert / replace / fetch / remove, `phokenize`, explode / implode, merge, subtract, normalize, expand / contract, checksum (xxh3-128, pure Java), manifest, soundex, index / offset, summaries and maps |
| `BinaryScroll` | `src/binary.rs` (feature `binary`) — canonical binary scrolls (`phext-binary:1:base64`), byte-oriented insert / replace, and `ingest` under the base64-verbatim ingestion rule |
| `PhextSeal` | `src/seal.rs` (feature `xz`) — the xz seal: preset 6 + CRC64, via XZ for Java (org.tukaani:xz, pure Java; Java's standard library has no lzma) |
| `Chain`, `Phext.parseChains` / `serializeChains`, `Phext.parseUniverseAddress` | the 0.6.0 chain layer — CHAIN_BREAK 0x02 / CHAIN_CLOSE 0x03, and `$N:` multiverse addressing (bare address = universe 1; `Chain.fetch` returns `""` for misses and present-but-empty scrolls alike) |
| `ScrollPosition`, `PointAddress`, `RangeAddress`, `Phext.parsePointAddress` / `parseRangeAddress`, `Chain.resolvePoint` / `fetchPoint` / `resolveRange` / `fetchRange` | the 0.6.1 line/column addressing layer — point addresses `[$N:]<coordinate>@<line>,<column>[+<offsetChars>]` and range addresses `<point>-<point>` (canonical form: endpoints ascending, `$1:` never written); positions are 1-based caret boundaries, half-open, counted in Unicode scalar values; resolution errors fail (fetch returns `""`), never clamp |

Version: **0.6.1** (`LibPhext.VERSION`).

## Build

Prerequisites: a JDK 21 (`javac` on your PATH). No Maven or Gradle is
required — the build is plain `javac` driven by the family scripts.

1. Clone this repo.
2. Run `scripts/build.sh` — it fetches the one dependency (XZ for Java
   1.9, pinned, SHA-256 verified) into `target/deps/` and compiles the
   library into `target/classes/`, producing `target/libphext-0.6.1.jar`.

## Test

Run `scripts/test.sh` — it builds, compiles the test tree, and runs the
plain main-driven harness (no test-framework dependency). The suites are
ported from the libphext-rs test modules (`test_lib`, `test_phokenize`,
`test_binary`, `test_seal`, `test_chain`), and
`tests/fixtures/chains/` carries the shared 0.6 chain conformance fixtures
(10 cases, 42 fetches) generated from libphext-rs 0.6.0 — every case is
checked for structure, per-scroll hashes, fetch results with found flags,
and a byte-identical parse→serialize round-trip. `tests/fixtures/ranges/`
carries the shared 0.6.1 range conformance fixtures (6 cases, 69 ops)
generated from libphext-rs 0.6.1 — every op (point/range parsing,
resolution, point/range fetching) is checked exactly, including the
resolved-vs-unresolved distinction on fetches.

## Publish

There is no registry publish for this port (no Maven Central channel is
configured for the family); distribution is a git tag, as with the other
non-registry ports.

1. Run `scripts/publish.sh` with no environment variables. It runs the
   test suite and the build, prints the tag it would create
   (`v0.6.1` on the current HEAD), and **stops**.
2. Review, then run `PUBLISH_CONFIRM=1 scripts/publish.sh` to create the
   tag and push it.

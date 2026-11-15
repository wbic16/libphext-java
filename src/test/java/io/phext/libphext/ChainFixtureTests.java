package io.phext.libphext;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;

/**
 * The shared libphext 0.6 chain conformance fixtures
 * ({@code tests/fixtures/chains/}, generated from libphext-rs 0.6.0 — see
 * FIXTURES.md in the fixture set's home). One harness test per case; a case
 * passes only if every check passes:
 * <ol>
 *   <li>input byte length + sha256 against the manifest;</li>
 *   <li>structure: chain count, closed flags, member counts, per-member
 *       byte length + sha256, and the phokenize scroll list (coordinates,
 *       byte lengths, sha256s, in order);</li>
 *   <li>byte-identical round-trip: serialize(parse(input)) == input;</li>
 *   <li>every fetch: byte-exact result text, plus the manifest's
 *       {@code found} flag reproduced from this port's phokenize output.</li>
 * </ol>
 */
public final class ChainFixtureTests {
    private ChainFixtureTests() {
    }

    private static String sha256Hex(byte[] bytes) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
        StringBuilder out = new StringBuilder();
        for (byte b : digest) {
            out.append(String.format("%02x", b));
        }
        return out.toString();
    }

    public static void register() {
        Path fixturesDir = TestMain.repoRoot.resolve("tests/fixtures/chains");
        List<String> caseNames;
        try {
            String tsv = Files.readString(fixturesDir.resolve("cases.tsv"), StandardCharsets.UTF_8);
            caseNames = new java.util.ArrayList<>();
            for (String line : tsv.split("\n")) {
                if (line.isBlank() || line.startsWith("case\t")) {
                    continue;
                }
                caseNames.add(line.split("\t")[0]);
            }
        } catch (Exception e) {
            TestMain.test("ChainFixtureTests", "cases.tsv readable", () -> {
                throw e;
            });
            return;
        }
        A.eq(10, caseNames.size(), "fixture case count");
        for (String caseName : caseNames) {
            TestMain.test("ChainFixtureTests", caseName, () -> runCase(fixturesDir.resolve(caseName)));
        }
    }

    private static void runCase(Path caseDir) throws Exception {
        Map<String, Object> manifest = MiniJson.object(
            MiniJson.parse(Files.readString(caseDir.resolve("manifest.json"), StandardCharsets.UTF_8)));
        String caseName = MiniJson.str(manifest, "case");

        // 1. input integrity
        byte[] inputBytes = Files.readAllBytes(caseDir.resolve(MiniJson.str(manifest, "input_file")));
        A.eq(MiniJson.num(manifest, "input_bytes"), inputBytes.length, caseName + " input_bytes");
        A.eq(MiniJson.str(manifest, "input_sha256"), sha256Hex(inputBytes), caseName + " input_sha256");
        String input = new String(inputBytes, StandardCharsets.UTF_8);

        // 2. structure
        List<Chain> chains = Phext.parseChains(input);
        List<Object> expectedChains = MiniJson.array(manifest.get("chains"));
        A.eq(expectedChains.size(), chains.size(), caseName + " chain count");
        for (int ci = 0; ci < expectedChains.size(); ci++) {
            Map<String, Object> expectedChain = MiniJson.object(expectedChains.get(ci));
            Chain chain = chains.get(ci);
            A.eq(MiniJson.bool(expectedChain, "closed"), chain.closed, caseName + " chain " + ci + " closed");
            List<Object> expectedMembers = MiniJson.array(expectedChain.get("members"));
            A.eq(expectedMembers.size(), chain.members.size(), caseName + " chain " + ci + " member count");
            for (int mi = 0; mi < expectedMembers.size(); mi++) {
                Map<String, Object> expectedMember = MiniJson.object(expectedMembers.get(mi));
                byte[] memberBytes = chain.members.get(mi).getBytes(StandardCharsets.UTF_8);
                A.eq(MiniJson.num(expectedMember, "bytes"), memberBytes.length,
                    caseName + " member " + ci + "/" + mi + " bytes");
                A.eq(MiniJson.str(expectedMember, "sha256"), sha256Hex(memberBytes),
                    caseName + " member " + ci + "/" + mi + " sha256");
                List<Object> expectedScrolls = MiniJson.array(expectedMember.get("scrolls"));
                List<PositionedScroll> scrolls = Phext.phokenize(chain.members.get(mi));
                A.eq(MiniJson.num(expectedMember, "scroll_count"), scrolls.size(),
                    caseName + " member " + ci + "/" + mi + " scroll_count");
                A.eq(expectedScrolls.size(), scrolls.size(),
                    caseName + " member " + ci + "/" + mi + " scroll list size");
                for (int si = 0; si < expectedScrolls.size(); si++) {
                    Map<String, Object> expectedScroll = MiniJson.object(expectedScrolls.get(si));
                    PositionedScroll scroll = scrolls.get(si);
                    byte[] scrollBytes = scroll.scroll.getBytes(StandardCharsets.UTF_8);
                    A.eq(MiniJson.str(expectedScroll, "c"), scroll.coord.toString(),
                        caseName + " scroll " + ci + "/" + mi + "/" + si + " coordinate");
                    A.eq(MiniJson.num(expectedScroll, "b"), scrollBytes.length,
                        caseName + " scroll " + ci + "/" + mi + "/" + si + " bytes");
                    A.eq(MiniJson.str(expectedScroll, "s"), sha256Hex(scrollBytes),
                        caseName + " scroll " + ci + "/" + mi + "/" + si + " sha256");
                }
            }
        }

        // 3. byte-identical round-trip
        byte[] reserialized = Phext.serializeChains(chains).getBytes(StandardCharsets.UTF_8);
        A.eqBytes(inputBytes, reserialized, caseName + " round-trip");

        // 4. fetches (byte-exact text + found flag from phokenize)
        List<Object> fetches = MiniJson.array(manifest.get("fetches"));
        for (int fi = 0; fi < fetches.size(); fi++) {
            Map<String, Object> fetch = MiniJson.object(fetches.get(fi));
            int chainIndex = (int) MiniJson.num(fetch, "chain_index");
            String address = MiniJson.str(fetch, "address");
            String result = chains.get(chainIndex).fetch(address);
            byte[] resultBytes = result.getBytes(StandardCharsets.UTF_8);
            byte[] expectedBytes = Files.readAllBytes(caseDir.resolve(MiniJson.str(fetch, "expected_file")));
            A.eqBytes(expectedBytes, resultBytes, caseName + " fetch " + fi + " " + address);
            A.eq(MiniJson.num(fetch, "expected_bytes"), resultBytes.length, caseName + " fetch " + fi + " bytes");
            A.eq(MiniJson.str(fetch, "expected_sha256"), sha256Hex(resultBytes), caseName + " fetch " + fi + " sha256");

            Phext.UniverseAddress parsed = Phext.parseUniverseAddress(address);
            boolean found = false;
            if (parsed != null && parsed.universe() >= 1
                && parsed.universe() <= chains.get(chainIndex).members.size()) {
                String member = chains.get(chainIndex).members.get((int) parsed.universe() - 1);
                for (PositionedScroll ps : Phext.phokenize(member)) {
                    if (ps.coord.equals(parsed.coordinate())) {
                        found = true;
                        break;
                    }
                }
            }
            A.eq(MiniJson.bool(fetch, "found"), found, caseName + " fetch " + fi + " found flag");
        }
    }
}

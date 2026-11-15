package io.phext.libphext;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

/** Port of libphext-rs {@code src/test_chain.rs} (12 tests). */
public final class ChainTests {
    private ChainTests() {
    }

    private static final String CB = "\u0002";
    private static final String CC = "\u0003";

    private static void roundtrip(String stream) {
        List<Chain> chains = Phext.parseChains(stream);
        A.eq(stream, Phext.serializeChains(chains), "round trip " + A.show(stream));
    }

    public static void register() {
        TestMain.test("ChainTests", "chain_free_input_is_one_unclosed_chain", () -> {
            String doc = "alpha\u0017beta\u0001gamma";
            List<Chain> chains = Phext.parseChains(doc);
            A.eq(1, chains.size());
            A.eq(List.of(doc), chains.get(0).members);
            A.isFalse(chains.get(0).closed);
            A.eq(doc, Phext.serializeChains(chains));
            A.eq("beta", Phext.fetch(doc, Phext.toCoordinate("1.1.1/1.1.1/1.1.2")));
            A.eq("gamma", Phext.fetch(doc, Phext.toCoordinate("2.1.1/1.1.1/1.1.1")));
            roundtrip(doc);
        });

        TestMain.test("ChainTests", "two_phexts_chained_and_closed_round_trip", () -> {
            String stream = "edition one" + CB + "edition two" + CC;
            List<Chain> chains = Phext.parseChains(stream);
            A.eq(1, chains.size());
            A.eq(List.of("edition one", "edition two"), chains.get(0).members);
            A.isTrue(chains.get(0).closed);
            roundtrip(stream);
        });

        TestMain.test("ChainTests", "unclosed_chain_at_eof_reports_truncated", () -> {
            List<Chain> chains = Phext.parseChains("one" + CB + "two");
            A.eq(1, chains.size());
            A.eq(List.of("one", "two"), chains.get(0).members);
            A.isFalse(chains.get(0).closed, "a chain ending at EOF must report unclosed");
            roundtrip("one" + CB + "two");
        });

        TestMain.test("ChainTests", "two_chains_in_one_stream_parse_as_two_units", () -> {
            String stream = "a" + CB + "b" + CC + "c" + CB + "d" + CC;
            List<Chain> chains = Phext.parseChains(stream);
            A.eq(2, chains.size());
            A.eq(List.of("a", "b"), chains.get(0).members);
            A.isTrue(chains.get(0).closed);
            A.eq(List.of("c", "d"), chains.get(1).members);
            A.isTrue(chains.get(1).closed);
            roundtrip(stream);

            List<Chain> mixed = Phext.parseChains("a" + CC + "b");
            A.eq(2, mixed.size());
            A.eq(List.of("a"), mixed.get(0).members);
            A.isTrue(mixed.get(0).closed);
            A.eq(List.of("b"), mixed.get(1).members);
            A.isFalse(mixed.get(1).closed);
            roundtrip("a" + CC + "b");
        });

        TestMain.test("ChainTests", "chained_members_carry_full_phexts", () -> {
            String first = "root\u0017second scroll";
            String second = Phext.insert("", Phext.toCoordinate("1.1.1/1.1.1/1.1.3"), "third edition");
            String stream = first + CB + second + CC;
            List<Chain> chains = Phext.parseChains(stream);
            A.eq(1, chains.size());
            A.eq(2, chains.get(0).members.size());
            A.eq("second scroll", Phext.fetch(chains.get(0).members.get(0), Phext.toCoordinate("1.1.1/1.1.1/1.1.2")));
            A.eq("third edition", Phext.fetch(chains.get(0).members.get(1), Phext.toCoordinate("1.1.1/1.1.1/1.1.3")));
            A.eq("", Phext.fetch(chains.get(0).members.get(1), Phext.toCoordinate("1.1.1/1.1.1/1.1.1")));
            roundtrip(stream);
        });

        TestMain.test("ChainTests", "editions_in_succession_read_in_order", () -> {
            String stream = "v1" + CB + "v2" + CB + "v3" + CC;
            List<Chain> chains = Phext.parseChains(stream);
            A.eq(1, chains.size());
            A.eq(List.of("v1", "v2", "v3"), chains.get(0).members);
            A.isTrue(chains.get(0).closed);
            roundtrip(stream);
        });

        TestMain.test("ChainTests", "empty_and_break_only_streams_round_trip", () -> {
            List<Chain> chains = Phext.parseChains("");
            A.eq(1, chains.size());
            A.eq(List.of(""), chains.get(0).members);
            A.isFalse(chains.get(0).closed);
            roundtrip("");

            chains = Phext.parseChains(CC);
            A.eq(1, chains.size());
            A.eq(List.of(""), chains.get(0).members);
            A.isTrue(chains.get(0).closed);
            roundtrip(CC);

            A.eq(List.of("a", ""), Phext.parseChains("a" + CB).get(0).members);
            A.eq(List.of("", "b"), Phext.parseChains(CB + "b").get(0).members);
            A.eq(3, Phext.parseChains(CB + CB).get(0).members.size());
            roundtrip("a" + CB);
            roundtrip(CB + "b");
            roundtrip(CB + CB);
            roundtrip(CB);

            chains = Phext.parseChains("a" + CC + CC);
            A.eq(2, chains.size());
            A.isTrue(chains.get(0).closed && chains.get(1).closed);
            A.eq(List.of(""), chains.get(1).members);
            roundtrip("a" + CC + CC);
        });

        TestMain.test("ChainTests", "serialize_from_constructed_chains", () -> {
            Chain chain = new Chain();
            A.isFalse(chain.closed);
            A.eq("", chain.serialize());
            chain.members.add("v1");
            chain.members.add("v2");
            A.eq("v1" + CB + "v2", chain.serialize());
            chain.closed = true;
            A.eq("v1" + CB + "v2" + CC, chain.serialize());
            A.eq("v1" + CB + "v2" + CC, Phext.serializeChains(List.of(chain)));
        });

        TestMain.test("ChainTests", "canon_shaped_documents_parse_unchanged", () -> {
            String doc = Files.readString(TestMain.repoRoot.resolve("regression-1-helios.phext"), StandardCharsets.UTF_8);
            A.isFalse(doc.contains(CB));
            A.isFalse(doc.contains(CC));
            List<Chain> chains = Phext.parseChains(doc);
            A.eq(1, chains.size());
            A.eq(List.of(doc), chains.get(0).members);
            A.isFalse(chains.get(0).closed);
            A.eq(doc, Phext.serializeChains(chains));
        });

        TestMain.test("ChainTests", "chain_layer_does_not_touch_member_structure", () -> {
            List<Chain> chains = Phext.parseChains("x\u0017y" + CB + "z\u0017w" + CC);
            A.eq(List.of("x\u0017y", "z\u0017w"), chains.get(0).members);
            A.eq(2, Phext.phokenize(chains.get(0).members.get(1)).size());
            roundtrip("x\u0017y" + CB + "z\u0017w" + CC);
        });

        TestMain.test("ChainTests", "multiverse_addressing", () -> {
            Chain chain = new Chain(List.of("U1 home", "U2 home"), true);
            A.eq("U1 home", chain.fetch("1.1.1/1.1.1/1.1.1"));
            A.eq("U1 home", chain.fetch("$1:1.1.1/1.1.1/1.1.1"));
            A.eq("U2 home", chain.fetch("$2:1.1.1/1.1.1/1.1.1"));
            A.eq("", chain.fetch("$3:1.1.1/1.1.1/1.1.1"));
            A.isNull(Phext.parseUniverseAddress("$0:1.1.1/1.1.1/1.1.1"), "$0: must be invalid");
            Phext.UniverseAddress parsed = Phext.parseUniverseAddress("$12:2.1.1/1.1.1/1.1.1");
            A.notNull(parsed, "$12: parses");
            A.eq(12, parsed.universe());
        });

        TestMain.test("ChainTests", "adjacent_closes_are_two_empty_chains", () -> {
            List<Chain> chains = Phext.parseChains(CC + CC);
            A.eq(2, chains.size());
            A.isTrue(chains.get(0).closed && chains.get(1).closed);
            A.eq(CC + CC, Phext.serializeChains(chains));
        });
    }
}

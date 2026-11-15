package io.phext.libphext;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A chain: an ordered series of complete phexts within one stream, joined
 * by CHAIN_BREAK (0x02) and optionally terminated by CHAIN_CLOSE (0x03).
 * The chain layer is a stream-level layer, one rung above the library
 * break: the nine dimension breaks structure a single phext, while the two
 * chain bytes structure a stream of phexts. Each member is a whole phext,
 * worked with the ordinary phext functions; read in succession, a chain's
 * members are editions in sequence — the chain is how a phext stream
 * encodes time.
 *
 * <p>{@code closed} records how the chain ended: true when a CHAIN_CLOSE
 * terminated it, false when the stream simply ran out (EOF). A chain that
 * ends at EOF is unclosed — possibly truncated — and is never silently
 * promoted to closed. A stream with no chain bytes at all is a single
 * unclosed chain holding one member phext: every pre-0.6 phext reads this
 * way.
 *
 * <p>Mirrors {@code Chain} in libphext-rs {@code src/phext.rs} (0.6.0).
 */
public final class Chain {
    public List<String> members;
    public boolean closed;

    public Chain() {
        this(new ArrayList<>(), false);
    }

    public Chain(List<String> members, boolean closed) {
        this.members = members;
        this.closed = closed;
    }

    /**
     * Serializes this chain alone: its members joined by CHAIN_BREAK, with
     * a trailing CHAIN_CLOSE when the chain is closed. See
     * {@link Phext#serializeChains} for whole streams.
     */
    public String serialize() {
        StringBuilder result = new StringBuilder();
        boolean first = true;
        for (String member : members) {
            if (!first) {
                result.append(Phext.CHAIN_BREAK);
            }
            first = false;
            result.append(member);
        }
        if (closed) {
            result.append(Phext.CHAIN_CLOSE);
        }
        return result.toString();
    }

    /**
     * Fetches a scroll by multiverse address: "$N:coord", or a bare
     * coordinate (universe 1). Out-of-range universes, malformed addresses
     * and missing scrolls yield an empty string, as
     * {@link Phext#fetch} does — the same value as a present-but-empty
     * scroll. There is no error channel in the fetch API.
     */
    public String fetch(String address) {
        Phext.UniverseAddress parsed = Phext.parseUniverseAddress(address);
        if (parsed == null) {
            return "";
        }
        long index = parsed.universe() - 1;
        if (index < 0 || index >= members.size()) {
            return "";
        }
        return Phext.fetch(members.get((int) index), parsed.coordinate());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Chain other)) return false;
        return closed == other.closed && members.equals(other.members);
    }

    @Override
    public int hashCode() {
        return Objects.hash(members, closed);
    }

    @Override
    public String toString() {
        return "Chain{members=" + members.size() + ", closed=" + closed + "}";
    }
}

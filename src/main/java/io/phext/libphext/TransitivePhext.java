package io.phext.libphext;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Branching hash-map scroll store: a {@link BranchingMap} instantiated
 * at depth 9 — one hash-map layer per coordinate dimension (library,
 * shelf, series, collection, volume, book, chapter, section, scroll),
 * leaves holding scroll text. The depth is derived from
 * {@link Coordinate#toComponents()}, never hardcoded.
 *
 * <p>Fetch, insert, and remove touch exactly one map per dimension: no
 * sort, no scan, no binary search, and no dependence on how many
 * scrolls the phext holds. Serialization walks the hierarchy in sorted
 * key order at every level, which is coordinate order, so
 * {@link #serialize()} is byte-identical to the (normalized) string
 * path over any edit sequence.
 *
 * <p>Edit semantics are the string path's own, exactly, as in
 * {@link DynamicPhext}: insert concatenates old text before new,
 * replace overwrites, fetch returns the scroll's text or {@code ""}.
 */
public final class TransitivePhext {
    private final int depth;
    private BranchingMap<String> map;

    public TransitivePhext() {
        this("");
    }

    public TransitivePhext(String buffer) {
        this.depth = new Coordinate().toComponents().length;
        this.map = new BranchingMap<>(depth);
        if (buffer != null && !buffer.isEmpty()) {
            load(buffer);
        }
    }

    public int depth() {
        return depth;
    }

    // -- loading and serialization --

    public void load(String buffer) {
        map = new BranchingMap<>(depth);
        for (PositionedScroll ps : Phext.phokenize(buffer)) {
            map.put(ps.coord.toComponents(), ps.scroll);
        }
    }

    public List<PositionedScroll> scrolls() {
        List<PositionedScroll> out = new ArrayList<>();
        for (Map.Entry<long[], String> e : map.items()) {
            out.add(new PositionedScroll(Coordinate.fromComponents(e.getKey()), e.getValue()));
        }
        return out;
    }

    public String serialize() {
        return Phext.dephokenize(scrolls());
    }

    // -- lookup: one map lookup per dimension, independent of phext size --

    public String fetch(Coordinate coord) {
        String value = map.fetch(coord.toComponents());
        return value == null ? "" : value;
    }

    public boolean contains(Coordinate coord) {
        return map.contains(coord.toComponents());
    }

    public int size() {
        return map.size();
    }

    // -- edits: string-path update semantics, by hash walk --

    public void update(Coordinate coord, String text, boolean overwrite) {
        long[] key = coord.toComponents();
        if (text.isEmpty()) {
            if (overwrite) {
                map.remove(key);
            }
            return;
        }
        if (!overwrite) {
            String existing = map.fetch(key);
            if (existing != null) {
                text = existing + text;
            }
        }
        map.put(key, text);
    }

    public void insert(Coordinate coord, String text) {
        update(coord, text, false);
    }

    public void replace(Coordinate coord, String text) {
        update(coord, text, true);
    }

    public void remove(Coordinate coord) {
        map.remove(coord.toComponents());
    }

    /** A hash hierarchy has no tail; append is insert. Kept for API parity with {@link DynamicPhext}. */
    public void append(Coordinate coord, String text) {
        insert(coord, text);
    }
}

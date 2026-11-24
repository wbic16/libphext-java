package io.phext.libphext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * A hash map of dimension {@code depth} branching into hash maps of one
 * fewer dimension, recursively, down to leaves holding values — the
 * transitive branching structure of the original Phext Notepad
 * ("hash maps of hash maps transitively... in 9 layers").
 *
 * <p>One recursive concept: every operation is defined recursively over
 * the hierarchy, one {@link HashMap} lookup per level. The depth is a
 * parameter of the instantiation, not of the structure — keys are
 * {@code long[]} tuples of exactly {@code depth} components.
 *
 * @param <V> the leaf value type
 */
public final class BranchingMap<V> {
    private final int depth;
    private final HashMap<Long, Object> root = new HashMap<>();
    private int count;

    public BranchingMap(int depth) {
        if (depth < 1) {
            throw new IllegalArgumentException("depth must be >= 1");
        }
        this.depth = depth;
    }

    public int depth() {
        return depth;
    }

    public int size() {
        return count;
    }

    private void check(long[] key) {
        if (key.length != depth) {
            throw new IllegalArgumentException(
                "key has " + key.length + " components, map depth is " + depth);
        }
    }

    // -- fetch: one lookup per dimension, recursively --

    public V fetch(long[] key) {
        check(key);
        return fetch(root, key, 0);
    }

    @SuppressWarnings("unchecked")
    private V fetch(HashMap<Long, Object> node, long[] key, int level) {
        Object child = node.get(key[level]);
        if (child == null) {
            return null;
        }
        if (level == depth - 1) {
            return (V) child;
        }
        return fetch((HashMap<Long, Object>) child, key, level + 1);
    }

    public boolean contains(long[] key) {
        return fetch(key) != null;
    }

    // -- put: descend recursively, branching new maps as needed --

    /** Stores {@code value} at {@code key}; returns true when a new leaf was created. */
    public boolean put(long[] key, V value) {
        check(key);
        boolean created = put(root, key, 0, value);
        if (created) {
            count += 1;
        }
        return created;
    }

    @SuppressWarnings("unchecked")
    private boolean put(HashMap<Long, Object> node, long[] key, int level, V value) {
        long component = key[level];
        if (level == depth - 1) {
            boolean created = !node.containsKey(component);
            node.put(component, value);
            return created;
        }
        HashMap<Long, Object> child = (HashMap<Long, Object>) node.get(component);
        if (child == null) {
            child = new HashMap<>();
            node.put(component, child);
        }
        return put(child, key, level + 1, value);
    }

    // -- remove: recursive, pruning branches left empty --

    public boolean remove(long[] key) {
        check(key);
        boolean removed = remove(root, key, 0);
        if (removed) {
            count -= 1;
        }
        return removed;
    }

    @SuppressWarnings("unchecked")
    private boolean remove(HashMap<Long, Object> node, long[] key, int level) {
        long component = key[level];
        if (level == depth - 1) {
            if (node.containsKey(component)) {
                node.remove(component);
                return true;
            }
            return false;
        }
        HashMap<Long, Object> child = (HashMap<Long, Object>) node.get(component);
        if (child == null) {
            return false;
        }
        boolean removed = remove(child, key, level + 1);
        if (removed && child.isEmpty()) {
            node.remove(component);
        }
        return removed;
    }

    // -- items: recursive traversal, sorted at every level, which is
    //    lexicographic (coordinate) order over the full key --

    public List<Map.Entry<long[], V>> items() {
        List<Map.Entry<long[], V>> out = new ArrayList<>();
        items(root, new long[depth], 0, out);
        return out;
    }

    @SuppressWarnings("unchecked")
    private void items(HashMap<Long, Object> node, long[] prefix, int level,
                       List<Map.Entry<long[], V>> out) {
        for (Map.Entry<Long, Object> e : new TreeMap<>(node).entrySet()) {
            prefix[level] = e.getKey();
            if (level == depth - 1) {
                out.add(Map.entry(prefix.clone(), (V) e.getValue()));
            } else {
                items((HashMap<Long, Object>) e.getValue(), prefix, level + 1, out);
            }
        }
    }
}

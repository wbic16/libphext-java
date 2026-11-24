package io.phext.libphext;

import java.util.ArrayList;
import java.util.List;

/**
 * Dynamic-array scroll store.
 *
 * <p>The {@link Phext} buffer API is buffer-in/buffer-out on immutable
 * strings: every insert/replace/remove re-scans and re-serializes the
 * whole buffer — a sequence of N edits on a growing phext costs
 * O(N × size). DynamicPhext pays the phokenize once: it holds the
 * scrolls in a capacity-managed dynamic array ({@code PositionedScroll[]}
 * backing with explicit capacity) and mutates it in place, serializing
 * on demand through {@link Phext#dephokenize}.
 *
 * <p>Capacity discipline (CLRS problem 3-5, the dynamic-table strategy):
 * start at capacity 1, double when full, and shrink only when the load
 * drops to one quarter — never at half full, which thrashes at a resize
 * boundary. Resize events are instrumented ({@link #resizeCount()},
 * {@link #elementsCopied()}) so tests can assert the amortized behavior.
 *
 * <p>Edit semantics are the string path's own, exactly: insert at an
 * existing coordinate concatenates old text before new, replace
 * overwrites, fetch returns the scroll's text or {@code ""}, and
 * serialization is byte-identical to the (normalized) string path over
 * any edit sequence. The array is kept in strictly increasing
 * coordinate order.
 */
public final class DynamicPhext {
    private static final int MIN_CAPACITY = 1;

    private final int shrinkDivisor;
    private PositionedScroll[] data;
    private int capacity;
    private int n;
    private long resizeCount;
    private long elementsCopied;

    public DynamicPhext() {
        this("");
    }

    public DynamicPhext(String buffer) {
        this(buffer, 4);
    }

    /**
     * @param shrinkDivisor the load denominator at which the array halves:
     *     the correct strategy is 4 (quarter full). The value 2 reproduces
     *     the textbook's bad half-full strategy and exists so the thrash
     *     test can measure it; production use should never pass it.
     */
    public DynamicPhext(String buffer, int shrinkDivisor) {
        if (shrinkDivisor < 2) {
            throw new IllegalArgumentException("shrinkDivisor must be >= 2");
        }
        this.shrinkDivisor = shrinkDivisor;
        this.capacity = MIN_CAPACITY;
        this.data = new PositionedScroll[this.capacity];
        this.n = 0;
        if (buffer != null && !buffer.isEmpty()) {
            load(buffer);
        }
    }

    // -- capacity machinery --

    private void resize(int newCapacity) {
        PositionedScroll[] newData = new PositionedScroll[newCapacity];
        System.arraycopy(data, 0, newData, 0, n);
        data = newData;
        capacity = newCapacity;
        resizeCount += 1;
        elementsCopied += n;
    }

    private void growIfFull() {
        if (n == capacity) {
            resize(2 * capacity);
        }
    }

    private void shrinkIfSparse() {
        // Quarter-full rule: after halving, the load is at most 1/2, so
        // the next grow is at least capacity/4 appends away — no thrash.
        if (capacity > MIN_CAPACITY && n <= capacity / shrinkDivisor) {
            resize(Math.max(MIN_CAPACITY, capacity / 2));
        }
    }

    public int capacity() {
        return capacity;
    }

    public long resizeCount() {
        return resizeCount;
    }

    public long elementsCopied() {
        return elementsCopied;
    }

    public void resetStats() {
        resizeCount = 0;
        elementsCopied = 0;
    }

    // -- loading and serialization --

    public void load(String buffer) {
        capacity = MIN_CAPACITY;
        data = new PositionedScroll[capacity];
        n = 0;
        resetStats();
        for (PositionedScroll ps : Phext.phokenize(buffer)) {
            push(ps);
        }
    }

    public List<PositionedScroll> scrolls() {
        List<PositionedScroll> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            out.add(data[i]);
        }
        return out;
    }

    public String serialize() {
        return Phext.dephokenize(scrolls());
    }

    // -- lookup: binary search over the coordinate-ordered array --

    /** Returns {index, found?1:0}; when not found, index is the insertion point. */
    private int[] find(Coordinate coord) {
        int lo = 0;
        int hi = n;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            int cmp = data[mid].coord.compareTo(coord);
            if (cmp < 0) {
                lo = mid + 1;
            } else if (cmp > 0) {
                hi = mid;
            } else {
                return new int[]{mid, 1};
            }
        }
        return new int[]{lo, 0};
    }

    public String fetch(Coordinate coord) {
        int[] r = find(coord);
        return r[1] == 1 ? data[r[0]].scroll : "";
    }

    public boolean contains(Coordinate coord) {
        return find(coord)[1] == 1;
    }

    public int size() {
        return n;
    }

    // -- edits: string-path update semantics, in place --

    private void push(PositionedScroll ps) {
        growIfFull();
        data[n] = ps;
        n += 1;
    }

    /** Amortized O(1) tail append; the coordinate must extend the tail. */
    public void append(Coordinate coord, String text) {
        if (n > 0 && coord.compareTo(data[n - 1].coord) <= 0) {
            throw new IllegalArgumentException(
                "append requires a coordinate past the current tail");
        }
        if (text.isEmpty()) {
            return;
        }
        push(new PositionedScroll(coord.copy(), text));
    }

    public void update(Coordinate coord, String text, boolean overwrite) {
        int[] r = find(coord);
        int index = r[0];
        if (r[1] == 1) {
            if (text.isEmpty()) {
                if (overwrite) {
                    removeAt(index);
                }
                return;
            }
            if (!overwrite) {
                text = data[index].scroll + text;
            }
            data[index] = new PositionedScroll(coord.copy(), text);
            return;
        }
        if (text.isEmpty()) {
            return;
        }
        growIfFull();
        System.arraycopy(data, index, data, index + 1, n - index);
        data[index] = new PositionedScroll(coord.copy(), text);
        n += 1;
    }

    public void insert(Coordinate coord, String text) {
        update(coord, text, false);
    }

    public void replace(Coordinate coord, String text) {
        update(coord, text, true);
    }

    private void removeAt(int index) {
        System.arraycopy(data, index + 1, data, index, n - index - 1);
        n -= 1;
        data[n] = null;
        shrinkIfSparse();
    }

    public void remove(Coordinate coord) {
        int[] r = find(coord);
        if (r[1] == 1) {
            removeAt(r[0]);
        }
    }
}

package com.aivean.grid2d;

/**
 * On thread safety:
 * <ul>
 *  <li>not thread safe for both writing AND READING</li>
 *  <li>to read from another thread, create a {@link Grid#createReadOnlyView()}</li>
 *  <li>when new keys are added, previously created views (GridReadOnlyView) might not see the added elements (but removed and changed elements should be visible)</li>
 *  <li>access has to be explicitly synchronized to write from multiple threads</li>
 * </ul>
 *
 * @author Ivan Zaitsev https://github.com/Aivean/grid2d
 */
public class Grid<T> implements GridReadOnlyView<T> {
    public static final int BITS = 5;
    public static final int SIZE = 1 << BITS;
    public static final int MASK = SIZE - 1;

    private Node root = null;
    private Node cache = null;
    private int i0;
    private int j0;
    private int depth;

    // TODO  don't forget to unset the cache when grid is resized (both ways)
    // cachei0 and cachej0 are in already adjusted coords
    // -1 indicates unset cache
    private int cachei0 = -1;
    private int cachej0 = -1;

    static class Node {
        Object[] c = new Object[2];
        int keys;
        int n;

        /**
         * @return index or -1 if not found
         */
        int firstNonNullIndex() {
            if (n <= 2) return keys & MASK;
            else {
                for (int j = 0; j < SIZE; j++) {
                    if (c[j] != null) return j;
                }
                return -1;
            }
        }

        Object getOrNull(int i) {
            if (n <= 2) {
                if (n > 0 && (keys & MASK) == i) return c[0];
                else if (n == 2 && ((keys >>> BITS) & MASK) == i) return c[1];
                else return null;
            } else return c[i];
        }

        Object setNotNull(int i, Object v) {
            Object tmp;
            if (n == 0) {
                keys = i;
                n = 1;
                c[0] = v;
                return null;
            } else if (n == 1) {
                if (keys == i) {
                    tmp = c[0];
                    c[0] = v;
                    return tmp;
                } else if (keys < i) {
                    keys |= i << BITS;
                    n++;
                    c[1] = v;
                    return null;
                } else { /* keys > i */
                    keys = (keys << BITS) | i;
                    c[1] = c[0];
                    c[0] = v;
                    n++;
                    return null;
                }
            } else if (n == 2) {
                if ((keys & MASK) == i) {
                    tmp = c[0];
                    c[0] = v;
                    return tmp;
                } else if ((keys >>> BITS & MASK) == i) {
                    tmp = c[1];
                    c[1] = v;
                    return tmp;
                } else {
                    /* promote */
                    Object[] c0 = c;
                    c = new Object[SIZE];
                    c[keys & MASK] = c0[0];
                    c[(keys >>> BITS) & MASK] = c0[1];
                    keys = 0;
                }
            }

            tmp = c[i];
            if (tmp == null) n++;
            c[i] = v;
            return tmp;
        }

        Object setNull(int i) {
            Object tmp = getOrNull(i);
            if (tmp == null) return null;

            /* n cannot be 0 here or tmp would be null */
            if (n == 1) {
                c[0] = null;
                keys = 0;
            } else if (n == 2) {
                if ((keys & MASK) == i) { /* 0 el */
                    c[0] = c[1];
                    c[1] = null;
                    keys >>>= BITS;
                } else {  /*  1st el */
                    c[1] = null;
                    keys &= MASK;
                }
            } else {
                c[i] = null;
            }

            n--;

            if (n == 2) { // demote
                int n0 = 0;
                Object[] c0 = new Object[2];
                keys = 0;
                int bits = 0;
                for (int j = 0; j < SIZE; j++) {
                    if (c[j] != null) {
                        c0[n0++] = c[j];
                        keys |= j << bits;
                        bits += BITS;
                    }
                }
                c = c0;
            }

            return tmp;
        }

        Node getOrCreateChildNode(int i) {
            Object el = getOrNull(i);
            if (el != null) return (Node) el;

            Node node = new Node();
            setNotNull(i, node);

            return node;
        }
    }

    public int getDepth() {
        return depth;
    }

    public void set(int i, int j, T value) {
        if (value != null) {
            ensureBoundaries(i, j);
            Node row = getOrCreateRow(i, j, 0);
            row.setNotNull(j & MASK, value);
        } else {
            Node row = getRowOrNull(i, j);
            if (row != null && row.setNull(j & MASK) != null && row.n == 0) {
                if (clearNodes(i, j)) {
                    collapseHierarchy();
                }
            }
        }
    }

    private void collapseHierarchy() {
        while (this.depth > 0 && this.root.n <= 1) {
            if (this.root.n == 0) {
                clear();
                return;
            }

            int i = this.root.firstNonNullIndex();
            if (i < 0) throw new IllegalStateException("WTF? Should have at least one non-null element");
            Node col = (Node) this.root.getOrNull(i);

            if (col.n > 1) return;
            if (col.n == 0) { // collection has only one empty column
                clear();
                return;
            }
            // col.n == 1
            int j = col.firstNonNullIndex();
            if (j < 0) throw new IllegalStateException("WTF? Should have at least one non-null element");

            if (depth == 1) return; /* cannot do anything, there's still one element left  */
            int shift = BITS * (depth - 1);
            i0 += i << shift;
            j0 += j << shift;
            this.root = (Node) col.getOrNull(j);
            this.depth--;
        }
    }

    public void clear() {
        this.cache = null;
        this.cachei0 = -1;
        this.depth = 0;
        this.root = null;
    }

    /**
     * Note: assumes that there is a null at the end of the path!
     * otherwise will throw cast exception
     *
     * @param i (unshifted) target index
     * @param j (unshifted) target index
     * @return
     */
    private boolean clearNodes(int i, int j) {
        int shift = 32 - depth * BITS;
        if (clearNodesRec(root, (i - i0) << shift, (j - j0) << shift)) {
            this.cache = null;
            this.cachei0 = -1;
            return true;
        }
        return false;
    }

    private boolean clearNodesRec(Node parent, int i, int j) {
        int idx = (i >>> (32 - BITS)) & MASK;
        Node child = (Node) parent.getOrNull(idx); // cast won't fail if last el is null
        if (child != null) {
            boolean res = clearNodesRec(child, j, i << BITS);
            if (child.n == 0) {
                parent.setNull(idx);
                return true;
            }
            return res;
        }
        return false;
    }

    private Node getRowOrNull(int i, int j) {
        if (!inRange(i, j)) return null;
        i -= i0;
        j -= j0;

        if (this.depth > 1 && this.cachei0 != -1 /* is this needed?*/ &&
                this.cachei0 >>> BITS == i >>> BITS &&
                this.cachej0 >>> BITS == j >>> BITS
        ) {
            if (this.cache == null) return null;
            return (Node) this.cache.getOrNull(i & MASK);
        }

        this.cachei0 = i >>> BITS << BITS;
        this.cachej0 = j >>> BITS << BITS;

        int d = this.depth;
        int bits = BITS * (d - 1);

        Node el;
        for (el = root; d > 1; --d) {
            el = (Node) el.getOrNull((i >>> bits) & MASK);
            if (el == null) {
                this.cache = null;
                return null;
            }

            el = (Node) el.getOrNull((j >>> bits) & MASK);
            if (el == null) {
                this.cache = null;
                return null;
            }
            bits -= BITS;
        }

        this.cache = el;
        if (el == null) return null;
        el = (Node) el.getOrNull(i & MASK);
        return el;
    }

    @Override
    @SuppressWarnings("unchecked")
    public T get(int i, int j) {
        Node row = getRowOrNull(i, j);
        if (row == null) return null;
        return (T) row.getOrNull(j & MASK);
    }

    @Override
    public void query(int i0, int j0, int i1, int j1, QueryFun<T> cb) {
        if (root == null) return;
        if (i1 < i0 || j1 < j0) return;
        int range = (1 << (BITS * depth)) - 1;
        queryRec(root,
                Math.max(i0 - this.i0, 0),
                Math.max(j0 - this.j0, 0),
                Math.min(i1 - this.i0, range),
                Math.min(j1 - this.j0, range),
                this.depth, true, cb);
    }

    @SuppressWarnings("unchecked")
    private void queryRec(Node n, int i0, int j0, int i1, int j1, int d, boolean col, QueryFun<T> cb) {
        if (d == 1 && !col) {
            if (n.n <= 2) {
                if (n.n == 0) return;
                int k1 = n.keys & MASK;
                if (k1 > (i1 & MASK)) return;
                if (k1 >= (i0 & MASK)) cb.apply(this.i0 + j0, this.j0 + ((i0 & (~MASK)) | k1), (T) n.c[0]);
                if (n.n == 2) {
                    int k2 = (n.keys >>> BITS) & MASK;
                    if (k2 <= (i1 & MASK) && k2 >= (i0 & MASK))
                        cb.apply(this.i0 + j0, this.j0 + ((i0 & (~MASK)) | k2), (T) n.c[1]);
                }
            } else {
                for (int i = i0 & MASK; i <= (i1 & MASK); i++) {
                    Object el = n.getOrNull(i);
                    if (el != null) cb.apply(
                            this.i0 + j0, this.j0 + ((i0 & (~MASK)) | i), (T) el);
                }
            }
        } else {
            int bits = BITS * (d - 1);
            int s = (i0 >>> bits) & MASK;
            int e = (i1 >>> bits) & MASK;
            int nextD = col ? d : d - 1;

            if (s == e) {
                Node el = (Node) n.getOrNull(s);
                if (el == null) return;
                queryRec(el, j0, i0, j1, i1, nextD, !col, cb);
            } else {
                int mask = (1 << bits) - 1;
                int start = i0 & ((~MASK) << bits);

                if (n.n <= 2) {
                    if (n.n == 0) return;
                    int[] keys;
                    if (n.n == 1) keys = new int[]{n.keys};
                    else keys = new int[]{n.keys & MASK, n.keys >>> BITS & MASK};

                    for (int i = 0; i < keys.length; i++) {
                        int k = keys[i];
                        if (k == s) queryRec((Node) n.c[i], j0, i0, j1, i0 | mask, nextD, !col, cb);
                        else if (k == e) queryRec((Node) n.c[i], j0, start | (e << bits), j1, i1, nextD, !col, cb);
                        else if (k > s && k < e) {
                            int j = start | (k << bits);
                            queryRec((Node) n.c[i], j0, j, j1, j | mask, nextD, !col, cb);
                        }
                    }
                } else {
                    Node el = (Node) n.getOrNull(s);
                    if (el != null) {
                        queryRec(el, j0, i0, j1, i0 | mask, nextD, !col, cb);
                    }

                    for (int i = s + 1; i < e; i++) {
                        el = (Node) n.getOrNull(i);
                        if (el == null) continue;
                        int j = start | (i << bits);
                        queryRec(el, j0, j, j1, j | mask, nextD, !col, cb);
                    }

                    el = (Node) n.getOrNull(e);
                    if (el != null) {
                        queryRec(el, j0, start | (e << bits), j1, i1, nextD, !col, cb);
                    }
                }
            }
        }
    }

    /**
     * Must ensure that whole path exists (doesn't do checks)
     * always returns non-null row value  (or fails)
     * <p>
     * WARNING: skipLevels should be smaller than this.depth
     * <p>
     * WARNING: be careful, if skipLevels != 0, cache might become invalid,
     * but won't be invalidated automatically
     * <p>
     * this.depth should be ≥ 1
     */
    private Node getOrCreateRow(int i, int j, int skipLevels) {
        i -= i0;
        j -= j0;

        if (skipLevels == 0 && this.cachei0 != -1 /* is this needed?*/ &&
                this.cachei0 >>> BITS == i >>> BITS &&
                this.cachej0 >>> BITS == j >>> BITS
        ) {
            if (this.cache != null) return this.cache.getOrCreateChildNode(i & MASK);
            // no need to invalidate cache here, as it will be set at the bottom
        }

        int d = this.depth;
        int bits = BITS * (d - 1);

        Node el;
        skipLevels++;
        for (el = root; d > skipLevels; --d) {
            Node row = el.getOrCreateChildNode((i >>> bits) & MASK);
            el = row.getOrCreateChildNode((j >>> bits) & MASK);
            bits -= BITS;
        }

        if (skipLevels == 1) {
            this.cachei0 = i >>> BITS << BITS;
            this.cachej0 = j >>> BITS << BITS;
            this.cache = el;
        }

        return el.getOrCreateChildNode((i >>> bits) & MASK);
    }

    /*
     *        -16..-13 -12..-9  -8..-5 -4..-1  0..3 4..7 8..11 12..15 16..19 20..23 24..27 28..31
     *        -16  ... ....................-1  0 .............     15 16 ......................31 32.........47  48.....63
     *                                         0    ....... .. .... . . .. . . ..                            47
     *
     *       i0 and j0 always normalize input, so we're not dealing with negative values
     *
     *       i0 and j0 encapsulate all previously chosen shifts (in corresponding bits)
     *
     *       to create depth 1:  choose shift that is multiple of 1, i.e. any shift
     *                               for convenience and easy copy, can chose multiple of SIZE (but not necessary)
     *                                           i.e. set lower BITs to 0
     *
     *       to create depth 2:  choose shift that is multiple of  SIZE, add previous shift to it
     *       to create depth 3:   shift is multiple of SIZE^2,   add previous shift
     *
     *       in other words,  to create depth `d`,  need to subtract multiple of   (1 <<< (d-1)) from current shift,
     *                           so that resulting range encapsulates current area (and anything else)
     *
     *
     *       more concrete approach:
     *               when expanding, adjust both i0 and j0 by multiple of (1 <<< d)  (current d)
     *               so that they are ≤ all points that are needed to cover
     *               (even better, try to put all points closer to the middle)
     *
     *
     *
     * */


//    /**
//     * checks for null rows
//     */
//    private Node getRowSafe(int i, int j) {
//        int d = this.depth;
//
//        Node el;
//        for (el = root; d > 1; --d) {
//            el = (Node) el.c[i >>> BITS * d & MASK];
//            if (el == null) return null;
//            el = (Node) el.c[j >>> BITS * d & MASK];
//        }
//
//        return (Node) el.c[i & MASK];
//    }

    //      -2        -1       0         1
    // [-64 -32-1][-32  -1] [0..32-1] [32..64-1]
    private static int index(int i) {
        if (i >= 0) return i >>> BITS;
        else return -((-i - 1) >>> BITS) - 1;
    }

    private static int startOfTheRange(int index) {
        if (index >= 0) return index << BITS;
        else return -((-index) << BITS);
    }

    private void ensureBoundaries(int i, int j) {
        if (root == null) {
            root = new Node();
            i0 = startOfTheRange(index(i));
            j0 = startOfTheRange(index(j));
            cachei0 = -1;  // reset cache
            cache = null;
            depth = 1;
        } else if (!inRange(i, j)) {
            /* need to expand */

            Shift iShift = findNewShift(i, i0, depth, depth + 1);
            Shift jShift = findNewShift(j, j0, depth, iShift.d);
            if (jShift.d > iShift.d) {
                iShift = findNewShift(i, i0, depth, jShift.d);
            }

            int oldI0 = i0;
            int oldJ0 = j0;
            int oldDepth = depth;
            Node oldRoot = root;

            root = new Node();
            i0 = iShift.i;
            j0 = jShift.i;

            depth = Math.max(iShift.d, jShift.d);

            cachei0 = -1; // reset cache
            cache = null;

            Node row = getOrCreateRow(oldI0, oldJ0, oldDepth);
            row.setNotNull((((oldJ0 - j0) >>> ((oldDepth) * BITS)) & MASK), oldRoot);
        }
    }

    private boolean inRange(int i, int j) {
        return i >= i0 && j >= j0 && ((i - i0) >>> (depth * BITS) == 0) && ((j - j0) >>> (depth * BITS) == 0);
    }

    public GridReadOnlyView<T> createReadOnlyView() {
        // essentially a shallow copy
        // rationale: other thread will change the cache calling 'get'

        Grid<T> g = new Grid<T>();
        g.root = this.root;
        g.cache = this.cache;
        g.i0 = this.i0;
        g.j0 = this.j0;
        g.depth = this.depth;
        g.cachei0 = this.cachei0;
        g.cachej0 = this.cachej0;
        return g;
    }

    /**
     * NOTE: doesn't check if i falls into current range, always increases depth
     *
     * @param i          value that needs to be covered
     * @param i0         current shift
     * @param curDepth   current depth (use to calculate correct shift)
     * @param startDepth start to search for new range from this depth
     * @return Shift
     */
    static Shift findNewShift(int i, int i0, int curDepth, int startDepth) {
        int min, max;
        int depthXbits = curDepth * BITS;
        if (i0 <= i) {
            min = i0;
            max = Math.max(i, i0 + ((1 << depthXbits) - 1));
        } else { // i < i0
            min = i;
            max = i0 + ((1 << depthXbits) - 1);
        }

        int dist = max - min;
        int newDepth = startDepth;
        int newRange = 1 << (newDepth * BITS);
        while (newRange <= dist) {
            newDepth++;
            newRange <<= BITS;
        }

        int mid = (min + max) / 2;
        /* truncate to make a multiple of  (1 << depth) */
        int newI0 = ((mid - newRange / 2 - i0) >> depthXbits << depthXbits) + i0; // account for negatives
        while (newI0 + newRange <= max) newI0 += (1 << depthXbits);
        return new Shift(newDepth, newI0);
    }

    static class Shift {
        final int d;
        final int i;

        public Shift(int d, int i) {
            this.d = d;
            this.i = i;
        }
    }

    /**
     * Callback interface for AABB query
     * @param <T> value type
     */
    @FunctionalInterface
    public interface QueryFun<T> {
        void apply(int i, int j, T v);
    }
}

package com.aivean.grid2d;

/**
 * Read only view that allows safe read access from other threads
 *
 * @author Ivan Zaitsev https://github.com/Aivean/grid2d
 */
public interface GridReadOnlyView<T> {
    /**
     * Single-element query
     *
     * @param i major key component
     * @param j minor key component
     * @return T or null
     */
    T get(int i, int j);


    /**
     * AABB query.
     * Calls callback function for every non-empty pair of i,j, such as:
     * i0 ≤ i ≤ i1
     * j0 ≤ j ≤ j1
     *
     * @param i0 starting value for i
     * @param j0 starting value for j
     * @param i1 end value for i
     * @param j1 end value for j
     * @param cb callback function that is called for every element in range
     *           NOTE: order in which indices are traversed is not specified
     */
    void query(int i0, int j0, int i1, int j1, Grid.QueryFun<T> cb);
}

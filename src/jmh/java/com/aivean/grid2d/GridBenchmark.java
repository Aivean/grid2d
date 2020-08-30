package com.aivean.grid2d;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.tinspin.index.qthypercube2.QuadTreeKD2;
import org.zoodb.index.critbit.CritBit64;

import java.util.HashMap;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * @author Ivan Zaitsev https://github.com/Aivean/grid2d
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)
@Fork(value = 1,
        jvmArgs = {"-Xms2G", "-XX:+UseSuperWord"}
)
@State(Scope.Thread)
public class GridBenchmark {

    @Param({"10", "100", "300",  "1000", "10000"/*, "100000", "1000000", "100000000"*/})
    int RANGE;

    @Param({/*"100",*/ "10000", /*"500000"*/})
    int POPULATION;

    Grid<Integer> grid;
    CritBit64<Integer> critBit;
    Integer[][] arr;
    HashMap<Long, Integer> map;
    QuadTreeKD2<Integer> quadTree;

    @State(Scope.Thread)
    public static class RandomIndexState {
        int range;
        int i = 0;
        int j = 0;

        @Setup
        public void doSetup(GridBenchmark state) {
            range = state.RANGE;
            i = 0;
            j = 0;
        }

        void next() {
            j = (j * 13 + i * i * 17) % range;
            i = (i * 13 + j * i * 17) % range;
        }
    }

    @State(Scope.Thread)
    public static class SequentialIndexState {
        int range;

        int i = 0;
        int j = 0;

        @Setup
        public void doSetup(GridBenchmark state) {
            range = state.RANGE;
            i = 0;
            j = 0;
        }

        void next() {
            j++;
            if (j == range) {
                j = 0;
                i = (i + 1) % range;
            }
        }
    }

    @State(Scope.Thread)
    public static class emptyStructuresState {
        int range;
        Grid<Integer> grid;
        CritBit64<Integer> critBit;
        Integer[][] arr;
        HashMap<Long, Integer> map;

        @Setup
        public void doSetup(GridBenchmark state) {
            range = state.RANGE;
            grid = new Grid<>();
            critBit = CritBit64.create();
            arr = new Integer[range][range];
            map = new HashMap<>();
        }
    }


    @Setup
    public void setup() {
        grid = new Grid<>();
        critBit = CritBit64.create();
        arr = RANGE <= 10000 ? new Integer[RANGE][RANGE] : new Integer[0][0]; // avoid OOM on large RANGEs
        map = new HashMap<>();
        quadTree = QuadTreeKD2.<Integer>create(2);

        Random rng = new Random(123);
        for (int k = 0; k < POPULATION; k++) {
            int i = rng.nextInt(RANGE);
            int j = rng.nextInt(RANGE);
            int v = rng.nextInt();

            grid.set(i, j, v);
            critBit.put((long) i << 32 | (long) j, v);
            map.put((long) i << 32 | (long) j, v);
            if (RANGE <= 10000) {
                arr[i][j] = v;
            }
            quadTree.insert(new double[]{(double) i, (double) j}, v);
        }
    }

    /**
     * <pre>
     * before cache
     * Benchmark                             (POPULATION)  (RANGE)  Mode  Cnt   Score   Error  Units
     * GridBenchmark.gridRandomGetBenchmark         10000       10  avgt    5  19.930 ± 0.457  ns/op
     * GridBenchmark.gridRandomGetBenchmark         10000      100  avgt    5  23.669 ± 0.113  ns/op
     * GridBenchmark.gridRandomGetBenchmark         10000      300  avgt    5  28.959 ± 0.625  ns/op
     * GridBenchmark.gridRandomGetBenchmark         10000     1000  avgt    5  39.067 ± 0.304  ns/op
     * GridBenchmark.gridRandomGetBenchmark         10000    10000  avgt    5  49.136 ± 2.012  ns/op
     *
     * cache:
     * Benchmark                             (POPULATION)  (RANGE)  Mode  Cnt   Score   Error  Units
     * GridBenchmark.gridRandomGetBenchmark         10000       10  avgt    5  19.786 ± 0.182  ns/op
     * GridBenchmark.gridRandomGetBenchmark         10000      100  avgt    5  20.126 ± 0.874  ns/op
     * GridBenchmark.gridRandomGetBenchmark         10000      300  avgt    5  19.987 ± 0.395  ns/op
     * GridBenchmark.gridRandomGetBenchmark         10000     1000  avgt    5  20.103 ± 0.311  ns/op
     * GridBenchmark.gridRandomGetBenchmark         10000    10000  avgt    5  19.724 ± 0.095  ns/op
     *
     * after adding compressed 2el node
     * Benchmark                             (POPULATION)  (RANGE)  Mode  Cnt   Score   Error  Units
     * GridBenchmark.gridRandomGetBenchmark         10000       10  avgt    5  19.589 ± 0.411  ns/op
     * GridBenchmark.gridRandomGetBenchmark         10000      100  avgt    5  19.676 ± 0.363  ns/op
     * GridBenchmark.gridRandomGetBenchmark         10000      300  avgt    5  20.074 ± 0.347  ns/op
     * GridBenchmark.gridRandomGetBenchmark         10000     1000  avgt    5  20.000 ± 0.347  ns/op
     * GridBenchmark.gridRandomGetBenchmark         10000    10000  avgt    5  19.864 ± 0.143  ns/op
     * </pre>
     */
    @Benchmark
    public void gridRandomGetBenchmark(Blackhole bh, RandomIndexState state) {
        state.next();
        bh.consume(grid.get(state.i, state.j));
    }

    /**
     * <pre>
     * Benchmark                                (POPULATION)  (RANGE)  Mode  Cnt   Score   Error  Units
     * GridBenchmark.critbitRandomGetBenchmark         10000       10  avgt    5  32.343 ± 0.942  ns/op
     * GridBenchmark.critbitRandomGetBenchmark         10000      100  avgt    5  42.839 ± 0.632  ns/op
     * GridBenchmark.critbitRandomGetBenchmark         10000      300  avgt    5  48.586 ± 0.499  ns/op
     * GridBenchmark.critbitRandomGetBenchmark         10000     1000  avgt    5  39.119 ± 0.549  ns/op
     * GridBenchmark.critbitRandomGetBenchmark         10000    10000  avgt    5  46.442 ± 1.224  ns/op
     * </pre>
     */
    @Benchmark
    public void critbitRandomGetBenchmark(Blackhole bh, RandomIndexState state) {
        state.next();
        bh.consume(critBit.get((long) state.i << 32 | (long) state.j));
    }

    /* 19.974 ± 0.315  ns/op   all ranges */
    @Benchmark
    public void plainArrayRandomGetBenchmark(Blackhole bh, RandomIndexState state) {
        state.next();
        bh.consume(arr[state.i][state.j]);
    }

    /**
     * <pre>
     * w/o compressed nodes
     * Grid SIZE: 32 ,   before cache
     * Benchmark                                 (POPULATION)  (RANGE)  Mode  Cnt   Score   Error  Units
     * GridBenchmark.gridSequentialGetBenchmark         10000       10  avgt    5   6.957 ± 0.067  ns/op
     * GridBenchmark.gridSequentialGetBenchmark         10000      100  avgt    5  11.799 ± 0.134  ns/op
     * GridBenchmark.gridSequentialGetBenchmark         10000      300  avgt    5  12.422 ± 0.175  ns/op
     * GridBenchmark.gridSequentialGetBenchmark         10000     1000  avgt    5  16.414 ± 0.151  ns/op
     * GridBenchmark.gridSequentialGetBenchmark         10000    10000  avgt    5  18.657 ± 0.717  ns/op
     *
     *  size 32, with cache
     * Benchmark                                 (POPULATION)  (RANGE)  Mode  Cnt  Score   Error  Units
     * GridBenchmark.gridSequentialGetBenchmark         10000       10  avgt    5  8.079 ± 0.157  ns/op
     * GridBenchmark.gridSequentialGetBenchmark         10000      100  avgt    5  8.161 ± 0.057  ns/op
     * GridBenchmark.gridSequentialGetBenchmark         10000      300  avgt    5  8.714 ± 0.220  ns/op
     * GridBenchmark.gridSequentialGetBenchmark         10000     1000  avgt    5  8.058 ± 0.602  ns/op
     * GridBenchmark.gridSequentialGetBenchmark         10000    10000  avgt    5  6.714 ± 0.128  ns/op
     *
     *  with compressed nodes
     * Benchmark                                 (POPULATION)  (RANGE)  Mode  Cnt   Score   Error  Units
     * GridBenchmark.gridSequentialGetBenchmark         10000       10  avgt    5   8.686 ± 0.180  ns/op
     * GridBenchmark.gridSequentialGetBenchmark         10000      100  avgt    5  11.000 ± 0.125  ns/op
     * GridBenchmark.gridSequentialGetBenchmark         10000      300  avgt    5  11.578 ± 0.254  ns/op
     * GridBenchmark.gridSequentialGetBenchmark         10000     1000  avgt    5  10.311 ± 0.463  ns/op
     * GridBenchmark.gridSequentialGetBenchmark         10000    10000  avgt    5   9.339 ± 3.998  ns/op
     * * </pre>
     */
    @Benchmark
    public void gridSequentialGetBenchmark(Blackhole bh, SequentialIndexState state) {
        state.next();
        bh.consume(grid.get(state.i, state.j));
    }

    /**
     * <pre>
     * Benchmark                                    (POPULATION)  (RANGE)  Mode  Cnt   Score   Error  Units
     * GridBenchmark.critbitSequentialGetBenchmark         10000       10  avgt    5  20.427 ± 0.219  ns/op
     * GridBenchmark.critbitSequentialGetBenchmark         10000      100  avgt    5  64.938 ± 0.549  ns/op
     * GridBenchmark.critbitSequentialGetBenchmark         10000      300  avgt    5  42.868 ± 0.960  ns/op
     * GridBenchmark.critbitSequentialGetBenchmark         10000     1000  avgt    5  33.510 ± 0.808  ns/op
     * GridBenchmark.critbitSequentialGetBenchmark         10000    10000  avgt    5  31.403 ± 2.155  ns/op
     *  </pre>
     */
    @Benchmark
    public void critbitSequentialGetBenchmark(Blackhole bh, SequentialIndexState state) {
        state.next();
        bh.consume(critBit.get((long) state.i << 32 | (long) state.j));
    }

    /* ≈3.6 ns/op for any range  */
    @Benchmark
    public void plainArraySequentialGetBenchmark(Blackhole bh, SequentialIndexState state) {
        state.next();
        bh.consume(arr[state.i][state.j]);
    }

    /*  2.9  ns/op */
    @Benchmark
    public void dummyBenchmark(Blackhole bh, SequentialIndexState state) {
        state.next();
        bh.consume(state.i + state.j);
    }

    /**
     * <pre>
     * Benchmark                                    (POPULATION)  (RANGE)  Mode  Cnt   Score   Error  Units
     * GridBenchmark.mapSequentialGetBenchmark         10000       10  avgt    5  25.206 ±  1.523  ns/op
     * GridBenchmark.mapSequentialGetBenchmark         10000      100  avgt    5  81.205 ± 17.841  ns/op
     * GridBenchmark.mapSequentialGetBenchmark         10000      300  avgt    5  76.433 ±  1.257  ns/op
     * GridBenchmark.mapSequentialGetBenchmark         10000     1000  avgt    5  59.202 ±  0.631  ns/op
     * GridBenchmark.mapSequentialGetBenchmark         10000    10000  avgt    5  15.774 ±  1.027  ns/op
     * </pre>
     */
    @Benchmark
    public void mapSequentialGetBenchmark(Blackhole bh, SequentialIndexState state) {
        state.next();
        bh.consume(map.get((long) state.i << 32 | (long) state.j));
    }

    /**
     * <pre>
     * Benchmark                                     (POPULATION)  (RANGE)  Mode  Cnt   Score    Error  Units
     * GridBenchmark.quadTreeSequentialGetBenchmark         10000       10  avgt    5  51.576 ± 24.789  ns/op
     * GridBenchmark.quadTreeSequentialGetBenchmark         10000      100  avgt    5  99.660 ± 29.206  ns/op
     * GridBenchmark.quadTreeSequentialGetBenchmark         10000      300  avgt    5  72.329 ±  1.895  ns/op
     * GridBenchmark.quadTreeSequentialGetBenchmark         10000     1000  avgt    5  65.680 ±  4.988  ns/op
     * </pre>
     *
     * @param bh
     * @param state
     */
    @Benchmark
    public void quadTreeSequentialGetBenchmark(Blackhole bh, SequentialIndexState state) {
        state.next();
        bh.consume(quadTree.queryExact(new double[]{(double) state.i, (double) state.j}));
    }

    /**
     * <pre>
     * before cache
     * Benchmark                                 (POPULATION)  (RANGE)  Mode  Cnt    Score     Error  Units
     * GridBenchmark.gridSequentialPutBenchmark         10000       10  avgt    5   10.142 ±   0.092  ns/op
     * GridBenchmark.gridSequentialPutBenchmark         10000      100  avgt    5   18.898 ±   2.933  ns/op
     * GridBenchmark.gridSequentialPutBenchmark         10000      300  avgt    5   24.075 ±   0.891  ns/op
     * GridBenchmark.gridSequentialPutBenchmark         10000     1000  avgt    5   32.121 ±   5.611  ns/op
     * GridBenchmark.gridSequentialPutBenchmark         10000    10000  avgt    5  129.049 ± 444.797  ns/op
     *
     *
     * after cache
     *
     * Benchmark                                 (POPULATION)  (RANGE)  Mode  Cnt   Score     Error  Units
     * GridBenchmark.gridSequentialPutBenchmark         10000       10  avgt    5  10.534 ±   0.228  ns/op
     * GridBenchmark.gridSequentialPutBenchmark         10000      100  avgt    5  14.591 ±   3.216  ns/op
     * GridBenchmark.gridSequentialPutBenchmark         10000      300  avgt    5  20.388 ±   0.346  ns/op
     * GridBenchmark.gridSequentialPutBenchmark         10000     1000  avgt    5  25.076 ±   5.700  ns/op
     * GridBenchmark.gridSequentialPutBenchmark         10000    10000  avgt    5  70.241 ± 139.024  ns/op
     *
     *
     * after addition of 2-el nodes
     *
     * Benchmark                                 (POPULATION)  (RANGE)  Mode  Cnt    Score     Error  Units
     * GridBenchmark.gridSequentialPutBenchmark         10000       10  avgt    5   14.378 ±   4.626  ns/op
     * GridBenchmark.gridSequentialPutBenchmark         10000      100  avgt    5   16.916 ±   2.287  ns/op
     * GridBenchmark.gridSequentialPutBenchmark         10000      300  avgt    5   22.079 ±   0.499  ns/op
     * GridBenchmark.gridSequentialPutBenchmark         10000     1000  avgt    5   26.484 ±   4.210  ns/op
     * GridBenchmark.gridSequentialPutBenchmark         10000    10000  avgt    5  126.225 ± 261.204  ns/op
     * </pre>
     *
     * @param bh
     * @param state
     */
    @Benchmark
    public void gridSequentialPutBenchmark(Blackhole bh, SequentialIndexState state) {
        state.next();
        grid.set(state.i, state.j, state.i + state.j);
    }

    /**
     * <pre>
     * Benchmark                                    (POPULATION)  (RANGE)  Mode  Cnt    Score   Error  Units
     * GridBenchmark.critbitSequentialPutBenchmark         10000       10  avgt    5   30.705 ± 0.699  ns/op
     * GridBenchmark.critbitSequentialPutBenchmark         10000      100  avgt    5   84.907 ± 4.871  ns/op
     * GridBenchmark.critbitSequentialPutBenchmark         10000      300  avgt    5  102.619 ± 4.301  ns/op
     * GridBenchmark.critbitSequentialPutBenchmark         10000     1000  avgt    5  115.135 ± 9.317  ns/op
     *    10k -- oom
     * </pre>
     */
    @Benchmark
    public void critbitSequentialPutBenchmark(Blackhole bh, SequentialIndexState state) {
        state.next();
        critBit.put((long) state.i << 32 | (long) state.j, state.i + state.j);
    }

    /**
     * <pre>
     * Benchmark                            (POPULATION)    (RANGE)  Mode  Cnt       Score       Error  Units
     * GridBenchmark.quadTreeAABBBenchmark         10000         10  avgt    5   15201.204 ±  3322.296  ns/op
     * GridBenchmark.quadTreeAABBBenchmark         10000        100  avgt    5   24468.002 ±  1775.742  ns/op
     * GridBenchmark.quadTreeAABBBenchmark         10000        300  avgt    5   22974.365 ±  1812.617  ns/op
     * GridBenchmark.quadTreeAABBBenchmark         10000       1000  avgt    5   21906.514 ±  1002.216  ns/op
     * GridBenchmark.quadTreeAABBBenchmark         10000      10000  avgt    5   24974.667 ±  8685.176  ns/op
     * GridBenchmark.quadTreeAABBBenchmark         10000     100000  avgt    5   23216.060 ±  1146.617  ns/op
     * GridBenchmark.quadTreeAABBBenchmark         10000    1000000  avgt    5   20835.609 ±   565.441  ns/op
     * GridBenchmark.quadTreeAABBBenchmark         10000  100000000  avgt    5   23755.170 ±   952.593  ns/op
     * </pre>
     */
    @Benchmark
    public void quadTreeAABBBenchmark(Blackhole bh, SequentialIndexState state) {
        double r = state.range;
        quadTree.query(
                new double[]{r / 3, r / 3},
                new double[]{r * 2 / 3, r * 2 / 3,}
        ).forEachRemaining(bh::consume);
    }

    /**
     * <pre>
     * GridBenchmark.gridAABBBenchmark         10000       10  avgt    5      92.000 ±    1.157  ns/op
     * GridBenchmark.gridAABBBenchmark         10000      100  avgt    5    4015.841 ±  100.930  ns/op
     * GridBenchmark.gridAABBBenchmark         10000      300  avgt    5   21986.145 ± 2089.463  ns/op
     * GridBenchmark.gridAABBBenchmark         10000     1000  avgt    5   69235.669 ± 1057.273  ns/op
     * GridBenchmark.gridAABBBenchmark         10000    10000  avgt    5  151573.149 ± 1422.292  ns/op
     *
     *
     * after 2-el node
     * Benchmark                            (POPULATION)    (RANGE)  Mode  Cnt       Score       Error  Units
     * GridBenchmark.gridAABBBenchmark             10000         10  avgt    5     100.915 ±    14.833  ns/op
     * GridBenchmark.gridAABBBenchmark             10000        100  avgt    5    4498.316 ±   162.307  ns/op
     * GridBenchmark.gridAABBBenchmark             10000        300  avgt    5   22645.560 ±   635.672  ns/op
     * GridBenchmark.gridAABBBenchmark             10000       1000  avgt    5   21668.281 ±  5677.756  ns/op
     * GridBenchmark.gridAABBBenchmark             10000      10000  avgt    5   48334.644 ±  7547.264  ns/op
     * GridBenchmark.gridAABBBenchmark             10000     100000  avgt    5   64539.936 ± 12738.610  ns/op
     * GridBenchmark.gridAABBBenchmark             10000    1000000  avgt    5   85854.062 ± 12065.084  ns/op
     * GridBenchmark.gridAABBBenchmark             10000  100000000  avgt    5  129764.745 ±  2404.556  ns/op
     * </pre>
     *
     * @param bh
     * @param state
     */
    @Benchmark
    public void gridAABBBenchmark(Blackhole bh, SequentialIndexState state) {
        int r = state.range;
        grid.query(
                r / 3, r / 3,
                r * 2 / 3, r * 2 / 3,
                (i, j, v) -> bh.consume(v)
        );
    }

    /**
     * <pre>
     * Benchmark                                          (POPULATION)  (RANGE)  Mode  Cnt         Score          Error  Units
     * GridBenchmark.gridSequentialSquareAccessBenchmark         10000       10  avgt    5        79.631 ±        3.021  ns/op
     * GridBenchmark.gridSequentialSquareAccessBenchmark         10000      100  avgt    5      8289.403 ±       96.643  ns/op
     * GridBenchmark.gridSequentialSquareAccessBenchmark         10000      300  avgt    5     82631.829 ±    19258.632  ns/op
     * GridBenchmark.gridSequentialSquareAccessBenchmark         10000     1000  avgt    5    813991.865 ±    10875.658  ns/op
     * GridBenchmark.gridSequentialSquareAccessBenchmark         10000    10000  avgt    5  75798308.756 ± 11739658.426  ns/op
     *
     * after adding compressed nodes:
     * GridBenchmark.gridSequentialSquareAccessBenchmark         10000       10  avgt    5        68.796 ±       3.004  ns/op
     * GridBenchmark.gridSequentialSquareAccessBenchmark         10000      100  avgt    5     11956.906 ±     452.726  ns/op
     * GridBenchmark.gridSequentialSquareAccessBenchmark         10000      300  avgt    5     98298.608 ±   16954.925  ns/op
     * GridBenchmark.gridSequentialSquareAccessBenchmark         10000     1000  avgt    5    881769.646 ±    9924.478  ns/op
     * GridBenchmark.gridSequentialSquareAccessBenchmark         10000    10000  avgt    5  93520930.823 ± 7319312.902  ns/op
     * </pre>
     */
    @Benchmark
    public void gridSequentialSquareAccessBenchmark(Blackhole bh, SequentialIndexState state) {
        int r = state.range;
        int r1 = r * 2 / 3;
        for (int i = r / 3; i < r1; i++) {
            for (int j = r / 3; j < r1; j++) {
                bh.consume(grid.get(i, j));
            }
        }
    }

    /**
     * <pre>
     * Benchmark                                                (POPULATION)  (RANGE)  Mode  Cnt         Score         Error  Units
     * GridBenchmark.plainArraySequentialSquareAccessBenchmark         10000       10  avgt    5        51.077 ±       2.308  ns/op
     * GridBenchmark.plainArraySequentialSquareAccessBenchmark         10000      100  avgt    5      5526.101 ±     331.266  ns/op
     * GridBenchmark.plainArraySequentialSquareAccessBenchmark         10000      300  avgt    5     48036.926 ±     481.117  ns/op
     * GridBenchmark.plainArraySequentialSquareAccessBenchmark         10000     1000  avgt    5    415886.188 ±   10077.582  ns/op
     * GridBenchmark.plainArraySequentialSquareAccessBenchmark         10000    10000  avgt    5  42753263.190 ± 5106892.159  ns/op
     * </pre>
     */
    @Benchmark
    public void plainArraySequentialSquareAccessBenchmark(Blackhole bh, SequentialIndexState state) {
        int r = state.range;
        int r1 = r * 2 / 3;
        for (int i = r / 3; i < r1; i++) {
            for (int j = r / 3; j < r1; j++) {
                bh.consume(arr[i][j]);
            }
        }
    }
}

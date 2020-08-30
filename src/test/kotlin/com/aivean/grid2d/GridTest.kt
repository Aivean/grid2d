package com.aivean.grid2d

import org.testng.Assert
import org.testng.annotations.Test
import org.zoodb.index.critbit.CritBit64
import kotlin.random.Random

class GridTest {

    @Test
    fun testShift() {
        val shift = Grid.findNewShift(5, 0, 0, 1)
        Assert.assertEquals(shift.d, 1)
        Assert.assertEquals(shift.i, -14)
    }

    @Test
    fun testGridSet() {
        val g = Grid<Int>()

        g[0, 1] = 1
        Assert.assertEquals(g[0, 1], 1)
        g[0, 0] = 2
        Assert.assertEquals(g[0, 1], 1)
        Assert.assertEquals(g[0, 0], 2)

        g[-20, 2] = -20
        Assert.assertEquals(g[-20, 2], -20)

        g[16, 0] = -200
        g[0, 1000] = -100

        Assert.assertEquals(g[0, 0], 2)
        Assert.assertEquals(g[0, 1000], -100)
        Assert.assertEquals(g[0, 0], 2)
        Assert.assertEquals(g[0, 1], 1)
        Assert.assertEquals(g[0, 1], 1)
        Assert.assertEquals(g[-20, 2], -20)
        Assert.assertEquals(g[-20, 2], -20)
    }

    @Test
    fun testGridUnset() {
        val g = Grid<Int>()
        g[0, 0] = 1
        Assert.assertEquals(g.depth, 1)
        g[0, 100] = 1
        Assert.assertEquals(g.depth, 2)
        g[0, 0] = null
        Assert.assertEquals(g.depth, 1)
        g[0, 100] = null
        Assert.assertEquals(g.depth, 0)
    }

    @Test
    fun testDepth() {
        val g = Grid<Int>()
        Assert.assertEquals(g.depth, 0)
        g[-1000, 0] = 0
        Assert.assertEquals(g.depth, 1)
        g[-500, 0] = 0
        Assert.assertEquals(g.depth, 2)
        g[1000, 0] = 0
        Assert.assertEquals(g.depth, 3)
    }

    @Test
    fun testGridRandomized() {
        for (range in listOf(10, 100, 10000)) {
            for (iter in 1..6) {
                val rng = Random(iter + range)
                val g = Grid<Int>()
                val eta = CritBit64.create<Int>()

                fun key(i: Int, j: Int) = (i.toLong() shl 32) or (j.toLong() shl 32 ushr 32)
                fun Long.keyI() = (this shr 32).toInt()
                fun Long.keyJ() = (this shl 32 shr 32).toInt()

                repeat(range * iter) {
                    val i = rng.nextInt(-range, range)
                    val j = rng.nextInt(-range, range)
                    val key = key(i, j)

                    Assert.assertTrue(g[i, j] == eta[key])

                    val v = if (rng.nextBoolean()) null else {
                        rng.nextInt()
                    }

                    if (v != null) eta.put(key, v) else eta.remove(key)
                    g[i, j] = v

                    Assert.assertTrue(g[i, j] == eta[key])
                }

                val rangeSqrt = Math.round(Math.sqrt(range.toDouble())).toInt()
                repeat(rangeSqrt) {
                    val i0 = rng.nextInt(-range, range)
                    val j0 = rng.nextInt(-range, range)
                    val si = rng.nextInt(rangeSqrt * 10)
                    val sj = rng.nextInt(rangeSqrt * 10)

                    val etaSet = mutableSetOf<Triple<Int, Int, Int>>()
                    val resSet = mutableSetOf<Triple<Int, Int, Int>>()

                    for (i in i0..Math.min(i0 + si, range)) {
                        for (j in j0..Math.min(j0 + sj, range)) {
                            val key = key(i, j)
                            eta[key]?.let {
                                etaSet += Triple(i, j, it)
                            }
                        }
                    }

                    g.query(i0, j0, i0 + si, j0 + sj) { i, j, v ->
                        resSet += Triple(i, j, v)
                    }

                    Assert.assertEquals(resSet, etaSet)
                }

                val iterator = eta.iterator()
                generateSequence { if (iterator.hasNext()) iterator.nextEntry() else null }.forEach {
                    val key = it.key()

                    val i = key.keyI()
                    val j = key.keyJ()
                    Assert.assertTrue(g[i, j] == eta[key],
                        "$i $j ${g[i, j]} ${eta[key]}"
                    )
                    eta.remove(key)
                    g[i, j] = null
                    Assert.assertTrue(g[i, j] == eta[key])
                }

                Assert.assertEquals(g.depth, 0)
            }
        }
    }

    private fun <T> Grid<T>.query(i0: Int, j0: Int, i1: Int, j1: Int): List<Triple<Int, Int, T>> =
        mutableListOf<Triple<Int, Int, T>>().also {
            this@query.query(i0, j0, i1, j1) { i, j, v ->
                it += Triple(i, j, v)
            }
        }

    @Test
    fun testQuery() {
        val g = Grid<Int>()
        for (i in -10..10) {
            for (j in -10..10) {
                g[i, j] = i + j
            }
        }

        Assert.assertEquals(
            g.query(-50, -1, 50, 1).toSet(),
            (-10..10).flatMap { i ->
                (-1..1).map { j ->
                    Triple(i, j, i + j)
                }
            }.toSet()
        )

        Assert.assertEquals(
            g.query(8, 8, 12, 12).toSet(),
            listOf(
                8 to 8,
                8 to 9,
                8 to 10,
                9 to 8,
                9 to 9,
                9 to 10,
                10 to 8,
                10 to 9,
                10 to 10
            ).map { (i, j) -> Triple(i, j, i + j) }.toSet()
        )
    }

    @Test
    fun testQuery1() {
        val g = Grid<Int>()
        for (i in -1000..100) {
            for (j in -1000..100) {
                g[i, j] = i + j
            }
        }

        Assert.assertEquals(
            g.query(-100, -10, 1000, 1000).toSet(),
            (-100..100).flatMap { i ->
                (-10..100).map { j ->
                    Triple(i, j, i + j)
                }
            }.toSet()
        )

        Assert.assertEquals(
            g.query(0, 0, 0, 0).toSet(),
            setOf(Triple(0, 0, 0))
        )
    }
}
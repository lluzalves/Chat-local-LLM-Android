package com.example.chatlocalllm

import com.example.chatlocalllm.ui.companion.CompanionGeometry
import com.example.chatlocalllm.ui.companion.CompanionMode
import com.example.chatlocalllm.ui.companion.CompanionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

class CompanionGeometryTest {

    private fun CompanionState.after(seconds: Double): CompanionState {
        repeat((seconds * 60).toInt()) { step(1.0 / 60) }
        return this
    }

    private fun CompanionGeometry.assertWellFormed() {
        val n = CompanionGeometry.SHARDS
        assertEquals(12, n)
        points.forEach { assertTrue("point not finite: $it", it.isFinite()) }
        depth.forEach { assertTrue(it.isFinite()) }
        alpha.forEach { assertTrue("alpha out of range: $it", it > 0.05f && it <= 1f) }
        brightness.forEach { assertTrue("brightness out of range: $it", it in 0..15) }
        assertEquals("a permutation", (0 until n).toSet(), order.toSet())
        for (k in 1 until n) assertTrue("sorted back to front", depth[order[k - 1]] <= depth[order[k]])
    }

    @Test
    fun `every mode produces a well-formed frame`() {
        CompanionMode.entries.forEach { mode ->
            val state = CompanionState(mode).after(1.0)
            CompanionGeometry().apply { compute(state) }.assertWellFormed()
        }
    }

    @Test
    fun `the closed shell fits inside the halo, the open shell reaches further out`() {
        fun reach(mode: CompanionMode): Float {
            val g = CompanionGeometry()
            g.compute(CompanionState(mode).after(0.5))
            var max = 0f
            for (k in 0 until CompanionGeometry.SHARDS) for (p in 0 until 4) {
                max = maxOf(max, hypot(g.points[k * 8 + p * 2], g.points[k * 8 + p * 2 + 1]))
            }
            return max
        }
        val closed = reach(CompanionMode.IDLE)
        val open = reach(CompanionMode.THINKING)
        assertTrue("closed reach $closed", closed in 30f..CompanionGeometry.HALO_RADIUS)
        assertTrue("open reach $open > closed $closed", open > closed + 10f)
        assertTrue("the open shell still fits the 172-unit box", open < CompanionGeometry.UNIT / 2)
    }

    @Test
    fun `shards sit on both sides of the core`() {
        val g = CompanionGeometry()
        g.compute(CompanionState(CompanionMode.IDLE).after(0.3))
        assertTrue("some behind", g.depth.any { it < 0f })
        assertTrue("some in front", g.depth.any { it > 0f })
    }

    @Test
    fun `while assembling the unplaced shards are faint and scattered`() {
        val state = CompanionState(CompanionMode.ASLEEP).apply { mode = CompanionMode.LOADING }
        state.step(1.0 / 60)
        val g = CompanionGeometry()
        g.compute(state)
        g.assertWellFormed()
        assertTrue("faint: ${g.alpha.toList()}", g.alpha.all { it < 0.3f })
        var far = 0
        for (k in 0 until CompanionGeometry.SHARDS) {
            if (hypot(g.points[k * 8], g.points[k * 8 + 1]) > CompanionGeometry.HALO_RADIUS) far++
        }
        assertTrue("scattered beyond the halo: $far", far >= 3)
    }

    @Test
    fun `the orbit lines are filled while open`() {
        val g = CompanionGeometry()
        g.compute(CompanionState(CompanionMode.THINKING).after(0.5))
        val perRing = CompanionGeometry.ORBIT_POINTS * 2
        for (i in CompanionGeometry.RINGS.indices) {
            val ring = CompanionGeometry.RINGS[i]
            val expected = ring.radius + ring.spread
            for (j in 0 until CompanionGeometry.ORBIT_POINTS) {
                val r = hypot(g.orbit[i * perRing + j * 2], g.orbit[i * perRing + j * 2 + 1])

                assertTrue("orbit point at $r for ring radius $expected", r > expected * 0.1 && r < expected * 1.3)
            }
        }
    }

    @Test
    fun `the spin moves the shards between frames`() {
        val state = CompanionState(CompanionMode.THINKING)
        val g = CompanionGeometry()
        g.compute(state)
        val before = g.points.copyOf()
        state.step(1.0 / 60)
        g.compute(state)
        assertTrue(before.indices.any { kotlin.math.abs(before[it] - g.points[it]) > 0.01f })
    }
}

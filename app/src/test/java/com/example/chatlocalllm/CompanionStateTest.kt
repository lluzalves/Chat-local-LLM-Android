package com.example.chatlocalllm

import com.example.chatlocalllm.ui.companion.CompanionMode
import com.example.chatlocalllm.ui.companion.CompanionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.exp

class CompanionStateTest {

    private val frame = 1.0 / 60

    private fun CompanionState.run(seconds: Double, dt: Double = frame): CompanionState {
        var left = seconds
        while (left > 1e-9) { step(minOf(dt, left)); left -= dt }
        return this
    }

    private fun CompanionState.assertFinite() {
        listOf(open, openVelocity, glow, energy, dim, assembly, innerSpin, outerSpin, pulse, shiver, scale, coreScale,
            glowEffective, dimEffective, jitterX, jitterY, jitterRotation, yaw, pitch, highlightX, highlightY, openEffective, time)
            .forEach { assertTrue("not finite: $it", it.isFinite()) }
    }

    @Test
    fun `unfolding settles near 1 within a second with at most 6 percent overshoot`() {
        val s = CompanionState(CompanionMode.IDLE).apply { mode = CompanionMode.THINKING }
        var peak = 0.0
        repeat(60) { s.step(frame); peak = maxOf(peak, s.open) }
        assertTrue("overshoot ${peak - 1}", peak <= 1.06)
        assertTrue("open after 1 s: ${s.open}", abs(s.open - 1) < 0.02)
        assertTrue("it did overshoot a little (a spring, not a tween): $peak", peak > 1.0)
    }

    @Test
    fun `folding back settles near 0 the same way`() {
        val s = CompanionState(CompanionMode.THINKING).apply { mode = CompanionMode.IDLE }
        var low = 1.0
        repeat(60) { s.step(frame); low = minOf(low, s.open) }
        assertTrue("undershoot $low", low >= -0.06)
        assertTrue(abs(s.open) < 0.02)
    }

    @Test
    fun `streaming half-opens`() {
        val s = CompanionState(CompanionMode.IDLE).apply { mode = CompanionMode.STREAMING }.run(1.0)
        assertTrue(abs(s.open - 0.45) < 0.02)
    }

    @Test
    fun `glow, energy and the spins follow their targets with tau 0,35 s`() {
        val s = CompanionState(CompanionMode.IDLE).apply { mode = CompanionMode.THINKING }.run(0.35)
        val expected = 1 - exp(-1.0)
        assertEquals(0.55 + (0.85 - 0.55) * expected, s.glow, 0.02)
        assertEquals(expected, s.energy, 0.02)
        assertEquals(0.12 + (2.6 - 0.12) * expected, s.innerSpinSpeed, 0.05)
        assertEquals(-0.09 + (-1.9 + 0.09) * expected, s.outerSpinSpeed, 0.05)
    }

    @Test
    fun `no model dims to grey and the spins stop`() {
        val s = CompanionState(CompanionMode.IDLE).apply { mode = CompanionMode.NONE }.run(3.0)
        assertEquals(1.0, s.dim, 0.01)
        assertEquals(0.12, s.glow, 0.01)
        assertEquals(0.0, s.innerSpinSpeed, 0.01)
        assertEquals(0.0, s.open, 0.01)
    }

    @Test
    fun `the rings counter-rotate and the angle accumulates`() {
        val s = CompanionState(CompanionMode.IDLE).run(2.0)
        assertTrue("inner forward: ${s.innerSpin}", s.innerSpin > 0.2)
        assertTrue("outer backward: ${s.outerSpin}", s.outerSpin < -0.15)
    }

    @Test
    fun `the state starts where its mode settles - a no-model phone never flashes teal`() {
        val none = CompanionState(CompanionMode.NONE)
        assertEquals(1.0, none.dim, 0.0)
        assertEquals(0.12, none.glow, 0.0)
        assertEquals(1.0, none.dimEffective, 0.0)
        val thinking = CompanionState(CompanionMode.THINKING)
        assertEquals(1.0, thinking.open, 0.0)
        assertEquals(1.0, thinking.energy, 0.0)
    }

    @Test
    fun `idle breathes - scale and glow move around their rest over 4,2 s`() {
        val s = CompanionState(CompanionMode.IDLE)
        s.run(4.2 / 4)
        assertEquals(1.025, s.scale, 0.003)
        assertEquals(0.65, s.glowEffective, 0.01)
        s.run(4.2 / 2)
        assertEquals(0.975, s.scale, 0.003)
        assertEquals(0.45, s.glowEffective, 0.01)
    }

    @Test
    fun `thinking barely breathes - the breath scales with calm`() {
        val s = CompanionState(CompanionMode.THINKING).run(4.2 / 4)
        assertEquals(1 + 0.025 * 0.4, s.scale, 0.003)
        assertEquals(0.85, s.glowEffective, 0.01)
    }

    @Test
    fun `a token pulse flares then decays with tau 0,16 s`() {
        val s = CompanionState(CompanionMode.STREAMING).run(1.0)
        val restGlow = s.glowEffective
        s.pulseToken()
        assertEquals(1.0, s.pulse, 0.0)
        s.step(0.0)
        assertTrue("glow flares by 0.35: ${s.glowEffective} vs $restGlow", s.glowEffective >= minOf(1.0, restGlow + 0.34))
        assertTrue("the core swells", s.coreScale > 1.05)
        assertTrue("a little extra unfold", s.openEffective > s.open + 0.1)
        s.run(0.16)
        assertEquals(exp(-1.0), s.pulse, 0.02)
        s.run(1.0)
        assertTrue("gone after a second: ${s.pulse}", s.pulse < 0.01)
        assertEquals(s.open, s.openEffective, 0.002)
    }

    @Test
    fun `two pulses in one frame are one pulse`() {
        val s = CompanionState(CompanionMode.STREAMING)
        s.pulseToken(); s.pulseToken()
        assertEquals(1.0, s.pulse, 0.0)
    }

    @Test
    fun `a shiver lasts 0,7 s - jitter, a grey flinch, a glow dip - then nothing`() {
        val s = CompanionState(CompanionMode.IDLE).run(1.0)
        val restGlow = s.glowEffective
        s.shiverOnce()
        s.step(0.0)
        assertEquals(0.7, s.shiver, 1e-9)
        assertTrue("grey flinch: ${s.dimEffective}", s.dimEffective > 0.5)
        assertTrue("glow dips: ${s.glowEffective} vs $restGlow", s.glowEffective < restGlow * 0.6)
        s.run(0.35)
        assertEquals(0.35, s.shiver, 0.02)
        assertTrue("half a flinch: ${s.dimEffective}", s.dimEffective > 0.2 && s.dimEffective < 0.35)
        var jittered = false
        repeat(6) { s.step(frame); if (s.jitterX != 0.0 || s.jitterY != 0.0 || s.jitterRotation != 0.0) jittered = true }
        assertTrue("it jitters while shivering", jittered)
        s.run(0.7)
        assertEquals(0.0, s.shiver, 0.0)
        assertEquals(0.0, s.jitterX, 0.0)
        assertEquals(0.0, s.jitterY, 0.0)
        assertEquals(0.0, s.jitterRotation, 0.0)
        assertEquals(s.dim, s.dimEffective, 0.0)
    }

    @Test
    fun `a shiver never goes below zero`() {
        val s = CompanionState(CompanionMode.IDLE)
        s.shiverOnce()
        s.run(5.0, dt = 0.05)
        assertEquals(0.0, s.shiver, 0.0)
    }

    @Test
    fun `entering LOADING scatters the shards and they hover just short of the shell`() {
        val s = CompanionState(CompanionMode.ASLEEP)
        assertEquals(1.0, s.assembly, 0.0)
        s.mode = CompanionMode.LOADING
        assertEquals(0.0, s.assembly, 0.0)
        s.run(4.0)
        assertTrue("hovers at 0.88: ${s.assembly}", s.assembly > 0.85 && s.assembly <= 0.88)
        s.mode = CompanionMode.IDLE
        s.run(3.0)
        assertTrue("snaps shut when ready: ${s.assembly}", s.assembly > 0.99)
    }

    @Test
    fun `setting the same mode again does not scatter`() {
        val s = CompanionState(CompanionMode.LOADING).run(4.0)
        val before = s.assembly
        s.mode = CompanionMode.LOADING
        assertEquals(before, s.assembly, 0.0)
    }

    @Test
    fun `dt is clamped to 50 ms - a frame after a pause never jumps`() {
        val a = CompanionState(CompanionMode.IDLE).apply { mode = CompanionMode.THINKING }
        val b = CompanionState(CompanionMode.IDLE).apply { mode = CompanionMode.THINKING }
        a.step(100.0)
        b.step(0.05)
        assertEquals(0.05, a.time, 0.0)
        assertEquals(b.open, a.open, 0.0)
        assertEquals(b.glow, a.glow, 0.0)
    }

    @Test
    fun `a negative dt is a zero step - the spring never runs backwards`() {
        val s = CompanionState(CompanionMode.IDLE).apply { mode = CompanionMode.THINKING }.run(0.2)
        val open = s.open
        val glow = s.glow
        val time = s.time
        s.step(-1.0)
        assertEquals(time, s.time, 0.0)
        assertEquals(open, s.open, 0.0)
        assertEquals(glow, s.glow, 0.0)
        s.assertFinite()
    }

    @Test
    fun `a NaN dt is a zero step too`() {
        val s = CompanionState(CompanionMode.IDLE).apply { mode = CompanionMode.THINKING }.run(0.2)
        val open = s.open
        s.step(Double.NaN)
        assertEquals(open, s.open, 0.0)
        s.assertFinite()
    }

    @Test
    fun `huge steps for a long time never blow up`() {
        val s = CompanionState(CompanionMode.IDLE)
        s.mode = CompanionMode.THINKING
        repeat(600) { s.step(Double.MAX_VALUE) }
        s.pulseToken(); s.shiverOnce()
        repeat(600) { s.step(1e9) }
        s.assertFinite()
        assertEquals(1.0, s.open, 0.02)
    }

    @Test
    fun `the spring is stable at the largest frame`() {
        val s = CompanionState(CompanionMode.IDLE).apply { mode = CompanionMode.THINKING }
        var peak = 0.0
        repeat(40) { s.step(0.05); peak = maxOf(peak, s.open) }
        assertTrue("overshoot at 20 fps: $peak", peak <= 1.12)
        assertTrue(abs(s.open - 1) < 0.03)
    }

    @Test
    fun `reduced motion keeps open at 0 and nothing spins, sways or jitters - but the glow still says thinking`() {
        val s = CompanionState(CompanionMode.IDLE, reducedMotion = true).apply { mode = CompanionMode.THINKING }
        s.pulseToken(); s.shiverOnce()
        s.run(2.0)
        assertEquals(0.0, s.open, 0.0)
        assertEquals(0.0, s.openEffective, 0.0)
        assertEquals(0.0, s.innerSpin, 0.0)
        assertEquals(0.0, s.outerSpin, 0.0)
        assertEquals(0.0, s.yaw, 0.0)
        assertEquals(0.0, s.pitch, 0.0)
        assertEquals(1.0, s.scale, 0.0)
        assertEquals(1.0, s.coreScale, 0.0)
        assertEquals(-4.0, s.highlightX, 0.01)
        assertEquals(-5.0, s.highlightY, 0.01)
        assertEquals(0.85, s.glow, 0.01)
        s.shiverOnce()
        repeat(10) { s.step(frame); assertEquals(0.0, s.jitterX, 0.0); assertEquals(0.0, s.jitterRotation, 0.0) }
        assertTrue("the grey flinch is a brightness change, so it stays", s.dimEffective > 0.3)
    }

    @Test
    fun `reduced motion switched on mid-turn folds the shell back`() {
        val s = CompanionState(CompanionMode.THINKING)
        assertEquals(1.0, s.open, 0.0)
        s.reducedMotion = true
        s.run(1.5)
        assertEquals(0.0, s.open, 0.02)
    }

    @Test
    fun `a pulse under reduced motion is brightness only`() {
        val s = CompanionState(CompanionMode.IDLE, reducedMotion = true).run(0.5)
        val glow = s.glowEffective
        s.pulseToken()
        s.step(0.0)
        assertTrue("${s.glowEffective} vs $glow", s.glowEffective > glow + 0.3)
        assertEquals(0.0, s.openEffective, 0.0)
        assertEquals(1.0, s.coreScale, 0.0)
    }
}

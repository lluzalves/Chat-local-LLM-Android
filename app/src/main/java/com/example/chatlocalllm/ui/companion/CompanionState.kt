package com.example.chatlocalllm.ui.companion

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sin

class CompanionState(
    initialMode: CompanionMode = CompanionMode.IDLE,
    reducedMotion: Boolean = false,
) {

    var mode: CompanionMode = initialMode
        set(next) {
            if (next == field) return
            if (next == CompanionMode.LOADING) assembly = 0.0
            field = next
        }

    var reducedMotion: Boolean = reducedMotion

    var time: Double = 0.0
        private set

    var open: Double = 0.0; private set
    var openVelocity: Double = 0.0; private set
    var glow: Double = 0.0; private set
    var energy: Double = 0.0; private set
    var dim: Double = 0.0; private set
    var assembly: Double = 1.0; private set
    var innerSpinSpeed: Double = 0.0; private set
    var outerSpinSpeed: Double = 0.0; private set
    var pulse: Double = 0.0; private set
    var shiver: Double = 0.0; private set

    var innerSpin: Double = 0.0; private set
    var outerSpin: Double = 0.0; private set

    var scale: Double = 1.0; private set

    var coreScale: Double = 1.0; private set

    var glowEffective: Double = 0.0; private set

    var dimEffective: Double = 0.0; private set

    var jitterX: Double = 0.0; private set
    var jitterY: Double = 0.0; private set
    var jitterRotation: Double = 0.0; private set

    var yaw: Double = 0.0; private set
    var pitch: Double = 0.0; private set

    var highlightX: Double = -4.0; private set
    var highlightY: Double = -5.0; private set

    var openEffective: Double = 0.0; private set

    init {

        val t = initialMode.targets
        val rm = if (reducedMotion) 0.0 else 1.0
        open = t.open * rm
        glow = t.glow
        energy = t.energy
        dim = t.dim
        assembly = t.assembly
        innerSpinSpeed = t.innerSpin * rm
        outerSpinSpeed = t.outerSpin * rm
        deriveFrame(0.0)
    }

    fun pulseToken() {
        pulse = 1.0
    }

    fun shiverOnce() {
        shiver = SHIVER_SECONDS
    }

    fun step(dtSeconds: Double) {
        val dt = if (dtSeconds.isNaN()) 0.0 else dtSeconds.coerceIn(0.0, MAX_DT)
        time += dt
        val target = mode.targets
        val k = 1 - exp(-dt / SMOOTH_TAU)
        val rm = if (reducedMotion) 0.0 else 1.0

        glow += (target.glow - glow) * k
        energy += (target.energy - energy) * k
        dim += (target.dim - dim) * k
        innerSpinSpeed += (target.innerSpin * rm - innerSpinSpeed) * k
        outerSpinSpeed += (target.outerSpin * rm - outerSpinSpeed) * k
        innerSpin += innerSpinSpeed * dt
        outerSpin += outerSpinSpeed * dt
        assembly += (target.assembly - assembly) * (1 - exp(-dt / (if (target.assembly > assembly) ASSEMBLY_RISE_TAU else ASSEMBLY_FALL_TAU)))

        val openTarget = target.open * rm
        val acceleration = -K_SPRING * (open - openTarget) - C_SPRING * openVelocity
        openVelocity += acceleration * dt
        open += openVelocity * dt

        pulse *= exp(-dt / PULSE_TAU)
        if (shiver > 0) shiver = max(0.0, shiver - dt)

        deriveFrame(dt)
    }

    private fun deriveFrame(dt: Double) {
        val rm = if (reducedMotion) 0.0 else 1.0
        val breath = sin(time * TAU / BREATHE_SECONDS)
        val calm = 1 - energy
        scale = if (reducedMotion) 1.0 else 1 + 0.025 * breath * (0.4 + 0.6 * calm)
        glowEffective = clamp01(glow + 0.10 * breath * calm + 0.35 * pulse)
        val sv = shiver / SHIVER_SECONDS
        if (sv > 0) {
            glowEffective *= 1 - 0.5 * sv
            dimEffective = clamp01(dim + 0.55 * sv)
            jitterX = rm * sv * 3.5 * sin(time * TAU * 26)
            jitterY = rm * sv * 1.2 * sin(time * TAU * 31 + 1)
            jitterRotation = rm * sv * 0.05 * sin(time * TAU * 19)
        } else {
            dimEffective = dim
            jitterX = 0.0
            jitterY = 0.0
            jitterRotation = 0.0
        }

        val e = energy
        yaw = rm * ((1 - e) * 0.35 * sin(time * TAU / 9) + e * 0.55 * sin(time * TAU / 3.4))
        pitch = rm * ((1 - e) * 0.18 * sin(time * TAU / 13 + 1) + e * 0.30 * sin(time * TAU / 4.6))

        val targetX = if (reducedMotion) -4.0 else (1 - e) * (-4 + 2.5 * sin(time * TAU / 7.3)) + e * (5.5 * sin(time * TAU / 2.1))
        val targetY = if (reducedMotion) -5.0 else (1 - e) * (-5 + 2.0 * sin(time * TAU / 9.1 + 1)) + e * (-4 + 3 * cos(time * TAU / 2.9))
        val ke = 1 - exp(-dt / HIGHLIGHT_TAU)
        highlightX += (targetX - highlightX) * ke
        highlightY += (targetY - highlightY) * ke
        coreScale = 1 + rm * (0.06 * pulse + 0.05 * energy + 0.02 * breath * calm)

        openEffective = open + rm * 0.12 * pulse
    }

    companion object {
        const val TAU = PI * 2
        const val MAX_DT = 0.05
        const val K_SPRING = 90.0
        const val C_SPRING = 13.0
        const val SMOOTH_TAU = 0.35
        const val HIGHLIGHT_TAU = 0.25
        const val PULSE_TAU = 0.16
        const val SHIVER_SECONDS = 0.7
        const val BREATHE_SECONDS = 4.2
        const val ASSEMBLY_RISE_TAU = 0.9
        const val ASSEMBLY_FALL_TAU = 0.3

        internal fun clamp01(x: Double): Double = if (x < 0) 0.0 else if (x > 1) 1.0 else x
    }
}

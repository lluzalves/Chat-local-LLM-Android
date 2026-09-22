package com.example.chatlocalllm.ui.companion

import com.example.chatlocalllm.ui.companion.CompanionState.Companion.TAU
import com.example.chatlocalllm.ui.companion.CompanionState.Companion.clamp01
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

class CompanionGeometry {

    class Ring(
        val count: Int,
        val radius: Double,
        val spread: Double,
        val tilt: Double,
        val twist: Double,
        val halfWidth: Double,
        val tipLength: Double,
        val tailLength: Double,
        val phase: Double,
    )

    val points = FloatArray(SHARDS * 8)
    val depth = FloatArray(SHARDS)
    val alpha = FloatArray(SHARDS)
    val brightness = IntArray(SHARDS)
    val order = IntArray(SHARDS) { it }
    val orbit = FloatArray(RINGS.size * ORBIT_POINTS * 2)

    private val ringOf = IntArray(SHARDS)
    private val baseAngle = DoubleArray(SHARDS)
    private val sign = DoubleArray(SHARDS)
    private val scatter = DoubleArray(SHARDS * 3)
    private val hover = DoubleArray(SHARDS)

    private val rotPitch = DoubleArray(9)
    private val rotYaw = DoubleArray(9)
    private val global = DoubleArray(9)
    private val ringBase = Array(RINGS.size) { DoubleArray(9) }
    private val ringMatrix = Array(RINGS.size) { DoubleArray(9) }

    init {

        var seed = 7L
        fun random(): Double {
            seed = ((seed.toDouble() * 1103515245.0 + 12345.0).toLong().toInt() and 0x7fffffff).toLong()
            return seed.toDouble() / 0x7fffffff
        }
        var k = 0
        RINGS.forEachIndexed { i, ring ->
            for (j in 0 until ring.count) {
                ringOf[k] = i
                baseAngle[k] = ring.phase + TAU * j / ring.count
                sign[k] = if (j % 2 == 0) 1.0 else -1.0
                val th = random() * TAU
                val ph = (random() - 0.5) * 2.4
                val d = 80 + random() * 30
                scatter[k * 3] = cos(th) * cos(ph) * d
                scatter[k * 3 + 1] = sin(th) * cos(ph) * d
                scatter[k * 3 + 2] = sin(ph) * d
                hover[k] = random() * TAU
                k++
            }
        }
        val a = DoubleArray(9)
        val b = DoubleArray(9)
        RINGS.forEachIndexed { i, ring ->
            rotZ(ring.twist, a)
            rotX(ring.tilt, b)
            multiply(a, b, ringBase[i])
        }
    }

    fun compute(state: CompanionState) {
        val openEff = state.openEffective
        val energy = state.energy
        val pulse = state.pulse
        val assembly = state.assembly
        val time = state.time

        rotX(state.pitch, rotPitch)
        rotY(state.yaw, rotYaw)
        multiply(rotPitch, rotYaw, global)
        for (i in RINGS.indices) multiply(global, ringBase[i], ringMatrix[i])

        if (openEff > 0.02) {
            for (i in RINGS.indices) {
                val m = ringMatrix[i]
                val r = RINGS[i].radius + RINGS[i].spread * openEff
                val o = i * ORBIT_POINTS * 2
                for (j in 0 until ORBIT_POINTS) {
                    val ang = TAU * j / ORBIT_POINTS
                    val c = cos(ang) * r
                    val s = sin(ang) * r
                    val x = m[0] * c + m[1] * s
                    val y = m[3] * c + m[4] * s
                    val z = m[6] * c + m[7] * s
                    val p = 1 + z * PERSPECTIVE
                    orbit[o + j * 2] = (x * p).toFloat()
                    orbit[o + j * 2 + 1] = (y * p).toFloat()
                }
            }
        }

        val phi = openEff * FLARE
        val cp = cos(phi)
        val sp = sin(phi)
        for (k in 0 until SHARDS) {
            val i = ringOf[k]
            val ring = RINGS[i]
            val m = ringMatrix[i]
            val spin = if (i == 0) state.innerSpin else state.outerSpin
            val th = baseAngle[k] + spin
            val c = cos(th)
            val s = sin(th)

            val ux = m[0] * c + m[1] * s; val uy = m[3] * c + m[4] * s; val uz = m[6] * c + m[7] * s
            val vx = -m[0] * s + m[1] * c; val vy = -m[3] * s + m[4] * c; val vz = -m[6] * s + m[7] * c
            val sg = sign[k]
            val nx = m[2] * sg; val ny = m[5] * sg; val nz = m[8] * sg
            val r = ring.radius + ring.spread * openEff
            var cx = ux * r; var cy = uy * r; var cz = uz * r
            var a = 1.0
            if (assembly < 0.999) {

                val p = clamp01((assembly - 0.3 * k / (SHARDS - 1)) / 0.7)
                val e = 1 - (1 - p) * (1 - p) * (1 - p)
                val hw = (1 - e) * 6
                val hp = time * 1.3 + hover[k]
                val sx = scatter[k * 3] + sin(hp) * hw
                val sy = scatter[k * 3 + 1] + cos(hp * 0.8) * hw
                val sz = scatter[k * 3 + 2]
                cx = sx + (cx - sx) * e
                cy = sy + (cy - sy) * e
                cz = sz + (cz - sz) * e
                a = 0.15 + 0.85 * e
            }

            val ax = nx * cp + ux * sp; val ay = ny * cp + uy * sp; val az = nz * cp + uz * sp

            var fx = ay * vz - az * vy; var fy = az * vx - ax * vz; var fz = ax * vy - ay * vx
            if (fz < 0) { fx = -fx; fy = -fy; fz = -fz }
            val b = 0.3 + 0.7 * max(0.0, fx * LIGHT_X + fy * LIGHT_Y + fz * LIGHT_Z) + 0.22 * energy + 0.25 * pulse
            brightness[k] = (clamp01(b) * 15).roundToInt()
            depth[k] = cz.toFloat()
            alpha[k] = (a * (0.5 + 0.5 * clamp01((cz + 60) / 120))).toFloat()
            val o = k * 8
            val w = ring.halfWidth
            val lt = ring.tipLength
            val lb = ring.tailLength
            val lb2 = lb * 0.2
            project(cx + ax * lt, cy + ay * lt, cz + az * lt, o)
            project(cx + vx * w - ax * lb2, cy + vy * w - ay * lb2, cz + vz * w - az * lb2, o + 2)
            project(cx - ax * lb, cy - ay * lb, cz - az * lb, o + 4)
            project(cx - vx * w - ax * lb2, cy - vy * w - ay * lb2, cz - vz * w - az * lb2, o + 6)
        }

        for (k in 0 until SHARDS) order[k] = k
        for (k in 1 until SHARDS) {
            val v = order[k]
            var j = k - 1
            while (j >= 0 && depth[order[j]] > depth[v]) { order[j + 1] = order[j]; j-- }
            order[j + 1] = v
        }
    }

    private fun project(x: Double, y: Double, z: Double, o: Int) {
        val p = 1 + z * PERSPECTIVE
        points[o] = (x * p).toFloat()
        points[o + 1] = (y * p).toFloat()
    }

    companion object {
        const val UNIT = 172f
        const val CORE_RADIUS = 18f
        const val HALO_RADIUS = 62f
        const val ORBIT_POINTS = 28
        private const val PERSPECTIVE = 0.0035
        private const val FLARE = 0.6
        private const val LIGHT_X = -0.451
        private const val LIGHT_Y = -0.551
        private const val LIGHT_Z = 0.702

        val RINGS = listOf(
            Ring(count = 5, radius = 26.0, spread = 13.0, tilt = 1.05, twist = 0.25, halfWidth = 11.0, tipLength = 14.0, tailLength = 9.0, phase = 0.0),
            Ring(count = 7, radius = 33.0, spread = 18.0, tilt = -0.85, twist = 1.35, halfWidth = 12.0, tipLength = 15.0, tailLength = 10.0, phase = 0.4),
        )
        val SHARDS = RINGS.sumOf { it.count }

        private fun rotX(a: Double, o: DoubleArray) {
            val c = cos(a); val s = sin(a)
            o[0] = 1.0; o[1] = 0.0; o[2] = 0.0
            o[3] = 0.0; o[4] = c; o[5] = -s
            o[6] = 0.0; o[7] = s; o[8] = c
        }

        private fun rotY(a: Double, o: DoubleArray) {
            val c = cos(a); val s = sin(a)
            o[0] = c; o[1] = 0.0; o[2] = s
            o[3] = 0.0; o[4] = 1.0; o[5] = 0.0
            o[6] = -s; o[7] = 0.0; o[8] = c
        }

        private fun rotZ(a: Double, o: DoubleArray) {
            val c = cos(a); val s = sin(a)
            o[0] = c; o[1] = -s; o[2] = 0.0
            o[3] = s; o[4] = c; o[5] = 0.0
            o[6] = 0.0; o[7] = 0.0; o[8] = 1.0
        }

        private fun multiply(a: DoubleArray, b: DoubleArray, o: DoubleArray) {
            for (r in 0 until 3) for (c in 0 until 3) {
                o[r * 3 + c] = a[r * 3] * b[c] + a[r * 3 + 1] * b[3 + c] + a[r * 3 + 2] * b[6 + c]
            }
        }
    }
}

package com.example.chatlocalllm.ui.companion

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.chatlocalllm.ui.companion.CompanionGeometry.Companion.CORE_RADIUS
import com.example.chatlocalllm.ui.companion.CompanionGeometry.Companion.HALO_RADIUS
import com.example.chatlocalllm.ui.companion.CompanionGeometry.Companion.ORBIT_POINTS
import com.example.chatlocalllm.ui.companion.CompanionGeometry.Companion.RINGS
import com.example.chatlocalllm.ui.companion.CompanionGeometry.Companion.SHARDS
import com.example.chatlocalllm.ui.companion.CompanionGeometry.Companion.UNIT
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.isActive
import kotlin.math.roundToInt

@Composable
fun Companion(
    mode: CompanionMode,
    modifier: Modifier = Modifier,
    tokens: Flow<Unit> = emptyFlow(),
    failures: Flow<Unit> = emptyFlow(),
    reducedMotion: Boolean = false,
) {
    val state = remember { CompanionState(initialMode = mode, reducedMotion = reducedMotion) }
    val geometry = remember { CompanionGeometry().also { it.compute(state) } }
    val painter = remember { CompanionPainter() }
    val frame = remember { mutableIntStateOf(0) }

    SideEffect {
        state.mode = mode
        state.reducedMotion = reducedMotion
    }
    LaunchedEffect(Unit) {
        var last = 0L
        while (isActive) {
            withFrameNanos { now ->

                val dt = if (last == 0L) 0.0 else (now - last) / 1e9
                last = now
                state.step(dt)
                geometry.compute(state)
                frame.intValue++
            }
        }
    }
    LaunchedEffect(tokens) { tokens.collect { state.pulseToken() } }
    LaunchedEffect(failures) { failures.collect { state.shiverOnce() } }

    Canvas(modifier) {
        frame.intValue
        with(painter) { draw(state, geometry) }
    }
}

private class CompanionPainter {
    private val path = Path()
    private val shardStroke = Stroke(width = 0.8f, join = StrokeJoin.Round)
    private val orbitStroke = Stroke(width = 0.7f)
    private val ringStroke = Stroke(width = 1.2f)

    private val halo = Brush.radialGradient(
        0f to Color(0x8C2ABBD6), 0.45f to Color(0x2E2ABBD6), 1f to Color(0x002ABBD6),
        center = Offset.Zero, radius = HALO_RADIUS,
    )
    private val haloGrey = Brush.radialGradient(
        0f to Color(0x5978878E), 0.45f to Color(0x1A78878E), 1f to Color(0x0078878E),
        center = Offset.Zero, radius = HALO_RADIUS,
    )

    private val core = Brush.radialGradient(
        0f to Color(0xFFE9FBFF), 0.35f to Color(0xFF5BC2DC), 1f to Color(0xFF177C93),
        center = Offset(-3f, -3.5f), radius = CORE_RADIUS,
    )
    private val coreGrey = Brush.radialGradient(
        0f to Color(0xFFB8C4CA), 0.35f to Color(0xFF6B7B83), 1f to Color(0xFF33414A),
        center = Offset(-3f, -3.5f), radius = CORE_RADIUS,
    )
    private val hot = Brush.radialGradient(
        0f to Color(0xE6FFFFFF), 0.5f to Color(0x73B4F0FA), 1f to Color(0x00B4F0FA),
        center = Offset.Zero, radius = CORE_RADIUS,
    )
    private val eye = Brush.radialGradient(
        0f to Color(0xF2FFFFFF), 0.5f to Color(0x59FFFFFF), 1f to Color(0x00FFFFFF),
        center = Offset.Zero, radius = 7f,
    )
    private val innerRing = Color(0xFF0B1C26)

    private val fill: Array<Color> = Array(6 * 16) { i ->
        val d = i / 16
        val b = i % 16
        mix(ramp(TEAL, b / 15f), ramp(GREY, b / 15f), d / 5f)
    }
    private val stroke: Array<Color> = Array(6) { d ->
        mix(Color(155f / 255f, 227f / 255f, 242f / 255f), Color(140f / 255f, 154f / 255f, 161f / 255f), d / 5f).copy(alpha = 0.42f)
    }

    fun DrawScope.draw(s: CompanionState, g: CompanionGeometry) {
        if (size.minDimension <= 0f) return
        val unit = size.minDimension / UNIT * s.scale.toFloat()
        val pulse = s.pulse.toFloat()
        val glowEff = s.glowEffective.toFloat()
        val dimEff = s.dimEffective.toFloat()
        val teal = 1f - dimEff
        val energy = s.energy.toFloat()
        val open = s.open.toFloat()
        withTransform({
            translate(size.width / 2 + s.jitterX.toFloat() * unit, size.height / 2 + s.jitterY.toFloat() * unit)
            scale(unit, unit, pivot = Offset.Zero)
            if (s.jitterRotation != 0.0) rotate(Math.toDegrees(s.jitterRotation).toFloat(), pivot = Offset.Zero)
        }) {

            val haloAlpha = (glowEff * teal * (0.85f + 0.5f * pulse)).coerceIn(0f, 1f)
            if (haloAlpha > 0.01f) drawCircle(halo, HALO_RADIUS, Offset.Zero, alpha = haloAlpha)
            if (dimEff > 0.01f) drawCircle(haloGrey, HALO_RADIUS, Offset.Zero, alpha = (glowEff * dimEff).coerceIn(0f, 1f))
            val d = (dimEff * 5).roundToInt().coerceIn(0, 5)

            if (open > 0.02f) {
                val orbitAlpha = 0.16f * open.coerceIn(0f, 1f) * teal
                for (i in RINGS.indices) {
                    val o = i * ORBIT_POINTS * 2
                    path.rewind()
                    path.moveTo(g.orbit[o], g.orbit[o + 1])
                    for (j in 1 until ORBIT_POINTS) path.lineTo(g.orbit[o + j * 2], g.orbit[o + j * 2 + 1])
                    path.close()
                    drawPath(path, stroke[0], alpha = orbitAlpha, style = orbitStroke)
                }
            }

            var k = 0
            while (k < SHARDS && g.depth[g.order[k]] < 0f) { shard(g, g.order[k], d); k++ }

            withTransform({ scale(s.coreScale.toFloat(), s.coreScale.toFloat(), pivot = Offset.Zero) }) {
                drawCircle(core, CORE_RADIUS, Offset.Zero, alpha = teal)
                if (dimEff > 0.01f) drawCircle(coreGrey, CORE_RADIUS, Offset.Zero, alpha = dimEff)
                val hotAlpha = (0.55f * energy + 0.6f * pulse).coerceIn(0f, 1f) * teal
                if (hotAlpha > 0.01f) drawCircle(hot, CORE_RADIUS, Offset.Zero, alpha = hotAlpha)
                drawCircle(innerRing, CORE_RADIUS * 0.7f, Offset.Zero, alpha = 0.22f * (1 - dimEff * 0.6f), style = ringStroke)
                val eyeAlpha = ((0.85f - 0.5f * dimEff) * (0.7f + 0.3f * glowEff)).coerceIn(0f, 1f)
                withTransform({ translate(s.highlightX.toFloat(), s.highlightY.toFloat()) }) {
                    drawCircle(eye, 7f, Offset.Zero, alpha = eyeAlpha)
                }
            }

            while (k < SHARDS) { shard(g, g.order[k], d); k++ }
        }
    }

    private fun DrawScope.shard(g: CompanionGeometry, k: Int, d: Int) {
        val o = k * 8
        val p = g.points
        path.rewind()
        path.moveTo(p[o], p[o + 1])
        path.lineTo(p[o + 2], p[o + 3])
        path.lineTo(p[o + 4], p[o + 5])
        path.lineTo(p[o + 6], p[o + 7])
        path.close()
        val a = g.alpha[k].coerceIn(0f, 1f)
        drawPath(path, fill[d * 16 + g.brightness[k]], alpha = a)
        drawPath(path, stroke[d], alpha = a, style = shardStroke)
    }

    private companion object {
        val TEAL = listOf(Color(0xFF123E4C), Color(0xFF2ABBD6), Color(0xFFC4F1FA))
        val GREY = listOf(Color(0xFF273239), Color(0xFF4E5C64), Color(0xFF8C9AA1))

        fun mix(a: Color, b: Color, t: Float) = Color(
            red = a.red + (b.red - a.red) * t,
            green = a.green + (b.green - a.green) * t,
            blue = a.blue + (b.blue - a.blue) * t,
            alpha = a.alpha + (b.alpha - a.alpha) * t,
        )

        fun ramp(stops: List<Color>, t: Float) =
            if (t < 0.5f) mix(stops[0], stops[1], t * 2) else mix(stops[1], stops[2], (t - 0.5f) * 2)
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0B1C26, name = "idle")
@Composable
private fun CompanionIdlePreview() {
    Companion(mode = CompanionMode.IDLE, modifier = Modifier.size(172.dp))
}

@Preview(showBackground = true, backgroundColor = 0xFF0B1C26, name = "thinking")
@Composable
private fun CompanionThinkingPreview() {
    Companion(mode = CompanionMode.THINKING, modifier = Modifier.size(172.dp))
}

@Preview(showBackground = true, backgroundColor = 0xFF0B1C26, name = "streaming")
@Composable
private fun CompanionStreamingPreview() {
    Companion(mode = CompanionMode.STREAMING, modifier = Modifier.size(172.dp))
}

@Preview(showBackground = true, backgroundColor = 0xFF0B1C26, name = "no model")
@Composable
private fun CompanionNoModelPreview() {
    Companion(mode = CompanionMode.NONE, modifier = Modifier.size(172.dp))
}

@Preview(showBackground = true, backgroundColor = 0xFF0B1C26, name = "every mode, compact")
@Composable
private fun CompanionCompactPreview() {
    Row {
        CompanionMode.entries.forEach { Companion(mode = it, modifier = Modifier.size(56.dp).padding(2.dp)) }
    }
}

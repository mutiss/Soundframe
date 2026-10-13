package com.mutissx.soundframe.core.graphics

import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * ECHO — the recording as ripples. Each ring carries one moment of the sound: its brightness is
 * the energy envelope at that moment and its outline is distorted by spectral harmonics folded
 * into the sound's symmetry. Rings travel outward over time like echoes; behind them, two offset
 * families of fine circles create a moiré interference field.
 */
internal object EchoRenderer : ArtRenderer {
    override fun prepare(scene: ArtScene, width: Int, height: Int): PreparedArt = Prepared(scene, width, height)

    private const val SEGMENTS = 220
    private const val HARMONICS = 6

    private class Prepared(private val scene: ArtScene, override val width: Int, override val height: Int) : PreparedArt {
        private val p = scene.params
        private val cx = width / 2f
        private val cy = height / 2f
        private val maxRadius = min(width, height) * 0.47f
        private val ringCount = (16 + p.complexity * 34f).roundToInt().coerceIn(12, 52)
        /** Unit-radius ring outlines, scaled at draw time. */
        private val rings: Array<Path>
        private val dashed: BooleanArray
        private val ringColor: IntArray
        private val ringEnergy: FloatArray
        private val strokePx = max(1f, min(width, height) * 0.0018f * (0.8f + 0.8f * p.weight))
        private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeJoin = Paint.Join.ROUND
            blendMode = scene.glowBlend
        }
        private val dashedPaint = Paint(ringPaint).apply {
            pathEffect = DashPathEffect(floatArrayOf(0.012f, 0.018f), 0f)
        }
        private val moirePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = max(1f, min(width, height) * 0.0012f)
        }
        private val corePaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val moireOffset: Float

        init {
            val rng = Rng(p.seed xor 0xEC40L)
            val amps = FloatArray(HARMONICS) { h -> p.spectrumAt(h / (HARMONICS - 1f)) * (0.35f + 0.65f * p.contrast) / (h + 1f) }
            val phases = FloatArray(HARMONICS) { rng.range(0f, 6.283f) }
            val distortion = 0.05f * (0.4f + p.weight) * (0.6f + p.energy)
            val twist = rng.range(0.05f, 0.25f) * (0.5f + p.turbulence)
            val interference = 0.025f * p.turbulence
            val n = p.symmetry
            val dashEvery = max(2, 14 - p.repetition)

            rings = Array(ringCount) { j ->
                val path = Path()
                for (s in 0..SEGMENTS) {
                    val th = s / SEGMENTS.toFloat() * 2f * PI.toFloat()
                    var d = 0f
                    for (h in 0 until HARMONICS) d += amps[h] * sin(n * (h + 1) * th + phases[h] + j * twist)
                    d = d * distortion * 3f + interference * sin(th * n * 5f + j * 0.9f)
                    val r = 1f + d
                    val x = r * cos(th)
                    val y = r * sin(th)
                    if (s == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                path.close()
                path
            }
            dashed = BooleanArray(ringCount) { it % dashEvery == dashEvery - 1 }
            ringColor = IntArray(ringCount) { scene.color(it / (ringCount - 1f)) }
            ringEnergy = FloatArray(ringCount) { p.envelopeAt(it / (ringCount - 1f)) }

            moireOffset = min(width, height) * (0.015f + 0.05f * p.spread)
            moirePaint.color = ColorUtil.withAlpha(scene.color(0.5f), if (scene.isLight) 0.08f else 0.07f + 0.05f * p.intensity)
            corePaint.shader = RadialGradient(
                cx, cy, maxRadius * 0.35f,
                intArrayOf(ColorUtil.withAlpha(scene.color(0f), 0.7f), ColorUtil.withAlpha(scene.color(0.3f), 0.18f), ColorUtil.withAlpha(scene.color(0.3f), 0f)),
                floatArrayOf(0f, 0.35f, 1f), Shader.TileMode.CLAMP,
            )
        }

        override fun drawStatic(canvas: Canvas) {
            val extent = hypot(width.toFloat(), height.toFloat()) / 2f
            val spacing = max(4f, min(width, height) * (0.011f - 0.004f * p.detail))
            var r = spacing
            while (r < extent) {
                canvas.drawCircle(cx - moireOffset, cy, r, moirePaint)
                canvas.drawCircle(cx + moireOffset, cy + moireOffset * 0.4f, r, moirePaint)
                r += spacing
            }
        }

        override fun drawDynamic(canvas: Canvas, timeSec: Float) {
            val speed = 0.35f + 1.2f * p.motion
            val travel = (timeSec * speed) % ringCount
            val corePulse = 0.85f + 0.15f * sin(timeSec * 2.4f) * (0.5f + p.energy)
            canvas.save()
            canvas.scale(corePulse, corePulse, cx, cy)
            canvas.drawCircle(cx, cy, maxRadius * 0.35f, corePaint)
            canvas.restore()

            for (j in 0 until ringCount) {
                val pos = ((j + travel) % ringCount) / ringCount
                if (pos < 0.015f) continue
                val radius = maxRadius * pos.pow(0.85f)
                // Fade in near the centre, out at the rim.
                val fade = sin(PI.toFloat() * pos).coerceIn(0f, 1f)
                val alpha = fade * (0.18f + 0.75f * ringEnergy[j]) * (0.45f + 0.55f * p.intensity)
                val paint = if (dashed[j]) dashedPaint else ringPaint
                paint.color = ColorUtil.withAlpha(ringColor[j], alpha.coerceIn(0f, 1f))
                paint.strokeWidth = strokePx * (1f + 1.6f * ringEnergy[j]) / radius
                canvas.save()
                canvas.translate(cx, cy)
                canvas.rotate(timeSec * (2f + 4f * p.motion) * if (j % 2 == 0) 1f else -1f)
                canvas.scale(radius, radius)
                canvas.drawPath(rings[j], paint)
                canvas.restore()
            }
        }
    }
}

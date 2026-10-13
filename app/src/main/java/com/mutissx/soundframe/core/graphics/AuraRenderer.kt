package com.mutissx.soundframe.core.graphics

import android.graphics.BlendMode
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * AURA — luminous silk ribbons. Each ribbon is a bundle of fine strands whose spacing twists along
 * the curve; the ribbon's path follows the recording's energy envelope and its thickness swells
 * with loudness. Bass makes ribbons heavier and more curved, flux makes them flow faster.
 */
internal object AuraRenderer : ArtRenderer {
    override fun prepare(scene: ArtScene, width: Int, height: Int): PreparedArt = Prepared(scene, width, height)

    private const val POINTS = 64
    private const val BANDS = 3
    private const val MAX_STRANDS = 22

    private class Ribbon(
        val baseY: Float,
        val amplitude: Float,
        val thickness: Float,
        val freqA: Float,
        val freqB: Float,
        val phaseA: Float,
        val phaseB: Float,
        val twist: Float,
        val speed: Float,
        val envGain: Float,
        val strands: Int,
        val shader: Shader,
    )

    private class Prepared(private val scene: ArtScene, override val width: Int, override val height: Int) : PreparedArt {
        private val p = scene.params
        private val minDim = min(width, height).toFloat()
        private val rng = Rng(p.seed xor 0xA0BAL)
        private val ribbons: List<Ribbon>
        private val env = FloatArray(POINTS)
        private val xs = FloatArray(POINTS)
        private val centers: Array<FloatArray>
        private val halfWidths: Array<FloatArray>
        private val path = Path()
        private val bandLines = Array(BANDS) { FloatArray(MAX_STRANDS * (POINTS - 1) * 4) }
        private val bandSize = IntArray(BANDS)
        private val strandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.BUTT
            strokeJoin = Paint.Join.ROUND
            blendMode = scene.glowBlend
        }
        private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            blendMode = scene.glowBlend
        }
        private val dustPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            strokeCap = Paint.Cap.ROUND
            blendMode = if (scene.isLight) BlendMode.MULTIPLY else BlendMode.PLUS
        }
        private val dust: FloatArray // ribbon, u, offset, phase
        private val dustPoints: FloatArray

        init {
            for (k in 0 until POINTS) {
                val u = k / (POINTS - 1f)
                xs[k] = -0.08f * width + u * 1.16f * width
                env[k] = p.envelopeAt(u)
            }
            val count = (3 + p.complexity * 7f + p.detail * 3f).roundToInt().coerceIn(3, 14)
            val span = height * (0.16f + 0.30f * p.spread) * if (height > width) 0.75f else 1f
            ribbons = List(count) { i ->
                val f = if (count == 1) 0.5f else i / (count - 1f)
                val t = f * 0.9f + rng.range(-0.05f, 0.05f)
                Ribbon(
                    baseY = height * 0.5f + (f - 0.5f) * span + rng.range(-0.03f, 0.03f) * height,
                    amplitude = minDim * (0.06f + 0.16f * p.weight + 0.07f * p.contrast) * rng.range(0.6f, 1.2f),
                    thickness = minDim * (0.012f + 0.045f * p.weight + 0.03f * p.energy) * rng.range(0.55f, 1.3f),
                    freqA = (2f * PI.toFloat()) * rng.range(0.6f, 1.4f) * (0.7f + 1.4f * p.turbulence),
                    freqB = (2f * PI.toFloat()) * rng.range(1.6f, 3.2f) * (0.6f + p.turbulence),
                    phaseA = rng.range(0f, 6.283f),
                    phaseB = rng.range(0f, 6.283f),
                    twist = rng.range(2.5f, 6.5f),
                    speed = (0.12f + 0.55f * p.motion) * rng.range(0.7f, 1.3f) * if (rng.nextFloat() > 0.5f) 1f else -1f,
                    envGain = 0.6f + 1.2f * p.contrast,
                    strands = (8 + p.complexity * 14f).roundToInt().coerceIn(6, MAX_STRANDS),
                    shader = LinearGradient(
                        0f, 0f, width.toFloat(), 0f,
                        intArrayOf(scene.color(t - 0.18f), scene.color(t), scene.color(t + 0.22f), scene.color(t + 0.05f)),
                        null, Shader.TileMode.CLAMP,
                    ),
                )
            }
            centers = Array(count) { FloatArray(POINTS) }
            halfWidths = Array(count) { FloatArray(POINTS) }
            val dustCount = (p.detail * 140f + p.complexity * 40f).toInt()
            dust = FloatArray(dustCount * 4)
            for (d in 0 until dustCount) {
                dust[d * 4] = rng.nextInt(count).toFloat()
                dust[d * 4 + 1] = rng.nextFloat()
                dust[d * 4 + 2] = rng.gaussian() * 1.6f
                dust[d * 4 + 3] = rng.range(0f, 6.283f)
            }
            dustPoints = FloatArray(dustCount * 2)
            strandPaint.strokeWidth = max(1f, minDim * 0.0016f * (0.7f + 0.6f * p.detail))
            dustPaint.strokeWidth = max(1.5f, minDim * 0.004f)
        }

        override fun drawStatic(canvas: Canvas) = Unit

        override fun drawDynamic(canvas: Canvas, timeSec: Float) {
            val baseAlpha = (0.07f + 0.2f * p.intensity) * if (scene.isLight) 0.8f else 1f
            for ((r, ribbon) in ribbons.withIndex()) {
                val c = centers[r]
                val hw = halfWidths[r]
                val tt = timeSec * ribbon.speed
                for (k in 0 until POINTS) {
                    val u = k / (POINTS - 1f)
                    val e = env[k]
                    val wave = sin(u * ribbon.freqA + ribbon.phaseA + tt) * 0.6f + sin(u * ribbon.freqB + ribbon.phaseB - tt * 0.7f) * 0.4f
                    c[k] = ribbon.baseY + ribbon.amplitude * (wave + (e - 0.5f) * ribbon.envGain)
                    hw[k] = ribbon.thickness * (0.25f + 0.95f * e) * (0.88f + 0.12f * sin(timeSec * 0.9f + k * 0.11f + r))
                }

                // Soft halo underneath the strands.
                glowPaint.shader = ribbon.shader
                // One soft halo per ribbon (a changing Path is re-rasterised every frame, so keep few).
                buildCenterPath(c)
                glowPaint.strokeWidth = ribbon.thickness * 2.4f
                glowPaint.alpha = (255 * baseAlpha * 0.4f).toInt()
                canvas.drawPath(path, glowPaint)

                // Strands are batched as line segments: GPU-native, no path rasterisation.
                // Bands group strands by distance from the ribbon's centre, because edges catch
                // more light than the middle — that's what gives the silk its sheen.
                bandSize.fill(0)
                val n = ribbon.strands
                for (s in 0 until n) {
                    val f = s / (n - 1f) * 2f - 1f
                    val band = (abs(f) * (BANDS - 0.001f)).toInt()
                    val twistPhase = ribbon.twist * (1f + 0.15f * s / n) + tt * 0.8f
                    val out = bandLines[band]
                    var o = bandSize[band]
                    var px = 0f
                    var py = 0f
                    for (k in 0 until POINTS) {
                        val y = c[k] + hw[k] * f * cos(k / (POINTS - 1f) * 3.1f + twistPhase)
                        if (k > 0) {
                            out[o] = px; out[o + 1] = py; out[o + 2] = xs[k]; out[o + 3] = y
                            o += 4
                        }
                        px = xs[k]; py = y
                    }
                    bandSize[band] = o
                }
                strandPaint.shader = ribbon.shader
                for (b in 0 until BANDS) {
                    if (bandSize[b] == 0) continue
                    strandPaint.alpha = (255 * baseAlpha * (0.5f + 0.5f * (b + 0.5f) / BANDS)).toInt().coerceIn(0, 255)
                    canvas.drawLines(bandLines[b], 0, bandSize[b], strandPaint)
                }
            }
            drawDust(canvas, timeSec)
        }

        private fun buildCenterPath(c: FloatArray) {
            path.rewind()
            var px = 0f
            var py = 0f
            for (k in 0 until POINTS) {
                val x = xs[k]
                val y = c[k]
                if (k == 0) path.moveTo(x, y) else path.quadTo(px, py, (px + x) / 2f, (py + y) / 2f)
                px = x; py = y
            }
            path.lineTo(px, py)
        }

        private fun drawDust(canvas: Canvas, timeSec: Float) {
            val count = dust.size / 4
            if (count == 0) return
            for (d in 0 until count) {
                val r = dust[d * 4].toInt()
                val u = (dust[d * 4 + 1] + timeSec * 0.01f * (1 + r % 3)) % 1f
                val k = (u * (POINTS - 1)).toInt()
                dustPoints[d * 2] = xs[k]
                dustPoints[d * 2 + 1] = centers[r][k] + halfWidths[r][k] * dust[d * 4 + 2]
            }
            dustPaint.color = scene.color(0.05f)
            dustPaint.alpha = (255 * (0.35f + 0.4f * p.intensity) * (0.6f + 0.4f * sin(timeSec * 1.7f))).toInt().coerceIn(0, 255)
            canvas.drawPoints(dustPoints, 0, count * 2, dustPaint)
        }
    }
}

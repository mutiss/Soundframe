package com.mutissx.soundframe.core.graphics

import android.graphics.BlendMode
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

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
    private const val HALO_COLUMNS = 160

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
        private val strandY = FloatArray(POINTS)
        private val strandHalfWidth = max(1f, minDim * 0.0016f * (0.7f + 0.6f * p.detail)) / 2f
        private val strandMesh = StrokeMesh(MAX_STRANDS * POINTS)
        private val strandPaint = Paint().apply {
            color = 0xFFFFFFFF.toInt()
            blendMode = scene.glowBlend
        }
        private val glowMesh = StrokeMesh(HALO_COLUMNS)
        private val segUx = FloatArray(POINTS - 1)
        private val segUy = FloatArray(POINTS - 1)
        private val segLen = FloatArray(POINTS - 1)
        private val glowPaint = Paint().apply {
            color = 0xFFFFFFFF.toInt()
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
                drawHalo(canvas, c, ribbon.thickness * 1.2f, ColorUtil.withAlpha(0xFFFFFFFF.toInt(), baseAlpha * 0.4f))

                // All of a ribbon's strands go out as one mesh; anti-aliased wide lines fall back to
                // a path draw per segment on the GPU backend. Bands group strands by distance from
                // the ribbon's centre, because edges catch more light than the middle — that's what
                // gives the silk its sheen.
                val n = ribbon.strands
                for (s in 0 until n) {
                    val f = s / (n - 1f) * 2f - 1f
                    val band = (abs(f) * (BANDS - 0.001f)).toInt()
                    val twistPhase = ribbon.twist * (1f + 0.15f * s / n) + tt * 0.8f
                    val color = ColorUtil.withAlpha(0xFFFFFFFF.toInt(), baseAlpha * (0.5f + 0.5f * (band + 0.5f) / BANDS))
                    for (k in 0 until POINTS) {
                        strandY[k] = c[k] + hw[k] * f * cos(k / (POINTS - 1f) * 3.1f + twistPhase)
                    }
                    strandMesh.begin(canvas, strandPaint, POINTS)
                    for (k in 0 until POINTS) {
                        val prev = max(k - 1, 0)
                        val next = min(k + 1, POINTS - 1)
                        val tx = xs[next] - xs[prev]
                        val ty = strandY[next] - strandY[prev]
                        val len = sqrt(tx * tx + ty * ty)
                        strandMesh.point(xs[k], strandY[k], -ty / len, tx / len, strandHalfWidth, color)
                    }
                }
                strandPaint.shader = ribbon.shader
                strandMesh.flush(canvas, strandPaint)
            }
            drawDust(canvas, timeSec)
        }

        /**
         * The halo is the centre line stroked [radius] either side. A wide stroked path is rasterised
         * on the CPU every frame, so it's drawn as a mesh instead. The line is a graph of x, so the
         * stroke covers one vertical span per column: the union of each segment's capsule there.
         */
        private fun drawHalo(canvas: Canvas, c: FloatArray, radius: Float, color: Int) {
            glowMesh.begin(canvas, glowPaint, HALO_COLUMNS)
            for (k in 0 until POINTS - 1) {
                val dx = xs[k + 1] - xs[k]
                val dy = c[k + 1] - c[k]
                val len = sqrt(dx * dx + dy * dy)
                segLen[k] = len
                segUx[k] = dx / len
                segUy[k] = dy / len
            }
            val left = xs[0] - radius
            val right = xs[POINTS - 1] + radius
            var first = 0
            for (i in 0 until HALO_COLUMNS) {
                val x = left + (right - left) * i / (HALO_COLUMNS - 1f)
                while (first < POINTS - 2 && xs[first + 1] < x - radius) first++
                var top = Float.MAX_VALUE
                var bottom = -Float.MAX_VALUE
                var k = first
                while (k < POINTS - 1 && xs[k] <= x + radius) {
                    val ax = xs[k]; val ay = c[k]
                    val bx = xs[k + 1]; val by = c[k + 1]
                    // Round ends.
                    val da = x - ax
                    if (abs(da) <= radius) {
                        val h = sqrt(radius * radius - da * da)
                        top = min(top, ay - h); bottom = max(bottom, ay + h)
                    }
                    val db = x - bx
                    if (abs(db) <= radius) {
                        val h = sqrt(radius * radius - db * db)
                        top = min(top, by - h); bottom = max(bottom, by + h)
                    }
                    // The band along the segment: within radius of the line, projecting inside it.
                    val len = segLen[k]
                    val ux = segUx[k]
                    val uy = segUy[k]
                    var lo = ay + (uy * da - radius) / ux
                    var hi = ay + (uy * da + radius) / ux
                    if (uy != 0f) {
                        val s0 = ay - ux * da / uy
                        val s1 = ay + (len - ux * da) / uy
                        lo = max(lo, min(s0, s1)); hi = min(hi, max(s0, s1))
                    } else if (x < ax || x > bx) {
                        hi = lo - 1f
                    }
                    if (hi >= lo) {
                        top = min(top, lo); bottom = max(bottom, hi)
                    }
                    k++
                }
                if (bottom < top) { top = 0f; bottom = 0f }
                glowMesh.point(x, (top + bottom) / 2f, 0f, 1f, (bottom - top) / 2f, color)
            }
            glowMesh.flush(canvas, glowPaint)
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

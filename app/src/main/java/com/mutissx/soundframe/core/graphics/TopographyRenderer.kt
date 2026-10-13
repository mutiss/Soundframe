package com.mutissx.soundframe.core.graphics

import android.graphics.Canvas
import android.graphics.Paint
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * TOPOGRAPHY — the sound as a landscape. An elevation field combines domain-warped fractal noise
 * (texture from the sound's noisiness) with summits whose heights come from the energy envelope.
 * Contours are traced once with marching squares; animation sweeps a soft survey light across the
 * elevation bands, timed by the sound's motion.
 */
internal object TopographyRenderer : ArtRenderer {
    override fun prepare(scene: ArtScene, width: Int, height: Int): PreparedArt = Prepared(scene, width, height)

    private class Prepared(private val scene: ArtScene, override val width: Int, override val height: Int) : PreparedArt {
        private val p = scene.params
        private val minDim = min(width, height).toFloat()
        private val levels = (12 + p.complexity * 26f).roundToInt().coerceIn(10, 40)
        private val contours: Array<FloatArray>
        private val markers: FloatArray
        private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.BUTT
        }
        private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.BUTT
            color = ColorUtil.withAlpha(Backgrounds.deep(scene.background), if (scene.isLight) 0.12f else 0.55f)
        }
        private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.BUTT
            blendMode = scene.glowBlend
        }
        private val markerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = max(1f, minDim * 0.0014f)
        }
        private val baseWidth = max(1f, minDim * 0.0016f * (0.8f + 0.5f * p.weight))

        init {
            val rng = Rng(p.seed xor 0x7090L)
            val noise = PerlinNoise(p.seed)
            val warp = PerlinNoise(p.seed xor 0x55L)
            val cols = (width / 7).coerceIn(90, 300)
            val cell = width / (cols - 1f)
            val rows = (height / cell).toInt() + 2
            val field = FloatArray(cols * rows)

            val peakCount = p.repetition
            val px = FloatArray(peakCount) { rng.range(0.1f, 0.9f) * width }
            val py = FloatArray(peakCount) { rng.range(0.1f, 0.9f) * height }
            val ph = FloatArray(peakCount) { (0.25f + p.envelopeAt(it / max(1f, peakCount - 1f))) * (0.5f + p.contrast) }
            val sigma = minDim * (0.1f + 0.14f * p.weight)
            val freq = (1.4f + 2.4f * p.turbulence) / minDim
            val warpAmount = minDim * (0.08f + 0.18f * p.turbulence)
            val noiseGain = 0.35f + 0.45f * p.turbulence

            var lo = Float.MAX_VALUE
            var hi = -Float.MAX_VALUE
            for (r in 0 until rows) {
                for (c in 0 until cols) {
                    val x = c * cell
                    val y = r * cell
                    val wx = x + warp.fbm(x * freq * 0.6f, y * freq * 0.6f, 2) * warpAmount
                    val wy = y + warp.fbm(y * freq * 0.6f + 31f, x * freq * 0.6f, 2) * warpAmount
                    var v = noise.fbm(wx * freq, wy * freq, 4) * noiseGain
                    for (k in 0 until peakCount) {
                        val dx = wx - px[k]
                        val dy = wy - py[k]
                        v += ph[k] * exp(-(dx * dx + dy * dy) / (2f * sigma * sigma))
                    }
                    field[r * cols + c] = v
                    if (v < lo) lo = v
                    if (v > hi) hi = v
                }
            }
            val range = max(hi - lo, 1e-6f)
            for (i in field.indices) field[i] = (field[i] - lo) / range

            contours = Array(levels) { l ->
                val iso = (l + 0.5f) / levels
                marchingSquares(field, cols, rows, cell, iso)
            }

            markers = FloatArray(peakCount * 2) { if (it % 2 == 0) px[it / 2] else py[it / 2] }
            markerPaint.color = ColorUtil.withAlpha(scene.color(0.05f), 0.55f)
        }

        override fun drawStatic(canvas: Canvas) {
            val shadowOffset = minDim * 0.004f
            for (l in 0 until levels) {
                val segments = contours[l]
                if (segments.isEmpty()) continue
                val major = l % 5 == 4
                val t = l / (levels - 1f)
                val w = if (major) baseWidth * 2.2f else baseWidth
                shadowPaint.strokeWidth = w * 1.6f
                canvas.save()
                canvas.translate(shadowOffset * 0.6f, shadowOffset)
                canvas.drawLines(segments, shadowPaint)
                canvas.restore()
                linePaint.strokeWidth = w
                val alpha = (0.28f + 0.55f * t * (0.4f + p.contrast)) * (0.55f + 0.45f * p.intensity) + if (major) 0.15f else 0f
                linePaint.color = ColorUtil.withAlpha(scene.color(1f - t), alpha.coerceIn(0f, 1f))
                canvas.drawLines(segments, linePaint)
            }
            val r = minDim * 0.008f
            for (k in 0 until markers.size / 2) {
                val x = markers[k * 2]
                val y = markers[k * 2 + 1]
                canvas.drawCircle(x, y, r, markerPaint)
                canvas.drawLine(x + r * 1.6f, y - r * 1.6f, x + r * 4.5f, y - r * 4.5f, markerPaint)
            }
        }

        override fun drawDynamic(canvas: Canvas, timeSec: Float) {
            val sweep = ((timeSec * (0.05f + 0.1f * p.motion)) % 1f) * (levels + 6) - 3f
            for (l in 0 until levels) {
                val d = l - sweep
                val light = exp(-d * d / 3f)
                if (light < 0.04f) continue
                val segments = contours[l]
                if (segments.isEmpty()) continue
                val shimmer = 0.85f + 0.15f * sin(timeSec * 3f + l)
                val c = scene.color(1f - l / (levels - 1f) - 0.15f)
                glowPaint.strokeWidth = baseWidth * 5f
                glowPaint.color = ColorUtil.withAlpha(c, 0.16f * light * shimmer)
                canvas.drawLines(segments, glowPaint)
                glowPaint.strokeWidth = baseWidth * 1.4f
                glowPaint.color = ColorUtil.withAlpha(ColorUtil.lerp(c, 0xFFFFFFFF.toInt(), 0.4f), 0.8f * light)
                canvas.drawLines(segments, glowPaint)
            }
        }
    }

    /** Classic marching squares with linear edge interpolation; returns line segments (x0,y0,x1,y1)*. */
    fun marchingSquares(field: FloatArray, cols: Int, rows: Int, cell: Float, iso: Float): FloatArray {
        val out = FloatList(2048)
        for (r in 0 until rows - 1) {
            for (c in 0 until cols - 1) {
                val a = field[r * cols + c]
                val b = field[r * cols + c + 1]
                val d = field[(r + 1) * cols + c]
                val e = field[(r + 1) * cols + c + 1]
                var idx = 0
                if (a > iso) idx = idx or 8
                if (b > iso) idx = idx or 4
                if (e > iso) idx = idx or 2
                if (d > iso) idx = idx or 1
                if (idx == 0 || idx == 15) continue
                val x = c * cell
                val y = r * cell
                // Edge crossings: top (a-b), right (b-e), bottom (d-e), left (a-d).
                val tx = x + cell * interp(a, b, iso); val ty = y
                val rx = x + cell; val ry = y + cell * interp(b, e, iso)
                val bx = x + cell * interp(d, e, iso); val by = y + cell
                val lx = x; val ly = y + cell * interp(a, d, iso)
                when (idx) {
                    1, 14 -> out.add4(lx, ly, bx, by)
                    2, 13 -> out.add4(bx, by, rx, ry)
                    3, 12 -> out.add4(lx, ly, rx, ry)
                    4, 11 -> out.add4(tx, ty, rx, ry)
                    6, 9 -> out.add4(tx, ty, bx, by)
                    7, 8 -> out.add4(lx, ly, tx, ty)
                    5 -> { out.add4(lx, ly, tx, ty); out.add4(bx, by, rx, ry) }
                    10 -> { out.add4(tx, ty, rx, ry); out.add4(lx, ly, bx, by) }
                }
            }
        }
        return out.toArray()
    }

    private fun interp(v0: Float, v1: Float, iso: Float): Float {
        val d = v1 - v0
        return if (kotlin.math.abs(d) < 1e-6f) 0.5f else ((iso - v0) / d).coerceIn(0f, 1f)
    }
}

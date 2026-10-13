package com.mutissx.soundframe.core.graphics

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
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
    private const val DASH_ON = 0.012f
    private const val DASH_PERIOD = 0.03f

    private class Prepared(private val scene: ArtScene, override val width: Int, override val height: Int) : PreparedArt {
        private val p = scene.params
        private val cx = width / 2f
        private val cy = height / 2f
        private val maxRadius = min(width, height) * 0.47f
        private val ringCount = (16 + p.complexity * 34f).roundToInt().coerceIn(12, 52)
        /** Unit-radius ring outlines as x,y pairs, transformed at draw time. */
        private val outlines: Array<FloatArray>
        /** Unit normals matching [outlines]. */
        private val normals: Array<FloatArray>
        /** Dashed rings as x0,y0,x1,y1,nx,ny per dash in unit space; null for solid rings. */
        private val dashes: Array<FloatArray?>
        private val ringColor: IntArray
        private val ringEnergy: FloatArray
        private val strokePx = max(1f, min(width, height) * 0.0018f * (0.8f + 0.8f * p.weight))
        private val mesh = StrokeMesh()
        private val meshPaint = Paint().apply {
            color = 0xFFFFFFFF.toInt()
            blendMode = scene.glowBlend
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

            outlines = Array(ringCount) { j ->
                val pts = FloatArray((SEGMENTS + 1) * 2)
                for (s in 0..SEGMENTS) {
                    val th = s / SEGMENTS.toFloat() * 2f * PI.toFloat()
                    var d = 0f
                    for (h in 0 until HARMONICS) d += amps[h] * sin(n * (h + 1) * th + phases[h] + j * twist)
                    d = d * distortion * 3f + interference * sin(th * n * 5f + j * 0.9f)
                    val r = 1f + d
                    pts[s * 2] = r * cos(th)
                    pts[s * 2 + 1] = r * sin(th)
                }
                pts
            }
            normals = Array(ringCount) { j -> normalsOf(outlines[j]) }
            dashes = Array(ringCount) { j -> if (j % dashEvery == dashEvery - 1) dashesOf(outlines[j]) else null }
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

        /** The outline is closed (last point == first), so neighbours wrap around the seam. */
        private fun normalsOf(pts: FloatArray): FloatArray {
            val out = FloatArray(pts.size)
            for (s in 0..SEGMENTS) {
                val prev = if (s == 0) SEGMENTS - 1 else s - 1
                val next = if (s == SEGMENTS) 1 else s + 1
                val tx = pts[next * 2] - pts[prev * 2]
                val ty = pts[next * 2 + 1] - pts[prev * 2 + 1]
                val len = hypot(tx, ty)
                out[s * 2] = -ty / len
                out[s * 2 + 1] = tx / len
            }
            return out
        }

        /** Cuts a closed outline into short straight dashes along its length. */
        private fun dashesOf(pts: FloatArray): FloatArray {
            val path = Path()
            path.moveTo(pts[0], pts[1])
            for (s in 1..SEGMENTS) path.lineTo(pts[s * 2], pts[s * 2 + 1])
            val measure = PathMeasure(path, false)
            val out = FloatList()
            val a = FloatArray(2)
            val b = FloatArray(2)
            var d = 0f
            while (d < measure.length) {
                measure.getPosTan(d, a, null)
                measure.getPosTan(min(d + DASH_ON, measure.length), b, null)
                val len = hypot(b[0] - a[0], b[1] - a[1])
                if (len > 0f) {
                    out.add4(a[0], a[1], b[0], b[1])
                    out.add(-(b[1] - a[1]) / len)
                    out.add((b[0] - a[0]) / len)
                }
                d += DASH_PERIOD
            }
            return out.toArray()
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

            val spin = timeSec * (2f + 4f * p.motion) * (PI.toFloat() / 180f)
            for (j in 0 until ringCount) {
                val pos = ((j + travel) % ringCount) / ringCount
                if (pos < 0.015f) continue
                val radius = maxRadius * pos.pow(0.85f)
                // Fade in near the centre, out at the rim.
                val fade = sin(PI.toFloat() * pos).coerceIn(0f, 1f)
                val alpha = fade * (0.18f + 0.75f * ringEnergy[j]) * (0.45f + 0.55f * p.intensity)
                val color = ColorUtil.withAlpha(ringColor[j], alpha.coerceIn(0f, 1f))
                val halfWidth = strokePx * (1f + 1.6f * ringEnergy[j]) / 2f
                // Rings are rotated and scaled on the CPU so they can all share one mesh.
                val angle = if (j % 2 == 0) spin else -spin
                val cs = cos(angle)
                val sn = sin(angle)
                val dash = dashes[j]
                if (dash != null) {
                    for (o in 0 until dash.size step 6) {
                        val nx = cs * dash[o + 4] - sn * dash[o + 5]
                        val ny = sn * dash[o + 4] + cs * dash[o + 5]
                        mesh.begin(canvas, meshPaint, 2)
                        mesh.point(cx + radius * (cs * dash[o] - sn * dash[o + 1]), cy + radius * (sn * dash[o] + cs * dash[o + 1]), nx, ny, halfWidth, color)
                        mesh.point(cx + radius * (cs * dash[o + 2] - sn * dash[o + 3]), cy + radius * (sn * dash[o + 2] + cs * dash[o + 3]), nx, ny, halfWidth, color)
                    }
                } else {
                    val pts = outlines[j]
                    val nrm = normals[j]
                    mesh.begin(canvas, meshPaint, SEGMENTS + 1)
                    for (s in 0..SEGMENTS) {
                        val x = pts[s * 2]
                        val y = pts[s * 2 + 1]
                        val nx = nrm[s * 2]
                        val ny = nrm[s * 2 + 1]
                        mesh.point(cx + radius * (cs * x - sn * y), cy + radius * (sn * x + cs * y), cs * nx - sn * ny, sn * nx + cs * ny, halfWidth, color)
                    }
                }
            }
            mesh.flush(canvas, meshPaint)
        }
    }
}

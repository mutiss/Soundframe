package com.mutissx.soundframe.core.graphics

import android.graphics.BlendMode
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * BLOOM — an abstract flower. Layers of petals whose count is the sound's rotational symmetry and
 * whose silhouette is sculpted by the spectrum profile (mirrored inside each petal so the flower
 * stays symmetric). Bass rounds the petals, noise frays their tips, rhythm drives the breathing.
 */
internal object BloomRenderer : ArtRenderer {
    override fun prepare(scene: ArtScene, width: Int, height: Int): PreparedArt = Prepared(scene, width, height)

    private const val STEPS = 420

    private class Layer(
        val petals: Path,
        val veins: Path,
        val fill: Paint,
        val shadow: Paint,
        val outline: Paint,
        val veinPaint: Paint,
        val rotation: Float,
        val spin: Float,
        val breathPhase: Float,
    )

    private class Prepared(private val scene: ArtScene, override val width: Int, override val height: Int) : PreparedArt {
        private val p = scene.params
        private val rng = Rng(p.seed xor 0xB100L)
        private val cx = width / 2f
        private val cy = height / 2f
        private val radius = min(width, height) * (0.36f + 0.06f * p.energy)
        private val layers: List<Layer>
        private val halo = Paint(Paint.ANTI_ALIAS_FLAG)
        private val stamen: FloatArray
        private val stamenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
        private val corePaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val pollen: FloatArray // radius, angle, speed per grain
        private val pollenPoints: FloatArray
        private val pollenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            strokeCap = Paint.Cap.ROUND
            blendMode = if (scene.isLight) BlendMode.MULTIPLY else BlendMode.PLUS
        }

        init {
            val n = p.symmetry
            val layerCount = (3 + p.complexity * 4f + p.weight * 2f).roundToInt().coerceIn(3, 9)
            val roundness = 0.22f + 0.22f * p.weight
            val sharpness = 0.5f + 2.2f * (1f - p.weight)
            val fray = 0.06f * p.turbulence
            val frayFreq = n * rng.range(3f, 6f)
            val twist = rng.range(0.3f, 0.9f)

            layers = List(layerCount) { l ->
                val depth = l / layerCount.toFloat()
                val r = radius * (1f - depth * 0.82f)
                val petalsOnLayer = if (l % 2 == 1 && p.detail > 0.55f) n * 2 else n
                val offset = (l * PI.toFloat() / petalsOnLayer) * twist
                val petals = Path()
                for (s in 0..STEPS) {
                    val th = s / STEPS.toFloat() * 2f * PI.toFloat()
                    val local = (th * petalsOnLayer / (2f * PI.toFloat())) % 1f
                    val mirrored = 1f - abs(2f * local - 1f)
                    val lobe = abs(cos(th * petalsOnLayer / 2f)).pow(sharpness)
                    val sculpt = 1f + 0.18f * (p.spectrumAt(mirrored) - 0.5f)
                    val rr = r * (roundness + (1f - roundness) * lobe) * sculpt * (1f + fray * sin(th * frayFreq))
                    val x = cx + rr * cos(th + offset)
                    val y = cy + rr * sin(th + offset)
                    if (s == 0) petals.moveTo(x, y) else petals.lineTo(x, y)
                }
                petals.close()

                val veins = Path()
                val veinsPerPetal = 1 + (p.detail * 2).toInt()
                val veinCount = petalsOnLayer * veinsPerPetal
                for (v in 0 until veinCount) {
                    val a = v * 2f * PI.toFloat() / veinCount + offset
                    val isMidrib = v % veinsPerPetal == 0
                    val len = r * if (isMidrib) 0.9f else 0.72f
                    veins.moveTo(cx + cos(a) * r * 0.12f, cy + sin(a) * r * 0.12f)
                    veins.quadTo(
                        cx + cos(a + 0.12f) * len * 0.55f, cy + sin(a + 0.12f) * len * 0.55f,
                        cx + cos(a) * len, cy + sin(a) * len,
                    )
                }

                val tc = 0.15f + depth * 0.8f
                val alpha = (0.55f + 0.4f * p.intensity) * (0.85f + 0.15f * depth)
                val fill = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG).apply {
                    shader = RadialGradient(
                        cx, cy, r,
                        intArrayOf(
                            ColorUtil.withAlpha(scene.color(tc + 0.35f), alpha),
                            ColorUtil.withAlpha(scene.color(tc), alpha * 0.92f),
                            ColorUtil.withAlpha(scene.color(tc - 0.2f), alpha * (0.35f + 0.4f * p.contrast)),
                        ),
                        floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP,
                    )
                }
                val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = ColorUtil.withAlpha(Backgrounds.deep(scene.background), if (scene.isLight) 0.18f else 0.5f)
                }
                val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = max(1f, radius * 0.004f)
                    color = ColorUtil.withAlpha(scene.color(tc + 0.45f), 0.35f + 0.35f * p.contrast)
                }
                val veinPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = max(0.8f, radius * 0.0025f)
                    strokeCap = Paint.Cap.ROUND
                    color = ColorUtil.withAlpha(scene.color(tc + 0.5f), 0.18f + 0.2f * p.detail)
                }
                Layer(
                    petals, veins, fill, shadow, outline, veinPaint,
                    rotation = 0f,
                    spin = (1.5f + 6f * p.motion) * if (l % 2 == 0) 1f else -1f,
                    breathPhase = l * 0.7f,
                )
            }

            halo.shader = RadialGradient(
                cx, cy, radius * 1.6f,
                intArrayOf(
                    ColorUtil.withAlpha(scene.color(0.4f), if (scene.isLight) 0.18f else 0.32f * (0.5f + p.intensity)),
                    ColorUtil.withAlpha(scene.color(0.6f), 0.08f),
                    ColorUtil.withAlpha(scene.color(0.6f), 0f),
                ),
                floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP,
            )

            val seeds = (60 + 260 * p.complexity).toInt()
            val coreRadius = radius * (0.14f + 0.05f * p.weight)
            stamen = FloatArray(seeds * 2)
            val golden = (PI * (3 - sqrt(5.0))).toFloat()
            for (i in 0 until seeds) {
                val rr = coreRadius * sqrt(i / seeds.toFloat())
                val a = i * golden
                stamen[i * 2] = cx + rr * cos(a)
                stamen[i * 2 + 1] = cy + rr * sin(a)
            }
            stamenPaint.strokeWidth = max(1.5f, coreRadius * 0.07f)
            stamenPaint.color = scene.color(0f)
            corePaint.shader = RadialGradient(
                cx, cy, coreRadius * 1.4f,
                intArrayOf(ColorUtil.withAlpha(scene.color(0.9f), 0.95f), ColorUtil.withAlpha(scene.color(0.6f), 0.6f), ColorUtil.withAlpha(scene.color(0.6f), 0f)),
                floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP,
            )

            val grains = (p.detail * 180f + 40f * p.energy).toInt()
            pollen = FloatArray(grains * 3)
            for (g in 0 until grains) {
                pollen[g * 3] = radius * rng.range(1.02f, 1.45f)
                pollen[g * 3 + 1] = rng.range(0f, 6.283f)
                pollen[g * 3 + 2] = rng.range(0.02f, 0.12f) * (0.4f + p.motion) * if (rng.nextFloat() > 0.5f) 1f else -1f
            }
            pollenPoints = FloatArray(grains * 2)
            pollenPaint.strokeWidth = max(1.5f, radius * 0.008f)
            pollenPaint.color = scene.color(0.1f)
        }

        override fun drawStatic(canvas: Canvas) {
            canvas.drawCircle(cx, cy, radius * 1.6f, halo)
        }

        override fun drawDynamic(canvas: Canvas, timeSec: Float) {
            val shadowOffset = radius * 0.02f
            for (layer in layers) {
                val breath = 1f + 0.025f * (0.5f + p.motion) * sin(timeSec * 1.1f + layer.breathPhase)
                canvas.save()
                canvas.rotate(layer.rotation + timeSec * layer.spin, cx, cy)
                canvas.scale(breath, breath, cx, cy)
                canvas.save()
                canvas.translate(shadowOffset * 0.4f, shadowOffset)
                canvas.drawPath(layer.petals, layer.shadow)
                canvas.restore()
                canvas.drawPath(layer.petals, layer.fill)
                canvas.drawPath(layer.veins, layer.veinPaint)
                canvas.drawPath(layer.petals, layer.outline)
                canvas.restore()
            }
            val pulse = 1f + 0.05f * sin(timeSec * 2.2f) * (0.4f + p.energy)
            canvas.save()
            canvas.scale(pulse, pulse, cx, cy)
            canvas.drawCircle(cx, cy, radius * 0.27f, corePaint)
            canvas.rotate(timeSec * 4f, cx, cy)
            canvas.drawPoints(stamen, stamenPaint)
            canvas.restore()

            val grains = pollen.size / 3
            for (g in 0 until grains) {
                val r = pollen[g * 3] * (1f + 0.02f * sin(timeSec + g))
                val a = pollen[g * 3 + 1] + timeSec * pollen[g * 3 + 2]
                pollenPoints[g * 2] = cx + r * cos(a)
                pollenPoints[g * 2 + 1] = cy + r * sin(a)
            }
            pollenPaint.alpha = (255 * (0.35f + 0.45f * p.intensity)).toInt()
            if (grains > 0) canvas.drawPoints(pollenPoints, 0, grains * 2, pollenPaint)
        }
    }
}

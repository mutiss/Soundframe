package com.mutissx.soundframe.core.graphics

import android.graphics.BlendMode
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * PARTICLES — a constellation flowing through a curl-like noise field. Trajectories are integrated
 * once in [prepare] (deterministic for a given seed), so animation only slides a bright head and a
 * short trail along each precomputed path; the threads behind them are drawn once into the static layer.
 * Onset-rich sounds create more vortices; louder sounds create denser fields.
 */
internal object ParticlesRenderer : ArtRenderer {
    override fun prepare(scene: ArtScene, width: Int, height: Int): PreparedArt = Prepared(scene, width, height)

    private const val STEPS = 40
    private const val TRAIL = 6
    private const val BUCKETS = 5

    private class Prepared(private val scene: ArtScene, override val width: Int, override val height: Int) : PreparedArt {
        private val p = scene.params
        private val rng = Rng(p.seed xor 0x9A27L)
        private val noise = PerlinNoise(p.seed)
        private val minDim = min(width, height).toFloat()
        private val count = (900 + 4100 * p.complexity * (0.45f + 0.55f * p.energy)).toInt().coerceIn(500, 5000)
        private val paths = FloatArray(count * STEPS * 2)
        private val bucketOf = IntArray(count)
        private val phase = FloatArray(count)
        private val speed = FloatArray(count)
        private val isStar = BooleanArray(count)
        private val threads = Array(BUCKETS) { FloatList(count * STEPS * 4 / BUCKETS + 16) }
        private val trailPath = Path()
        // Sized for the worst case of every particle landing in one colour bucket.
        private val headScratch = Array(BUCKETS) { FloatArray(count * 2 + 16) }
        private val headSize = IntArray(BUCKETS)
        private val starScratch = FloatArray(count * 2)
        private val bucketColors = IntArray(BUCKETS) { scene.color(it / (BUCKETS - 1f)) }

        private val threadPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = max(1f, minDim * 0.0011f)
            blendMode = scene.glowBlend
        }
        private val trailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            strokeWidth = max(1.2f, minDim * 0.0022f)
            blendMode = scene.glowBlend
        }
        private val headPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            strokeCap = Paint.Cap.ROUND
            blendMode = if (scene.isLight) BlendMode.MULTIPLY else BlendMode.SCREEN
        }

        init {
            integrate()
        }

        private fun integrate() {
            val attractors = max(2, p.repetition / 2)
            val ax = FloatArray(attractors)
            val ay = FloatArray(attractors)
            val strength = FloatArray(attractors)
            for (a in 0 until attractors) {
                ax[a] = width * rng.range(0.18f, 0.82f)
                ay[a] = height * rng.range(0.18f, 0.82f)
                strength[a] = (0.4f + 1.6f * p.fluxAt(a / attractors.toFloat())) * if (rng.nextFloat() > 0.5f) 1f else -1f
            }
            val freq = (1.2f + 2.6f * p.turbulence) / minDim
            val step = minDim * (0.006f + 0.008f * p.motion)
            val clusterShare = 0.25f + 0.55f * p.contrast
            val swirlRadius = minDim * (0.18f + 0.15f * p.spread)

            for (i in 0 until count) {
                var x: Float
                var y: Float
                if (rng.nextFloat() < clusterShare) {
                    val a = rng.nextInt(attractors)
                    x = ax[a] + rng.gaussian() * swirlRadius * 0.6f
                    y = ay[a] + rng.gaussian() * swirlRadius * 0.6f
                } else {
                    x = rng.range(-0.05f, 1.05f) * width
                    y = rng.range(-0.05f, 1.05f) * height
                }
                val base = i * STEPS * 2
                for (s in 0 until STEPS) {
                    paths[base + s * 2] = x
                    paths[base + s * 2 + 1] = y
                    var angle = noise.fbm(x * freq, y * freq, 3) * PI.toFloat() * (1.4f + 1.6f * p.turbulence)
                    var vx = cos(angle)
                    var vy = sin(angle)
                    for (a in 0 until attractors) {
                        val dx = x - ax[a]
                        val dy = y - ay[a]
                        val d2 = dx * dx + dy * dy
                        val falloff = exp(-d2 / (swirlRadius * swirlRadius)) * strength[a]
                        vx += -dy / (sqrt(d2) + 1f) * falloff
                        vy += dx / (sqrt(d2) + 1f) * falloff
                    }
                    angle = atan2(vy, vx)
                    x += cos(angle) * step
                    y += sin(angle) * step
                }
                val hue = ((y / height) * 0.6f + noise.noise(x * freq * 0.5f, 7.3f) * 0.4f + 0.2f).coerceIn(0f, 0.999f)
                bucketOf[i] = (hue * BUCKETS).toInt()
                phase[i] = rng.nextFloat()
                speed[i] = rng.range(0.05f, 0.14f) * (0.5f + p.motion)
                isStar[i] = rng.nextFloat() < 0.012f + 0.02f * p.detail

                val list = threads[bucketOf[i]]
                for (s in 0 until STEPS - 1) {
                    list.add4(paths[base + s * 2], paths[base + s * 2 + 1], paths[base + s * 2 + 2], paths[base + s * 2 + 3])
                }
            }
        }

        override fun drawStatic(canvas: Canvas) {
            val alpha = (0.035f + 0.07f * p.intensity) * if (scene.isLight) 1.4f else 1f
            for (b in 0 until BUCKETS) {
                val list = threads[b]
                if (list.size == 0) continue
                threadPaint.color = ColorUtil.withAlpha(bucketColors[b], alpha)
                canvas.drawLines(list.data, 0, list.size, threadPaint)
            }
        }

        override fun drawDynamic(canvas: Canvas, timeSec: Float) {
            headSize.fill(0)
            var stars = 0
            val trailAlpha = 0.18f + 0.3f * p.intensity
            for (i in 0 until count) {
                val b = bucketOf[i]
                val pos = ((phase[i] + timeSec * speed[i]) % 1f) * (STEPS - 1)
                val head = pos.toInt()
                val base = i * STEPS * 2
                val from = max(0, head - TRAIL)
                if (head > from) {
                    // One polyline per particle. Anti-aliased wide drawLines falls back to a separate
                    // path draw per segment on the GPU backend, which dominated frame time; one draw
                    // per particle still lets overlapping trails add up under the glow blend.
                    trailPath.rewind()
                    trailPath.moveTo(paths[base + from * 2], paths[base + from * 2 + 1])
                    for (s in from + 1..head) trailPath.lineTo(paths[base + s * 2], paths[base + s * 2 + 1])
                    trailPaint.color = ColorUtil.withAlpha(bucketColors[b], trailAlpha)
                    canvas.drawPath(trailPath, trailPaint)
                }
                val hx = paths[base + head * 2]
                val hy = paths[base + head * 2 + 1]
                val heads = headScratch[b]
                heads[headSize[b]] = hx
                heads[headSize[b] + 1] = hy
                headSize[b] += 2
                if (isStar[i]) {
                    starScratch[stars] = hx
                    starScratch[stars + 1] = hy
                    stars += 2
                }
            }
            for (b in 0 until BUCKETS) {
                if (headSize[b] > 0) {
                    headPaint.strokeWidth = max(1.6f, minDim * (0.0028f + 0.002f * p.detail))
                    headPaint.color = ColorUtil.withAlpha(ColorUtil.lerp(bucketColors[b], 0xFFFFFFFF.toInt(), 0.2f), 0.55f + 0.2f * p.intensity)
                    canvas.drawPoints(headScratch[b], 0, headSize[b], headPaint)
                }
            }
            if (stars > 0) {
                val twinkle = 0.6f + 0.4f * sin(timeSec * 2.3f)
                headPaint.color = ColorUtil.withAlpha(scene.color(0f), 0.18f * twinkle)
                headPaint.strokeWidth = minDim * 0.022f
                canvas.drawPoints(starScratch, 0, stars, headPaint)
                headPaint.color = ColorUtil.withAlpha(0xFFFFFFFF.toInt(), 0.9f)
                headPaint.strokeWidth = minDim * 0.0055f
                canvas.drawPoints(starScratch, 0, stars, headPaint)
            }
        }
    }
}

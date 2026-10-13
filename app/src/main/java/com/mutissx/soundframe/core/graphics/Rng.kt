package com.mutissx.soundframe.core.graphics

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * SplitMix64. Chosen over kotlin.random because its output sequence is fixed by definition, which
 * keeps artworks reproducible across library and OS versions.
 */
class Rng(seed: Long) {
    private var state = seed

    fun nextLong(): Long {
        state += -0x61c8864680b583ebL
        var z = state
        z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L
        z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
        return z xor (z ushr 31)
    }

    /** Uniform in [0, 1). */
    fun nextFloat(): Float = (nextLong() ushr 40).toFloat() / (1 shl 24).toFloat()

    fun range(from: Float, to: Float): Float = from + (to - from) * nextFloat()

    fun nextInt(bound: Int): Int = ((nextLong() ushr 1) % bound).toInt()

    fun gaussian(): Float {
        val u1 = nextFloat().coerceAtLeast(1e-7f)
        val u2 = nextFloat()
        return (sqrt(-2.0 * ln(u1.toDouble())) * cos(2.0 * PI * u2)).toFloat()
    }

    fun fork(salt: Long): Rng = Rng(nextLong() xor salt)
}

/** Seeded 2D Perlin gradient noise with fractal Brownian motion. Output roughly in -1..1. */
class PerlinNoise(seed: Long) {
    private val perm = IntArray(512)

    init {
        val p = IntArray(256) { it }
        val rng = Rng(seed)
        for (i in 255 downTo 1) {
            val j = rng.nextInt(i + 1)
            val t = p[i]; p[i] = p[j]; p[j] = t
        }
        for (i in 0 until 512) perm[i] = p[i and 255]
    }

    fun noise(x: Float, y: Float): Float {
        val xf = floor(x)
        val yf = floor(y)
        val xi = xf.toInt() and 255
        val yi = yf.toInt() and 255
        val dx = x - xf
        val dy = y - yf
        val u = fade(dx)
        val v = fade(dy)
        val aa = perm[perm[xi] + yi]
        val ab = perm[perm[xi] + yi + 1]
        val ba = perm[perm[xi + 1] + yi]
        val bb = perm[perm[xi + 1] + yi + 1]
        val x1 = lerp(grad(aa, dx, dy), grad(ba, dx - 1, dy), u)
        val x2 = lerp(grad(ab, dx, dy - 1), grad(bb, dx - 1, dy - 1), u)
        return lerp(x1, x2, v) * 1.41f
    }

    fun fbm(x: Float, y: Float, octaves: Int = 4, lacunarity: Float = 2f, gain: Float = 0.5f): Float {
        var sum = 0f
        var amp = 0.5f
        var fx = x
        var fy = y
        var norm = 0f
        repeat(octaves) {
            sum += amp * noise(fx, fy)
            norm += amp
            fx *= lacunarity
            fy *= lacunarity
            amp *= gain
        }
        return sum / norm
    }

    private fun fade(t: Float) = t * t * t * (t * (t * 6 - 15) + 10)
    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
    private fun grad(hash: Int, x: Float, y: Float): Float = when (hash and 7) {
        0 -> x + y
        1 -> -x + y
        2 -> x - y
        3 -> -x - y
        4 -> x
        5 -> -x
        6 -> y
        else -> -y
    }
}

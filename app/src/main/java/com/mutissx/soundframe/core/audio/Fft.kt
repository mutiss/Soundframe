package com.mutissx.soundframe.core.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Iterative in-place radix-2 Cooley–Tukey FFT with precomputed twiddles and bit-reversal table.
 * One instance per thread: the scratch buffers are reused across calls to avoid allocation.
 */
class Fft(val size: Int) {
    init {
        require(size >= 2 && size and (size - 1) == 0) { "FFT size must be a power of two" }
    }

    private val cosTable = FloatArray(size / 2) { cos(2.0 * PI * it / size).toFloat() }
    private val sinTable = FloatArray(size / 2) { -sin(2.0 * PI * it / size).toFloat() }
    private val bitReverse = IntArray(size).also { table ->
        val bits = Integer.numberOfTrailingZeros(size)
        for (i in 0 until size) table[i] = Integer.reverse(i) ushr (32 - bits)
    }
    private val re = FloatArray(size)
    private val im = FloatArray(size)

    /** Complex FFT of ([real], [imag]) in place. */
    fun transform(real: FloatArray, imag: FloatArray) {
        require(real.size == size && imag.size == size)
        for (i in 0 until size) {
            val j = bitReverse[i]
            if (j > i) {
                val tr = real[i]; real[i] = real[j]; real[j] = tr
                val ti = imag[i]; imag[i] = imag[j]; imag[j] = ti
            }
        }
        var half = 1
        while (half < size) {
            val step = size / (half * 2)
            var start = 0
            while (start < size) {
                var k = 0
                for (j in start until start + half) {
                    val wr = cosTable[k]
                    val wi = sinTable[k]
                    val l = j + half
                    val xr = real[l] * wr - imag[l] * wi
                    val xi = real[l] * wi + imag[l] * wr
                    real[l] = real[j] - xr
                    imag[l] = imag[j] - xi
                    real[j] += xr
                    imag[j] += xi
                    k += step
                }
                start += half * 2
            }
            half *= 2
        }
    }

    /**
     * Magnitude spectrum of a real frame. Writes `size / 2 + 1` bins into [out].
     * The input is multiplied by [window] when provided.
     */
    fun magnitudes(frame: FloatArray, window: FloatArray?, out: FloatArray) {
        require(out.size >= size / 2 + 1)
        for (i in 0 until size) {
            re[i] = if (i < frame.size) frame[i] * (window?.get(i) ?: 1f) else 0f
            im[i] = 0f
        }
        transform(re, im)
        for (k in 0..size / 2) out[k] = sqrt(re[k] * re[k] + im[k] * im[k])
    }
}

object Windows {
    /** Periodic Hann window, the standard choice for STFT analysis with 75% overlap. */
    fun hann(size: Int): FloatArray = FloatArray(size) { (0.5 - 0.5 * cos(2.0 * PI * it / size)).toFloat() }
}

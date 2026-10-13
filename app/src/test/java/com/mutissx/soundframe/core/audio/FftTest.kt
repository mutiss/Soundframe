package com.mutissx.soundframe.core.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class FftTest {

    @Test
    fun `matches a naive DFT`() {
        val n = 64
        val signal = FloatArray(n) { (sin(2 * PI * 5 * it / n) + 0.3 * cos(2 * PI * 11 * it / n) + 0.1 * it / n).toFloat() }
        val re = signal.copyOf()
        val im = FloatArray(n)
        Fft(n).transform(re, im)
        for (k in 0 until n) {
            var dr = 0.0
            var di = 0.0
            for (t in 0 until n) {
                dr += signal[t] * cos(2 * PI * k * t / n)
                di -= signal[t] * sin(2 * PI * k * t / n)
            }
            assertEquals("re[$k]", dr, re[k].toDouble(), 1e-3)
            assertEquals("im[$k]", di, im[k].toDouble(), 1e-3)
        }
    }

    @Test
    fun `pure tone peaks at its bin`() {
        val n = 1024
        val bin = 37
        val frame = FloatArray(n) { sin(2 * PI * bin * it / n).toFloat() }
        val mags = FloatArray(n / 2 + 1)
        Fft(n).magnitudes(frame, null, mags)
        val peak = mags.indices.maxBy { mags[it] }
        assertEquals(bin, peak)
        // Unwindowed full-scale sine: |X[k]| = N/2.
        assertEquals(n / 2f, mags[bin], 0.5f)
    }

    @Test
    fun `preserves energy (Parseval)`() {
        val n = 256
        val signal = FloatArray(n) { (sin(it * 0.37) * cos(it * 0.11)).toFloat() }
        val re = signal.copyOf()
        val im = FloatArray(n)
        Fft(n).transform(re, im)
        val timeEnergy = signal.sumOf { (it * it).toDouble() }
        val freqEnergy = (0 until n).sumOf { (re[it] * re[it] + im[it] * im[it]).toDouble() } / n
        assertEquals(timeEnergy, freqEnergy, 1e-3)
    }

    @Test
    fun `hann window is zero at the edges and one in the middle`() {
        val w = Windows.hann(512)
        assertEquals(0f, w[0], 1e-6f)
        assertEquals(1f, w[256], 1e-6f)
        assertEquals(0.5f, w.average().toFloat(), 1e-3f)
        assertEquals(sqrt(3f / 8f), sqrt(w.map { it * it }.average().toFloat()), 1e-3f)
    }

    @Test
    fun `rejects non power of two sizes`() {
        assertThrows(IllegalArgumentException::class.java) { Fft(100) }
    }
}

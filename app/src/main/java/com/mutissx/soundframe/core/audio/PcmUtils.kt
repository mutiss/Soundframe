package com.mutissx.soundframe.core.audio

import kotlin.math.log10
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

object PcmUtils {

    /** Averages interleaved channels into mono. */
    fun downmix(interleaved: ShortArray, length: Int, channels: Int): ShortArray {
        if (channels <= 1) return interleaved.copyOf(length)
        val frames = length / channels
        return ShortArray(frames) { f ->
            var acc = 0
            for (c in 0 until channels) acc += interleaved[f * channels + c]
            (acc / channels).toShort()
        }
    }

    /**
     * Linear-interpolation resampler. Adequate for analysis and preview playback; when
     * downsampling it applies a short box pre-filter to limit aliasing.
     */
    fun resample(input: ShortArray, fromRate: Int, toRate: Int): ShortArray {
        if (fromRate == toRate || input.isEmpty()) return input
        val ratio = fromRate.toDouble() / toRate
        val source = if (ratio > 1.0) boxFilter(input, ratio.roundToInt().coerceAtLeast(1)) else input
        val outLength = (input.size / ratio).toInt()
        return ShortArray(outLength) { i ->
            val x = i * ratio
            val lo = x.toInt().coerceAtMost(source.size - 1)
            val hi = (lo + 1).coerceAtMost(source.size - 1)
            val f = x - lo
            (source[lo] * (1 - f) + source[hi] * f).roundToInt().coerceIn(-32768, 32767).toShort()
        }
    }

    private fun boxFilter(input: ShortArray, width: Int): ShortArray {
        if (width <= 1) return input
        val out = ShortArray(input.size)
        var acc = 0L
        for (i in input.indices) {
            acc += input[i]
            if (i >= width) acc -= input[i - width]
            out[i] = (acc / minOf(i + 1, width)).toShort()
        }
        return out
    }

    /** Peak envelope in [points] buckets, normalised to the loudest bucket. Used for waveform UI. */
    fun peakEnvelope(samples: ShortArray, points: Int): List<Float> {
        if (samples.isEmpty() || points <= 0) return List(points.coerceAtLeast(0)) { 0f }
        val peaks = FloatArray(points) { i ->
            val from = i * samples.size / points
            val to = max((i + 1) * samples.size / points, from + 1).coerceAtMost(samples.size)
            var peak = 0
            for (k in from until to) {
                val v = kotlin.math.abs(samples[k].toInt())
                if (v > peak) peak = v
            }
            peak / 32768f
        }
        val loudest = peaks.max()
        return if (loudest <= 0f) peaks.map { 0f } else peaks.map { it / loudest }
    }

    /**
     * Perceptual 0..1 level for a buffer: RMS in dBFS mapped from a -60 dB floor.
     * Allocation-free so it can run inside the recording loop.
     */
    fun level(buffer: ShortArray, offset: Int, length: Int): Float {
        if (length <= 0) return 0f
        var sum = 0.0
        for (i in offset until offset + length) {
            val v = buffer[i] / 32768.0
            sum += v * v
        }
        val rms = sqrt(sum / length)
        val db = 20 * log10(max(rms, 1e-6))
        return ((db + 60.0) / 60.0).toFloat().coerceIn(0f, 1f)
    }
}

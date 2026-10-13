package com.mutissx.soundframe.core.audio

import com.mutissx.soundframe.domain.model.AnalysisQuality
import com.mutissx.soundframe.domain.model.AudioFeatures
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Short-time spectral analysis of a mono clip.
 *
 * Frames of [FRAME_SIZE] samples (46 ms at 44.1 kHz) with a Hann window and [HOP_SIZE] hop (75%
 * overlap). Spectral statistics are energy-weighted over non-silent frames so that pauses don't
 * drag the centroid towards zero. Pure Kotlin so it runs (and is tested) on the JVM.
 */
class FeatureExtractor {

    private val fft = Fft(FRAME_SIZE)
    private val window = Windows.hann(FRAME_SIZE)
    // Hann window coherent gain is 0.5; normalise so a full-scale sine reads ~1.0.
    private val magnitudeScale = 2f / (FRAME_SIZE * 0.5f)

    fun extract(input: FloatArray, sampleRate: Int, checkCancelled: () -> Unit = {}): AudioFeatures {
        require(sampleRate > 0)
        val duration = input.size.toFloat() / sampleRate
        if (input.isEmpty()) return silentFeatures(0f, sampleRate, AnalysisQuality.TOO_SHORT)

        // Remove DC offset (cheap microphones often have one) without touching the input array.
        val mean = input.average().toFloat()
        val x = FloatArray(max(input.size, FRAME_SIZE)) { if (it < input.size) input[it] - mean else 0f }
        val n = input.size

        var peak = 0f
        var sumSquares = 0.0
        var clipped = 0
        var crossings = 0
        for (i in 0 until n) {
            val v = x[i]
            val a = abs(v)
            if (a > peak) peak = a
            sumSquares += v * v
            if (abs(input[i]) >= CLIP_LEVEL) clipped++
            if (i > 0 && (x[i - 1] >= 0f) != (v >= 0f)) crossings++
        }
        val rms = sqrt(sumSquares / n).toFloat()
        if (peak < ABSOLUTE_SILENCE) {
            val q = if (duration < MIN_MEANINGFUL_SEC) AnalysisQuality.TOO_SHORT else AnalysisQuality.MOSTLY_SILENT
            return silentFeatures(duration, sampleRate, q)
        }

        val frameCount = 1 + (x.size - FRAME_SIZE) / HOP_SIZE
        val bins = FRAME_SIZE / 2 + 1
        val binHz = sampleRate.toFloat() / FRAME_SIZE
        val frame = FloatArray(FRAME_SIZE)
        val mags = FloatArray(bins)
        val prevMags = FloatArray(bins)

        val frameRms = FloatArray(frameCount)
        val frameCentroid = FloatArray(frameCount)
        val frameBandwidth = FloatArray(frameCount)
        val frameFlatness = FloatArray(frameCount)
        val framePower = FloatArray(frameCount)
        val flux = FloatArray(frameCount)
        var lowE = 0.0
        var midE = 0.0
        var highE = 0.0

        val bandEdges = logBandEdges(sampleRate, bins, binHz)
        val bandAccum = DoubleArray(AudioFeatures.SPECTRUM_BANDS)
        var bandFrames = 0

        for (f in 0 until frameCount) {
            checkCancelled()
            val offset = f * HOP_SIZE
            var e = 0.0
            for (i in 0 until FRAME_SIZE) {
                val v = x[offset + i]
                frame[i] = v
                e += v * v
            }
            frameRms[f] = sqrt(e / FRAME_SIZE).toFloat()
            fft.magnitudes(frame, window, mags)

            var power = 0.0
            var weighted = 0.0
            var logSum = 0.0
            var fluxSum = 0f
            for (k in 1 until bins) {
                val m = mags[k] * magnitudeScale
                mags[k] = m
                val p = m.toDouble() * m
                power += p
                weighted += p * k * binHz
                logSum += ln(p + 1e-12)
                // Log-compressed, half-wave rectified flux is robust to overall loudness.
                val d = ln(1f + 100f * m) - ln(1f + 100f * prevMags[k])
                if (d > 0f) fluxSum += d
                prevMags[k] = m
            }
            flux[f] = if (f == 0) 0f else fluxSum / bins
            framePower[f] = power.toFloat()
            if (power > 1e-12) {
                val centroid = weighted / power
                var spreadAcc = 0.0
                for (k in 1 until bins) {
                    val p = mags[k].toDouble() * mags[k]
                    val d = k * binHz - centroid
                    spreadAcc += p * d * d
                    val hz = k * binHz
                    when {
                        hz < LOW_CUTOFF_HZ -> lowE += p
                        hz < HIGH_CUTOFF_HZ -> midE += p
                        else -> highE += p
                    }
                }
                frameCentroid[f] = centroid.toFloat()
                frameBandwidth[f] = sqrt(spreadAcc / power).toFloat()
                val geometric = exp(logSum / (bins - 1))
                val arithmetic = power / (bins - 1)
                frameFlatness[f] = (geometric / arithmetic).toFloat().coerceIn(0f, 1f)
                for (b in 0 until AudioFeatures.SPECTRUM_BANDS) {
                    var s = 0.0
                    val from = bandEdges[b]
                    val to = max(bandEdges[b + 1], from + 1)
                    for (k in from until min(to, bins)) s += mags[k]
                    bandAccum[b] += s / (to - from)
                }
                bandFrames++
            }
        }

        // Silence: below an absolute floor, or 45 dB below the loudest frame.
        val frameDb = FloatArray(frameCount) { toDb(frameRms[it]) }
        val loudest = frameDb.max()
        val silenceThreshold = max(SILENCE_DB, loudest - RELATIVE_SILENCE_DB)
        val active = BooleanArray(frameCount) { frameDb[it] > silenceThreshold }
        val activeCount = active.count { it }
        val silenceRatio = 1f - activeCount.toFloat() / frameCount

        var wSum = 0.0
        var centroidAcc = 0.0
        var bandwidthAcc = 0.0
        var flatnessAcc = 0.0
        for (f in 0 until frameCount) {
            if (!active[f]) continue
            val w = framePower[f].toDouble()
            wSum += w
            centroidAcc += frameCentroid[f] * w
            bandwidthAcc += frameBandwidth[f] * w
            flatnessAcc += frameFlatness[f] * w
        }
        val centroid = if (wSum > 0) (centroidAcc / wSum).toFloat() else 0f
        val bandwidth = if (wSum > 0) (bandwidthAcc / wSum).toFloat() else 0f
        val flatness = if (wSum > 0) (flatnessAcc / wSum).toFloat() else 0f
        val totalBand = lowE + midE + highE

        val activeDb = frameDb.filterIndexed { i, _ -> active[i] }.sorted()
        val dynamicRange = if (activeDb.size >= 2) percentile(activeDb, 0.95f) - percentile(activeDb, 0.10f) else 0f

        val onsets = OnsetDetector.detect(flux, active, HOP_SIZE.toFloat() / sampleRate)
        val regularity = OnsetDetector.regularity(onsets, HOP_SIZE.toFloat() / sampleRate)

        val profile = DoubleArray(AudioFeatures.SPECTRUM_BANDS) {
            if (bandFrames == 0) 0.0 else toDb((bandAccum[it] / bandFrames).toFloat()).toDouble()
        }
        // Map band levels from a 60 dB window under the strongest band onto 0..1.
        val profileMax = profile.max()
        val spectrumProfile = profile.map { ((it - (profileMax - 60.0)) / 60.0).toFloat().coerceIn(0f, 1f) }

        val clippingRatio = clipped.toFloat() / n
        val quality = when {
            duration < MIN_MEANINGFUL_SEC -> AnalysisQuality.TOO_SHORT
            silenceRatio > 0.95f -> AnalysisQuality.MOSTLY_SILENT
            clippingRatio > 0.01f -> AnalysisQuality.CLIPPED
            else -> AnalysisQuality.OK
        }

        return AudioFeatures(
            durationSec = duration,
            sampleRate = sampleRate,
            rms = rms,
            peakAmplitude = peak.coerceAtMost(1f),
            crestFactorDb = if (rms > 0f) 20f * log10(peak / rms) else 0f,
            dynamicRangeDb = dynamicRange,
            spectralCentroid = centroid,
            spectralBandwidth = bandwidth,
            spectralFlatness = flatness,
            lowFrequencyEnergy = if (totalBand > 0) (lowE / totalBand).toFloat() else 0f,
            midFrequencyEnergy = if (totalBand > 0) (midE / totalBand).toFloat() else 0f,
            highFrequencyEnergy = if (totalBand > 0) (highE / totalBand).toFloat() else 0f,
            zeroCrossingRate = if (n > 1) crossings.toFloat() / (n - 1) else 0f,
            transientDensity = if (duration > 0f) onsets.size / duration else 0f,
            onsetCount = onsets.size,
            silenceRatio = silenceRatio,
            clippingRatio = clippingRatio,
            rhythmicRegularity = regularity,
            energyEnvelope = normalize(resample(frameRms, AudioFeatures.ENVELOPE_POINTS)),
            spectralFlux = normalize(resample(flux, AudioFeatures.ENVELOPE_POINTS)),
            spectrumProfile = spectrumProfile,
            quality = quality,
        )
    }

    private fun silentFeatures(duration: Float, sampleRate: Int, quality: AnalysisQuality) = AudioFeatures(
        durationSec = duration,
        sampleRate = sampleRate,
        rms = 0f,
        peakAmplitude = 0f,
        crestFactorDb = 0f,
        dynamicRangeDb = 0f,
        spectralCentroid = 0f,
        spectralBandwidth = 0f,
        spectralFlatness = 0f,
        lowFrequencyEnergy = 0f,
        midFrequencyEnergy = 0f,
        highFrequencyEnergy = 0f,
        zeroCrossingRate = 0f,
        transientDensity = 0f,
        onsetCount = 0,
        silenceRatio = 1f,
        clippingRatio = 0f,
        rhythmicRegularity = null,
        energyEnvelope = List(AudioFeatures.ENVELOPE_POINTS) { 0f },
        spectralFlux = List(AudioFeatures.ENVELOPE_POINTS) { 0f },
        spectrumProfile = List(AudioFeatures.SPECTRUM_BANDS) { 0f },
        quality = quality,
    )

    private fun logBandEdges(sampleRate: Int, bins: Int, binHz: Float): IntArray {
        val minHz = 40f
        val maxHz = min(16_000f, sampleRate / 2f)
        val bands = AudioFeatures.SPECTRUM_BANDS
        return IntArray(bands + 1) { i ->
            val hz = minHz * (maxHz / minHz).pow(i.toFloat() / bands)
            (hz / binHz).toInt().coerceIn(1, bins)
        }
    }

    companion object {
        const val FRAME_SIZE = 2048
        const val HOP_SIZE = 512
        const val LOW_CUTOFF_HZ = 250f
        const val HIGH_CUTOFF_HZ = 4000f
        // Calibrated on a real quiet room (Pixel 9 Pro XL, ~-55 dBFS): ambience is sound, not silence.
        const val SILENCE_DB = -62f
        const val RELATIVE_SILENCE_DB = 45f
        const val CLIP_LEVEL = 0.985f
        const val ABSOLUTE_SILENCE = 1e-4f
        const val MIN_MEANINGFUL_SEC = 0.25f

        fun toDb(amplitude: Float): Float = 20f * log10(max(amplitude, 1e-6f))

        fun percentile(sorted: List<Float>, p: Float): Float {
            val idx = (p * (sorted.size - 1)).coerceIn(0f, (sorted.size - 1).toFloat())
            val lo = idx.toInt()
            val hi = min(lo + 1, sorted.size - 1)
            val f = idx - lo
            return sorted[lo] * (1 - f) + sorted[hi] * f
        }

        /** Area-averaging resample to [points] values (or linear interpolation when upsampling). */
        fun resample(series: FloatArray, points: Int): FloatArray {
            if (series.isEmpty()) return FloatArray(points)
            if (series.size <= points) {
                return FloatArray(points) { i ->
                    if (series.size == 1) series[0] else {
                        val x = i.toFloat() / (points - 1) * (series.size - 1)
                        val lo = x.toInt().coerceAtMost(series.size - 2)
                        val f = x - lo
                        series[lo] * (1 - f) + series[lo + 1] * f
                    }
                }
            }
            return FloatArray(points) { i ->
                val from = i * series.size / points
                val to = max((i + 1) * series.size / points, from + 1)
                var s = 0f
                for (k in from until to) s += series[k]
                s / (to - from)
            }
        }

        fun normalize(series: FloatArray): List<Float> {
            val m = series.maxOrNull() ?: 0f
            return if (m <= 1e-9f) List(series.size) { 0f } else series.map { (it / m).coerceIn(0f, 1f) }
        }
    }
}

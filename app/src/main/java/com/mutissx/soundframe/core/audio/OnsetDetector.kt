package com.mutissx.soundframe.core.audio

import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** Spectral-flux onset picking with an adaptive median threshold. */
object OnsetDetector {
    private const val MEDIAN_RADIUS = 8
    private const val PEAK_RADIUS = 3
    private const val MIN_GAP_SEC = 0.06f
    private const val THRESHOLD_MULTIPLIER = 1.5f
    /**
     * Absolute floor for the per-bin log-flux. Steady tones measure ~1e-5 (numerical noise) while
     * real onsets measure 0.03–0.5, so purely relative thresholds would find "onsets" in silence.
     */
    private const val MIN_ONSET_FLUX = 0.01f

    /** @return frame indices of detected onsets. */
    fun detect(flux: FloatArray, active: BooleanArray, secondsPerFrame: Float): List<Int> {
        if (flux.size < 3) return emptyList()
        val globalMean = flux.average().toFloat()
        val globalMax = flux.max()
        if (globalMax <= 1e-6f) return emptyList()
        val minGapFrames = max(1, (MIN_GAP_SEC / secondsPerFrame).toInt())
        val window = FloatArray(2 * MEDIAN_RADIUS + 1)
        val onsets = ArrayList<Int>()
        var last = -minGapFrames
        for (i in 1 until flux.size - 1) {
            if (!active[i]) continue
            val v = flux[i]
            val from = max(0, i - MEDIAN_RADIUS)
            val to = min(flux.size - 1, i + MEDIAN_RADIUS)
            val count = to - from + 1
            for (k in 0 until count) window[k] = flux[from + k]
            java.util.Arrays.sort(window, 0, count)
            val median = window[count / 2]
            val threshold = max(MIN_ONSET_FLUX, median * THRESHOLD_MULTIPLIER + globalMean * 0.1f + globalMax * 0.05f)
            if (v <= threshold) continue
            var isPeak = true
            for (k in max(0, i - PEAK_RADIUS)..min(flux.size - 1, i + PEAK_RADIUS)) {
                if (k != i && flux[k] > v) { isPeak = false; break }
            }
            if (isPeak && i - last >= minGapFrames) {
                onsets.add(i)
                last = i
            }
        }
        return onsets
    }

    /**
     * 1 − coefficient of variation of inter-onset intervals. Only reported with at least four
     * onsets; anything less is not evidence of rhythm.
     */
    fun regularity(onsets: List<Int>, secondsPerFrame: Float): Float? {
        if (onsets.size < 4) return null
        val intervals = onsets.zipWithNext { a, b -> (b - a) * secondsPerFrame }
        val mean = intervals.average().toFloat()
        if (mean <= 0f) return null
        val variance = intervals.sumOf { ((it - mean) * (it - mean)).toDouble() }.toFloat() / intervals.size
        return (1f - sqrt(variance) / mean).coerceIn(0f, 1f)
    }
}

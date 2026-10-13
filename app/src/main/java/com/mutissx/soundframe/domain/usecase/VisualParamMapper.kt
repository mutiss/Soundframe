package com.mutissx.soundframe.domain.usecase

import com.mutissx.soundframe.domain.model.ArtworkSettings
import com.mutissx.soundframe.domain.model.AudioFeatures
import com.mutissx.soundframe.domain.model.VisualParams
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Deterministic audio → visual mapping. Same features + settings + [ENGINE_VERSION] always yield
 * identical [VisualParams]. Curves are tuned so typical real-world sounds land in the expressive
 * middle of each range instead of saturating.
 */
object VisualParamMapper {

    /** Bump whenever a change would alter the look of previously saved artworks. */
    const val ENGINE_VERSION = 1

    fun map(features: AudioFeatures, settings: ArtworkSettings): VisualParams {
        val rmsDb = 20f * kotlin.math.log10(max(features.rms, 1e-6f))
        // -66 dBFS (barely audible) .. -6 dBFS (very loud); quiet sounds still register.
        val energy = smooth((rmsDb + 66f) / 60f)

        val weight = smooth(features.lowFrequencyEnergy / 0.55f)
        val detail = (smooth(features.highFrequencyEnergy / 0.3f) * 0.7f +
            smooth(features.zeroCrossingRate / 0.25f) * 0.3f).coerceIn(0f, 1f)
        val brightness = if (features.spectralCentroid <= 0f) 0.35f else
            (log2(features.spectralCentroid / 150f) / log2(9000f / 150f)).coerceIn(0f, 1f)
        val spread = (features.spectralBandwidth / 3500f).coerceIn(0f, 1f)
        val contrast = smooth(features.dynamicRangeDb / 36f)
        val turbulence = smooth(features.spectralFlatness / 0.35f)
        val transient = smooth(features.transientDensity / 5f)

        val flux = features.spectralFlux
        val fluxMean = if (flux.isEmpty()) 0f else flux.sum() / flux.size
        val fluxStd = if (flux.isEmpty()) 0f else sqrt(flux.sumOf { ((it - fluxMean) * (it - fluxMean)).toDouble() }.toFloat() / flux.size)
        val motion = (0.35f * fluxMean + 0.25f * fluxStd * 2f + 0.4f * transient).coerceIn(0f, 1f)

        val intensityControl = 0.45f + 1.1f * settings.intensity.coerceIn(0f, 1f)
        val intensity = ((0.45f + 0.55f * energy) * intensityControl).coerceIn(0.08f, 1f)

        val complexityControl = 0.35f + 1.3f * settings.complexity.coerceIn(0f, 1f)
        val complexity = ((0.25f + 0.45f * detail + 0.3f * transient) * complexityControl).coerceIn(0.05f, 1f)

        val seed = mixSeed(fingerprint(features), settings.variation)

        // Repeated structures come from rhythm: onset density, reinforced when it is regular.
        val onsetShare = (features.transientDensity / 6f).coerceIn(0f, 1f).pow(0.6f)
        val repetition = (3 + (onsetShare * 9f + (features.rhythmicRegularity ?: 0f) * 2f).roundToInt()).coerceIn(3, 14)
        val symmetry = (3 + (brightness * 5f).roundToInt() + ((1f - turbulence) * 4f).roundToInt() +
            (seed and 1L).toInt()).coerceIn(3, 12)

        return VisualParams(
            seed = seed,
            energy = energy,
            intensity = intensity,
            weight = weight,
            detail = detail,
            brightness = brightness,
            spread = spread,
            contrast = contrast,
            turbulence = turbulence,
            motion = motion,
            stillness = features.silenceRatio.coerceIn(0f, 1f),
            complexity = complexity,
            repetition = repetition,
            symmetry = symmetry,
            envelope = smoothSeries(features.energyEnvelope),
            flux = features.spectralFlux.map { it.coerceIn(0f, 1f) },
            spectrum = features.spectrumProfile.map { it.coerceIn(0f, 1f) },
        )
    }

    /** Stable 64-bit FNV-1a fingerprint of quantised features (robust to float noise). */
    fun fingerprint(f: AudioFeatures): Long {
        var h = -0x340d631b7bdddcdbL // FNV offset basis
        fun mix(v: Int) {
            var x = v
            repeat(4) {
                h = h xor (x and 0xff).toLong()
                h *= 0x100000001b3L
                x = x ushr 8
            }
        }
        mix((f.rms * 10_000).roundToInt())
        mix((f.peakAmplitude * 10_000).roundToInt())
        mix(f.spectralCentroid.roundToInt())
        mix(f.spectralBandwidth.roundToInt())
        mix((f.spectralFlatness * 10_000).roundToInt())
        mix((f.durationSec * 1000).roundToInt())
        mix(f.onsetCount)
        f.energyEnvelope.forEach { mix((it * 1000).roundToInt()) }
        f.spectrumProfile.forEach { mix((it * 1000).roundToInt()) }
        return h
    }

    private fun mixSeed(base: Long, variation: Int): Long {
        // SplitMix64 finaliser over the base seed offset by the golden-ratio increment.
        var z = base + variation * -0x61c8864680b583ebL
        z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L
        z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
        return z xor (z ushr 31)
    }

    private fun smooth(x: Float): Float {
        val t = x.coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    /** Light 5-tap smoothing so single noisy frames don't create jagged geometry. */
    private fun smoothSeries(series: List<Float>): List<Float> {
        if (series.size < 3) return series.map { it.coerceIn(0f, 1f) }
        val w = floatArrayOf(0.1f, 0.2f, 0.4f, 0.2f, 0.1f)
        return List(series.size) { i ->
            var acc = 0f
            var norm = 0f
            for (k in -2..2) {
                val j = i + k
                if (j in series.indices) {
                    acc += series[j] * w[k + 2]
                    norm += w[k + 2]
                }
            }
            (acc / norm).coerceIn(0f, 1f)
        }
    }
}

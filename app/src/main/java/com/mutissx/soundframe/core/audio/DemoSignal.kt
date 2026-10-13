package com.mutissx.soundframe.core.audio

import com.mutissx.soundframe.domain.model.AnalysisQuality
import com.mutissx.soundframe.domain.model.AudioFeatures
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin

/**
 * Synthetic, deterministic "sounds" used by onboarding and the home hero before the user has
 * recorded anything. Each preset mimics the feature profile of a real-world sound.
 */
object DemoSignal {

    enum class Preset { VOICE, HEARTBEAT, OCEAN, RAIN, MUSIC }

    fun features(preset: Preset): AudioFeatures = when (preset) {
        Preset.VOICE -> build(
            rms = 0.12f, centroid = 1400f, bandwidth = 1300f, flatness = 0.08f,
            low = 0.22f, mid = 0.68f, high = 0.10f, transient = 1.6f, regularity = null,
            envelope = { t -> 0.35f + 0.65f * abs(sin(t * PI.toFloat() * 3.2f)) * (0.6f + 0.4f * sin(t * 9f)) },
            flux = { t -> 0.3f + 0.7f * abs(sin(t * 20f)) * abs(sin(t * 5f)) },
            spectrum = { b -> bell(b, 0.42f, 0.22f) },
        )
        Preset.HEARTBEAT -> build(
            rms = 0.09f, centroid = 180f, bandwidth = 260f, flatness = 0.05f,
            low = 0.82f, mid = 0.16f, high = 0.02f, transient = 2.2f, regularity = 0.86f,
            envelope = { t -> pulse(t * 7f) },
            flux = { t -> pulse(t * 7f) },
            spectrum = { b -> bell(b, 0.12f, 0.12f) },
        )
        Preset.OCEAN -> build(
            rms = 0.08f, centroid = 2200f, bandwidth = 2600f, flatness = 0.38f,
            low = 0.38f, mid = 0.42f, high = 0.20f, transient = 0.3f, regularity = null,
            envelope = { t -> 0.45f + 0.55f * (0.5f + 0.5f * sin(t * 2f * PI.toFloat() * 1.3f)) },
            flux = { t -> 0.2f + 0.3f * (0.5f + 0.5f * sin(t * 8.2f)) },
            spectrum = { b -> 0.75f - 0.35f * b },
        )
        Preset.RAIN -> build(
            rms = 0.06f, centroid = 5200f, bandwidth = 3300f, flatness = 0.46f,
            low = 0.06f, mid = 0.40f, high = 0.54f, transient = 5.5f, regularity = 0.2f,
            envelope = { t -> 0.6f + 0.4f * abs(sin(t * 61f)) * abs(sin(t * 17f)) },
            flux = { t -> abs(sin(t * 83f)) },
            spectrum = { b -> 0.3f + 0.6f * b },
        )
        Preset.MUSIC -> build(
            rms = 0.18f, centroid = 2100f, bandwidth = 2200f, flatness = 0.15f,
            low = 0.40f, mid = 0.45f, high = 0.15f, transient = 3.0f, regularity = 0.72f,
            envelope = { t -> 0.5f + 0.5f * pulse(t * 12f) * (0.7f + 0.3f * sin(t * 4f)) },
            flux = { t -> pulse(t * 12f) },
            spectrum = { b -> 0.5f + 0.4f * bell(b, 0.2f, 0.15f) + 0.2f * bell(b, 0.6f, 0.2f) },
        )
    }

    /** Animated level for the hero: a living, breathing signal in 0..1. */
    fun liveLevel(timeSec: Float): Float {
        val beat = pulse(timeSec * 1.1f)
        val drift = 0.5f + 0.5f * sin(timeSec * 0.7f)
        return (0.25f + 0.45f * beat + 0.3f * drift * sin(timeSec * 3.1f).let { abs(it) }).coerceIn(0f, 1f)
    }

    private fun pulse(x: Float): Float {
        val phase = x - kotlin.math.floor(x)
        return exp(-phase * 9f) + 0.55f * exp(-((phase - 0.28f) * (phase - 0.28f)) * 260f)
    }

    private fun bell(x: Float, center: Float, width: Float): Float =
        exp(-((x - center) * (x - center)) / (2 * width * width))

    private fun build(
        rms: Float, centroid: Float, bandwidth: Float, flatness: Float,
        low: Float, mid: Float, high: Float, transient: Float, regularity: Float?,
        envelope: (Float) -> Float, flux: (Float) -> Float, spectrum: (Float) -> Float,
    ): AudioFeatures {
        val env = List(AudioFeatures.ENVELOPE_POINTS) { envelope(it / (AudioFeatures.ENVELOPE_POINTS - 1f)) }
        val fl = List(AudioFeatures.ENVELOPE_POINTS) { flux(it / (AudioFeatures.ENVELOPE_POINTS - 1f)) }
        val sp = List(AudioFeatures.SPECTRUM_BANDS) { spectrum(it / (AudioFeatures.SPECTRUM_BANDS - 1f)) }
        val envMax = env.max().coerceAtLeast(1e-6f)
        val flMax = fl.max().coerceAtLeast(1e-6f)
        val spMax = sp.max().coerceAtLeast(1e-6f)
        return AudioFeatures(
            durationSec = 8f, sampleRate = 44_100, rms = rms, peakAmplitude = (rms * 4f).coerceAtMost(1f),
            crestFactorDb = 12f, dynamicRangeDb = 22f, spectralCentroid = centroid, spectralBandwidth = bandwidth,
            spectralFlatness = flatness, lowFrequencyEnergy = low, midFrequencyEnergy = mid, highFrequencyEnergy = high,
            zeroCrossingRate = (centroid / 44_100f * 2f).coerceIn(0f, 1f), transientDensity = transient,
            onsetCount = (transient * 8).toInt(), silenceRatio = 0.08f, clippingRatio = 0f, rhythmicRegularity = regularity,
            energyEnvelope = env.map { (it / envMax).coerceIn(0f, 1f) },
            spectralFlux = fl.map { (it / flMax).coerceIn(0f, 1f) },
            spectrumProfile = sp.map { (it / spMax).coerceIn(0f, 1f) },
            quality = AnalysisQuality.OK,
        )
    }
}

package com.mutissx.soundframe.domain.model

import kotlinx.serialization.Serializable

/**
 * Immutable description of a recording, produced once by the analyzer and never altered by editing.
 *
 * Absolute values (Hz, dB, seconds) are kept so they can be shown to the user and re-normalised by
 * future engine versions; the series are normalised to 0..1 relative to their own maximum.
 */
@Serializable
data class AudioFeatures(
    val durationSec: Float,
    val sampleRate: Int,
    /** Root-mean-square amplitude of the whole clip, 0..1 (full scale). */
    val rms: Float,
    /** Absolute peak sample, 0..1 (full scale). */
    val peakAmplitude: Float,
    /** Peak-to-RMS ratio in dB. */
    val crestFactorDb: Float,
    /** Spread between loud (p95) and quiet (p10) frames, in dB. */
    val dynamicRangeDb: Float,
    /** Energy-weighted spectral centroid in Hz. */
    val spectralCentroid: Float,
    /** Energy-weighted spectral bandwidth (standard deviation around the centroid) in Hz. */
    val spectralBandwidth: Float,
    /** Wiener entropy 0..1: ~0 for pure tones, ~1 for white noise. */
    val spectralFlatness: Float,
    /** Share of spectral energy below 250 Hz. low + mid + high == 1 (or all 0 for silence). */
    val lowFrequencyEnergy: Float,
    /** Share of spectral energy between 250 Hz and 4 kHz. */
    val midFrequencyEnergy: Float,
    /** Share of spectral energy above 4 kHz. */
    val highFrequencyEnergy: Float,
    /** Zero crossings per sample, 0..1. */
    val zeroCrossingRate: Float,
    /** Detected onsets per second. */
    val transientDensity: Float,
    val onsetCount: Int,
    /** Fraction of frames considered silent, 0..1. */
    val silenceRatio: Float,
    /** Fraction of samples at or near full scale, 0..1. */
    val clippingRatio: Float,
    /**
     * 0..1 regularity of inter-onset intervals. `null` when there are too few onsets for the
     * measure to be meaningful — we deliberately never infer a BPM from arbitrary sounds.
     */
    val rhythmicRegularity: Float?,
    /** Short-time RMS envelope, [ENVELOPE_POINTS] points, normalised 0..1. */
    val energyEnvelope: List<Float>,
    /** Half-wave rectified spectral flux, [ENVELOPE_POINTS] points, normalised 0..1. */
    val spectralFlux: List<Float>,
    /** Average log-magnitude spectrum in [SPECTRUM_BANDS] log-spaced bands, normalised 0..1. */
    val spectrumProfile: List<Float>,
    val quality: AnalysisQuality,
) {
    companion object {
        const val ENVELOPE_POINTS = 64
        const val SPECTRUM_BANDS = 32
    }
}

@Serializable
enum class AnalysisQuality {
    OK,
    MOSTLY_SILENT,
    CLIPPED,
    TOO_SHORT,
}

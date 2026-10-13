package com.mutissx.soundframe.domain.model

/**
 * Mono 16-bit PCM audio held in memory. At most ten seconds at 44.1 kHz (~860 KB), so keeping it
 * in memory for the lifetime of a creation session is cheap.
 */
class AudioClip(val samples: ShortArray, val sampleRate: Int) {
    val durationMs: Long get() = if (sampleRate == 0) 0 else samples.size * 1000L / sampleRate

    /** Converts to normalised floats in -1..1 for analysis. */
    fun toFloatArray(): FloatArray = FloatArray(samples.size) { samples[it] / 32768f }

    companion object {
        const val DEFAULT_SAMPLE_RATE = 44_100
        const val MAX_DURATION_MS = 10_000L
    }
}

data class CreationSession(
    val id: String,
    val clip: AudioClip,
    val source: AudioSource,
    val features: AudioFeatures? = null,
)

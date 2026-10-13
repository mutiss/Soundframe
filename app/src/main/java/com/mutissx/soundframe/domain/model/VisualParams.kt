package com.mutissx.soundframe.domain.model

/**
 * The bridge between sound and image: every renderer reads only these normalised values.
 * All scalar fields are in 0..1 unless stated otherwise. Produced deterministically by
 * [com.mutissx.soundframe.domain.usecase.VisualParamMapper].
 */
data class VisualParams(
    val seed: Long,
    /** Overall loudness. */
    val energy: Float,
    /** Final visual intensity after the user's intensity control. */
    val intensity: Float,
    /** Bass weight → mass, thickness and curvature. */
    val weight: Float,
    /** Treble share → fine detail and particles. */
    val detail: Float,
    /** Brightness of the sound (spectral centroid) → position in the palette. */
    val brightness: Float,
    /** Spectral bandwidth → spatial spread. */
    val spread: Float,
    /** Dynamic range → contrast and spatial variation. */
    val contrast: Float,
    /** Noise-likeness → turbulence and irregularity. */
    val turbulence: Float,
    /** Mean spectral flux → amount and speed of motion. */
    val motion: Float,
    /** Silence share → negative space. */
    val stillness: Float,
    /** Final structural complexity after the user's complexity control. */
    val complexity: Float,
    /** Count of repeated structures (rings, ribbons, petals...). 3..14. */
    val repetition: Int,
    /** Rotational symmetry order for radial styles. 3..12. */
    val symmetry: Int,
    val envelope: List<Float>,
    val flux: List<Float>,
    val spectrum: List<Float>,
) {
    /** Linear interpolation into the envelope; [t] in 0..1. */
    fun envelopeAt(t: Float): Float = sample(envelope, t)

    fun fluxAt(t: Float): Float = sample(flux, t)

    fun spectrumAt(t: Float): Float = sample(spectrum, t)

    private fun sample(series: List<Float>, t: Float): Float {
        if (series.isEmpty()) return 0f
        if (series.size == 1) return series[0]
        val x = t.coerceIn(0f, 1f) * (series.size - 1)
        val i = x.toInt().coerceAtMost(series.size - 2)
        val f = x - i
        return series[i] * (1 - f) + series[i + 1] * f
    }
}

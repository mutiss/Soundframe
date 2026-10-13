package com.mutissx.soundframe.core.graphics

import com.mutissx.soundframe.domain.model.BackgroundTone
import com.mutissx.soundframe.domain.model.PaletteId
import kotlin.math.floor

/** Curated palettes: five stops each, ordered from luminous highlight to deep shadow. */
class Palette(val id: PaletteId, val stops: IntArray) {

    /** Smooth sample along the stops. Out-of-range values ping-pong so offsets never jump. */
    fun sample(t: Float): Int {
        var u = t - 2f * floor(t / 2f)
        if (u > 1f) u = 2f - u
        val x = u * (stops.size - 1)
        val i = x.toInt().coerceAtMost(stops.size - 2)
        return ColorUtil.lerp(stops[i], stops[i + 1], x - i)
    }
}

object Palettes {
    private val all = mapOf(
        PaletteId.AURORA to intArrayOf(0xFF6CF5D2.toInt(), 0xFF35C9F2.toInt(), 0xFF5B7CFA.toInt(), 0xFFA35CFF.toInt(), 0xFFFF6AC8.toInt()),
        PaletteId.SOLAR_FLARE to intArrayOf(0xFFFFE6A3.toInt(), 0xFFFFB54C.toInt(), 0xFFFF6B3D.toInt(), 0xFFE5305C.toInt(), 0xFF8C1F78.toInt()),
        PaletteId.DEEP_OCEAN to intArrayOf(0xFFB5F4FF.toInt(), 0xFF4ACDE6.toInt(), 0xFF1F8BCB.toInt(), 0xFF2350A8.toInt(), 0xFF1A2466.toInt()),
        PaletteId.ROSE_QUARTZ to intArrayOf(0xFFFFE6EE.toInt(), 0xFFF9B8CB.toInt(), 0xFFE88AAA.toInt(), 0xFFBC78A3.toInt(), 0xFF7E5C93.toInt()),
        PaletteId.ELECTRIC_VIOLET to intArrayOf(0xFFE3C8FF.toInt(), 0xFFB56DFF.toInt(), 0xFF7C3AED.toInt(), 0xFF4F46E5.toInt(), 0xFF22D3EE.toInt()),
        PaletteId.NOCTURNE to intArrayOf(0xFFF4D894.toInt(), 0xFFD49A62.toInt(), 0xFF7FA697.toInt(), 0xFF3D6E78.toInt(), 0xFF22435A.toInt()),
        PaletteId.MONOCHROME to intArrayOf(0xFFFFFFFF.toInt(), 0xFFD9D9E0.toInt(), 0xFFA3A3AD.toInt(), 0xFF6B6B75.toInt(), 0xFF3A3A42.toInt()),
    )

    fun of(id: PaletteId): Palette = Palette(id, all.getValue(id))
}

object Backgrounds {
    fun base(tone: BackgroundTone): Int = when (tone) {
        BackgroundTone.VOID -> 0xFF06060A.toInt()
        BackgroundTone.MIDNIGHT -> 0xFF070C1C.toInt()
        BackgroundTone.EMBER -> 0xFF140809.toInt()
        BackgroundTone.PEARL -> 0xFFF3EFE8.toInt()
    }

    fun deep(tone: BackgroundTone): Int = when (tone) {
        BackgroundTone.VOID -> 0xFF000000.toInt()
        BackgroundTone.MIDNIGHT -> 0xFF02040C.toInt()
        BackgroundTone.EMBER -> 0xFF070203.toInt()
        BackgroundTone.PEARL -> 0xFFDCD5CB.toInt()
    }
}

object ColorUtil {
    fun lerp(a: Int, b: Int, t: Float): Int {
        val f = t.coerceIn(0f, 1f)
        val aa = a ushr 24; val ar = (a shr 16) and 0xff; val ag = (a shr 8) and 0xff; val ab = a and 0xff
        val ba = b ushr 24; val br = (b shr 16) and 0xff; val bg = (b shr 8) and 0xff; val bb = b and 0xff
        return ((aa + (ba - aa) * f).toInt() shl 24) or
            ((ar + (br - ar) * f).toInt() shl 16) or
            ((ag + (bg - ag) * f).toInt() shl 8) or
            (ab + (bb - ab) * f).toInt()
    }

    fun withAlpha(color: Int, alpha: Float): Int =
        ((alpha.coerceIn(0f, 1f) * 255f).toInt() shl 24) or (color and 0x00ffffff)

    fun luminance(color: Int): Float {
        val r = ((color shr 16) and 0xff) / 255f
        val g = ((color shr 8) and 0xff) / 255f
        val b = (color and 0xff) / 255f
        return 0.2126f * r + 0.7152f * g + 0.0722f * b
    }
}

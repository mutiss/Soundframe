package com.mutissx.soundframe.core.designsystem.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Colour tokens. Deep charcoal grounds with a small set of luminous accents. */
object SfColors {
    val Ink0 = Color(0xFF040406)
    val Ink1 = Color(0xFF09090E)
    val Ink2 = Color(0xFF101017)
    val Ink3 = Color(0xFF181822)
    val Ink4 = Color(0xFF242431)

    val TextPrimary = Color(0xFFF5F3FA)
    val TextSecondary = Color(0xFFADA9BD)
    val TextTertiary = Color(0xFF6F6B80)
    val TextOnLight = Color(0xFF0A0A10)

    val Aurora = Color(0xFF6CF5D2)
    val Cyan = Color(0xFF35C9F2)
    val Indigo = Color(0xFF5B7CFA)
    val Violet = Color(0xFFA35CFF)
    val Rose = Color(0xFFFF6AC8)
    val Solar = Color(0xFFFFB54C)
    val Danger = Color(0xFFFF5A6E)

    val GlassFill = Color(0x14FFFFFF)
    val GlassFillStrong = Color(0x24FFFFFF)
    val GlassStroke = Color(0x2EFFFFFF)
    val Hairline = Color(0x14FFFFFF)

    val Signature = listOf(Aurora, Cyan, Indigo, Violet, Rose)
    val SignatureBrush = Brush.linearGradient(Signature)
    val RecordGradient = listOf(Color(0xFFFF8A7A), Rose, Violet)
}

object SfSpacing {
    val xxs = 4.dp
    val xs = 8.dp
    val sm = 12.dp
    val md = 16.dp
    val lg = 24.dp
    val xl = 32.dp
    val xxl = 48.dp
    val screen = 24.dp
}

object SfShapes {
    val pill = RoundedCornerShape(percent = 50)
    val card = RoundedCornerShape(28.dp)
    val artwork = RoundedCornerShape(36.dp)
    val tile = RoundedCornerShape(24.dp)
    val small = RoundedCornerShape(14.dp)
    val sheet = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
}

/** Shared motion language: soft, decelerating, physical. */
object SfMotion {
    val EaseOutExpo = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)
    val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val EaseInOut = CubicBezierEasing(0.65f, 0f, 0.35f, 1f)

    const val SHORT = 180
    const val MEDIUM = 360
    const val LONG = 700
    const val CROSSFADE = 520

    fun <T> gentle() = spring<T>(dampingRatio = 0.82f, stiffness = 220f)
    fun <T> snappy() = spring<T>(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow)
    fun <T> press() = spring<T>(dampingRatio = 0.6f, stiffness = 900f)
}

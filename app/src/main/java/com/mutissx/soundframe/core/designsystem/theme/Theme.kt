package com.mutissx.soundframe.core.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.mutissx.soundframe.R

@OptIn(ExperimentalTextApi::class)
private fun manrope(weight: Int) = Font(
    R.font.manrope_variable,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val Manrope = FontFamily(manrope(300), manrope(400), manrope(500), manrope(600), manrope(700), manrope(800))

val InstrumentSerif = FontFamily(
    Font(R.font.instrument_serif_regular, FontWeight.Normal, FontStyle.Normal),
    Font(R.font.instrument_serif_italic, FontWeight.Normal, FontStyle.Italic),
)

/** Editorial serif for moments of emotion, a precise grotesk for everything functional. */
data class SfTypography(
    val hero: TextStyle = TextStyle(fontFamily = InstrumentSerif, fontSize = 52.sp, lineHeight = 54.sp, letterSpacing = (-0.02).em),
    val display: TextStyle = TextStyle(fontFamily = InstrumentSerif, fontSize = 40.sp, lineHeight = 44.sp, letterSpacing = (-0.01).em),
    val headline: TextStyle = TextStyle(fontFamily = InstrumentSerif, fontSize = 30.sp, lineHeight = 34.sp),
    val title: TextStyle = TextStyle(fontFamily = Manrope, fontWeight = FontWeight(650), fontSize = 18.sp, lineHeight = 24.sp, letterSpacing = (-0.005).em),
    val body: TextStyle = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 23.sp),
    val bodySmall: TextStyle = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 19.sp),
    val label: TextStyle = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 18.sp, letterSpacing = 0.01.em),
    val caption: TextStyle = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
    val overline: TextStyle = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.24.em),
    val wordmark: TextStyle = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, letterSpacing = 0.42.em),
    val numeric: TextStyle = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Light, fontSize = 56.sp, lineHeight = 60.sp, fontFeatureSettings = "tnum", letterSpacing = (-0.02).em),
)

val LocalSfTypography = staticCompositionLocalOf { SfTypography() }

/** True when the user (or the system animator scale) asked for less motion. */
val LocalReduceMotion = staticCompositionLocalOf { false }

object SoundframeTheme {
    val type: SfTypography @Composable get() = LocalSfTypography.current
}

@Composable
fun SoundframeTheme(reduceMotion: Boolean = false, content: @Composable () -> Unit) {
    val scheme = darkColorScheme(
        primary = SfColors.Aurora,
        onPrimary = SfColors.TextOnLight,
        secondary = SfColors.Violet,
        background = SfColors.Ink0,
        onBackground = SfColors.TextPrimary,
        surface = SfColors.Ink2,
        onSurface = SfColors.TextPrimary,
        surfaceVariant = SfColors.Ink3,
        onSurfaceVariant = SfColors.TextSecondary,
        error = SfColors.Danger,
        outline = SfColors.GlassStroke,
    )
    val type = SfTypography()
    CompositionLocalProvider(LocalSfTypography provides type, LocalReduceMotion provides reduceMotion) {
        MaterialTheme(
            colorScheme = scheme,
            typography = Typography(bodyLarge = type.body, bodyMedium = type.bodySmall, labelLarge = type.label),
            content = content,
        )
    }
}

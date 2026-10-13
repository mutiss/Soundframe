package com.mutissx.soundframe.core.designsystem.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mutissx.soundframe.R
import com.mutissx.soundframe.core.designsystem.theme.LocalReduceMotion
import com.mutissx.soundframe.core.designsystem.theme.SfColors
import com.mutissx.soundframe.core.designsystem.theme.SfShapes
import com.mutissx.soundframe.core.designsystem.theme.SoundframeTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Loading indicator: luminous particles tracing a Lissajous orbit — never a spinner. */
@Composable
fun ArtLoader(modifier: Modifier = Modifier, colors: List<Color> = SfColors.Signature) {
    val reduceMotion = LocalReduceMotion.current
    val t = rememberInfiniteTransition(label = "loader").animateFloat(
        0f, 1f, infiniteRepeatable(tween(2400, easing = LinearEasing)), label = "t",
    )
    Canvas(modifier) {
        val r = size.minDimension / 2f
        val dots = 9
        for (i in 0 until dots) {
            val phase = ((if (reduceMotion) 0.2f else t.value) + i * 0.035f) * 2f * PI.toFloat()
            val x = center.x + r * 0.8f * sin(phase * 2f)
            val y = center.y + r * 0.55f * cos(phase * 3f)
            val f = i / (dots - 1f)
            val color = colors[(f * (colors.size - 1)).toInt()]
            drawCircle(color.copy(alpha = 0.2f + 0.8f * f), radius = r * (0.05f + 0.09f * f), center = Offset(x, y))
        }
    }
}

/** Custom modal: glass card with an editorial title. */
@Composable
fun SfDialog(
    title: String,
    onDismiss: () -> Unit,
    confirmText: String,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    dismissText: String? = null,
    destructive: Boolean = false,
    confirmEnabled: Boolean = true,
    body: @Composable () -> Unit = {},
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
            GlassSurface(modifier.widthIn(max = 420.dp).fillMaxWidth(), strong = true) {
                Column(Modifier.padding(24.dp)) {
                    Text(title, style = SoundframeTheme.type.headline, color = SfColors.TextPrimary)
                    Spacer(Modifier.height(12.dp))
                    body()
                    Spacer(Modifier.height(24.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        GhostButton(dismissText ?: stringResource(R.string.action_cancel), onDismiss, Modifier.weight(1f))
                        LuminousButton(
                            confirmText, onConfirm, Modifier.weight(1f),
                            enabled = confirmEnabled,
                            height = 52.dp,
                            colors = if (destructive) listOf(SfColors.Danger, SfColors.Rose) else SfColors.Signature,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SfTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    onDone: () -> Unit = {},
    autoFocus: Boolean = true,
) {
    val focus = remember { FocusRequester() }
    if (autoFocus) LaunchedEffect(Unit) { focus.requestFocus() }
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = SoundframeTheme.type.title.copy(color = SfColors.TextPrimary),
        cursorBrush = Brush.verticalGradient(SfColors.Signature),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        modifier = modifier.focusRequester(focus),
        decorationBox = { inner ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(Color(0x14FFFFFF), SfShapes.small)
                    .padding(horizontal = 16.dp, vertical = 16.dp),
            ) {
                if (value.isEmpty()) Text(placeholder, style = SoundframeTheme.type.title, color = SfColors.TextTertiary)
                inner()
            }
        },
    )
}

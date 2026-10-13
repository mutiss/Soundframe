package com.mutissx.soundframe.core.designsystem.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mutissx.soundframe.core.designsystem.theme.SfColors
import com.mutissx.soundframe.core.designsystem.theme.SfMotion
import com.mutissx.soundframe.core.designsystem.theme.SfShapes
import com.mutissx.soundframe.core.designsystem.theme.SoundframeTheme

/** Frosted glass: a faint vertical sheen and a light-catching gradient edge. */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = SfShapes.card,
    strong: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    if (strong) listOf(Color(0xE6181822), Color(0xE00E0E15)) else listOf(Color(0x1FFFFFFF), Color(0x0AFFFFFF)),
                ),
            )
            .border(
                1.dp,
                Brush.linearGradient(listOf(Color(0x38FFFFFF), Color(0x0AFFFFFF), Color(0x1AFFFFFF))),
                shape,
            ),
        content = content,
    )
}

/** Physical press response used by every tappable surface in the app. */
fun Modifier.pressFeedback(interactionSource: MutableInteractionSource, pressedScale: Float = 0.95f): Modifier = composed {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) pressedScale else 1f, SfMotion.press(), label = "press")
    graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

@Composable
fun Overline(text: String, modifier: Modifier = Modifier, color: Color = SfColors.TextSecondary, dot: Boolean = false) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        if (dot) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(SfColors.SignatureBrush))
            Spacer(Modifier.width(8.dp))
        }
        Text(text.uppercase(), style = SoundframeTheme.type.overline, color = color)
    }
}

@Composable
fun StatBlock(value: String, label: String, modifier: Modifier = Modifier) {
    GlassSurface(modifier, shape = SfShapes.small) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(value, style = SoundframeTheme.type.title, color = SfColors.TextPrimary, textAlign = TextAlign.Start)
            Text(label, style = SoundframeTheme.type.caption, color = SfColors.TextTertiary)
        }
    }
}

@Composable
fun GradientDot(size: Dp = 8.dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size).clip(CircleShape).background(SfColors.SignatureBrush))
}

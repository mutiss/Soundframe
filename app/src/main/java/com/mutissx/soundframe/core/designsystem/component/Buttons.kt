package com.mutissx.soundframe.core.designsystem.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mutissx.soundframe.core.designsystem.theme.SfColors
import com.mutissx.soundframe.core.designsystem.theme.SfShapes
import com.mutissx.soundframe.core.designsystem.theme.SoundframeTheme

/** Primary call to action: a slowly drifting luminous gradient with a coloured glow. */
@Composable
fun LuminousButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    loading: Boolean = false,
    colors: List<Color> = SfColors.Signature,
    height: Dp = 58.dp,
) {
    val interaction = remember { MutableInteractionSource() }
    val drift = rememberInfiniteTransition(label = "drift").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(5200, easing = LinearEasing), RepeatMode.Reverse),
        label = "drift",
    )
    Box(
        modifier
            .pressFeedback(interaction)
            .alpha(if (enabled) 1f else 0.4f)
            .shadow(if (enabled) 22.dp else 0.dp, SfShapes.pill, ambientColor = colors.last(), spotColor = colors.first())
            .height(height)
            .clip(SfShapes.pill)
            .drawWithCache {
                onDrawBehind {
                    val shift = drift.value * size.width
                    drawRect(
                        Brush.linearGradient(
                            colors,
                            start = Offset(-size.width * 0.5f + shift, 0f),
                            end = Offset(size.width * 0.9f + shift, size.height),
                            tileMode = TileMode.Mirror,
                        ),
                    )
                    // Specular highlight along the top edge.
                    drawRoundRect(
                        Brush.verticalGradient(listOf(Color(0x55FFFFFF), Color.Transparent), endY = size.height * 0.55f),
                        cornerRadius = CornerRadius(size.height / 2f),
                    )
                }
            }
            .clickable(interaction, indication = null, enabled = enabled && !loading, role = Role.Button, onClick = onClick)
            .padding(horizontal = 28.dp),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(loading, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "loading") { isLoading ->
            if (isLoading) {
                ArtLoader(Modifier.size(28.dp), colors = listOf(SfColors.TextOnLight, SfColors.Ink3))
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (icon != null) {
                        Icon(icon, contentDescription = null, tint = SfColors.TextOnLight, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                    }
                    Text(text, style = SoundframeTheme.type.label, color = SfColors.TextOnLight)
                }
            }
        }
    }
}

/** Secondary action: glass pill. */
@Composable
fun GhostButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    contentColor: Color = SfColors.TextPrimary,
    height: Dp = 52.dp,
) {
    val interaction = remember { MutableInteractionSource() }
    GlassSurface(
        modifier
            .pressFeedback(interaction)
            .alpha(if (enabled) 1f else 0.4f)
            .height(height)
            .clickable(interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick),
        shape = SfShapes.pill,
    ) {
        Row(Modifier.align(Alignment.Center).padding(horizontal = 22.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(19.dp))
                Spacer(Modifier.width(9.dp))
            }
            Text(text, style = SoundframeTheme.type.label, color = contentColor)
        }
    }
}

/** Circular glass control for icon-only actions. Always at least 48dp for touch accessibility. */
@Composable
fun GlassIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    tint: Color = SfColors.TextPrimary,
    highlighted: Boolean = false,
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier
            .pressFeedback(interaction, 0.9f)
            .alpha(if (enabled) 1f else 0.4f)
            .size(size)
            .clip(CircleShape)
            .background(if (highlighted) Brush.linearGradient(SfColors.Signature) else Brush.verticalGradient(listOf(Color(0x24FFFFFF), Color(0x10FFFFFF))))
            .border(1.dp, Color(0x26FFFFFF), CircleShape)
            .clickable(interaction, indication = null, enabled = enabled, role = Role.Button, onClickLabel = contentDescription, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = if (highlighted) SfColors.TextOnLight else tint, modifier = Modifier.size(size * 0.44f))
    }
}

/** Low-emphasis text action. */
@Composable
fun TextAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = SfColors.TextSecondary) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier
            .pressFeedback(interaction)
            .defaultMinSize(minHeight = 48.dp)
            .clip(SfShapes.pill)
            .clickable(interaction, indication = null, role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = SoundframeTheme.type.label, color = color)
    }
}

/** Small selectable capsule for filters and options. */
@Composable
fun SelectablePill(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier
            .pressFeedback(interaction)
            .height(40.dp)
            .clip(SfShapes.pill)
            .background(if (selected) SfColors.TextPrimary else Color(0x14FFFFFF))
            .border(1.dp, if (selected) Color.Transparent else Color(0x1FFFFFFF), SfShapes.pill)
            .clickable(interaction, indication = null, role = Role.Tab, onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val color = if (selected) SfColors.TextOnLight else SfColors.TextSecondary
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
            }
            Text(text, style = SoundframeTheme.type.caption, color = color)
        }
    }
}

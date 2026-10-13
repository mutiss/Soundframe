package com.mutissx.soundframe.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mutissx.soundframe.core.designsystem.theme.SfColors
import com.mutissx.soundframe.core.designsystem.theme.SfMotion
import com.mutissx.soundframe.core.designsystem.theme.SfShapes
import com.mutissx.soundframe.core.designsystem.theme.SoundframeTheme

/** Minimal slider: hairline track, luminous fill and a glowing thumb. */
@Composable
fun SfSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    label: String,
    valueText: String,
    modifier: Modifier = Modifier,
    colors: List<Color> = SfColors.Signature,
) {
    val latest by rememberUpdatedState(onValueChange)
    val shown by animateFloatAsState(value, SfMotion.snappy(), label = "slider")
    Column(modifier) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = SoundframeTheme.type.label, color = SfColors.TextPrimary)
            Text(valueText, style = SoundframeTheme.type.caption, color = SfColors.TextSecondary)
        }
        Spacer(Modifier.height(4.dp))
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(36.dp)
                .semantics {
                    contentDescription = label
                    progressBarRangeInfo = ProgressBarRangeInfo(value, 0f..1f)
                    setProgress { target -> latest(target.coerceIn(0f, 1f)); true }
                }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        latest((down.position.x / size.width).coerceIn(0f, 1f))
                        drag(down.id) { change ->
                            latest((change.position.x / size.width).coerceIn(0f, 1f))
                            change.consume()
                        }
                    }
                },
        ) {
            val y = size.height / 2f
            val track = 4.dp.toPx()
            drawRoundRect(Color(0x1FFFFFFF), Offset(0f, y - track / 2), Size(size.width, track), CornerRadius(track))
            val x = shown * size.width
            drawRoundRect(
                Brush.horizontalGradient(colors, endX = size.width),
                Offset(0f, y - track / 2), Size(x, track), CornerRadius(track),
            )
            drawCircle(colors[(colors.size - 1) / 2].copy(alpha = 0.28f), 16.dp.toPx(), Offset(x, y))
            drawCircle(Color.White, 9.dp.toPx(), Offset(x, y))
        }
    }
}

@Composable
fun SfSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val knob by animateDpAsState(if (checked) 22.dp else 0.dp, SfMotion.snappy(), label = "knob")
    Box(
        modifier
            .size(52.dp, 30.dp)
            .clip(SfShapes.pill)
            .background(if (checked) Brush.horizontalGradient(SfColors.Signature) else Brush.horizontalGradient(listOf(Color(0x24FFFFFF), Color(0x24FFFFFF))))
            .toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(4.dp),
    ) {
        Box(
            Modifier
                .offset(x = knob)
                .size(22.dp)
                .clip(CircleShape)
                .background(if (enabled) Color.White else Color(0x88FFFFFF)),
        )
    }
}

/** Glass segmented control with a sliding indicator. */
@Composable
fun SegmentedTabs(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    GlassSurface(modifier.height(48.dp), shape = SfShapes.pill) {
        BoxWithConstraints(Modifier.fillMaxWidth().padding(4.dp)) {
            val segment = maxWidth / options.size
            val offset by animateDpAsState(segment * selected, SfMotion.snappy(), label = "tab")
            Box(
                Modifier
                    .offset(x = offset)
                    .width(segment)
                    .fillMaxHeight()
                    .clip(SfShapes.pill)
                    .background(Color(0x2EFFFFFF)),
            )
            Row(Modifier.fillMaxWidth().fillMaxHeight()) {
                options.forEachIndexed { index, label ->
                    val color by animateColorAsState(if (index == selected) SfColors.TextPrimary else SfColors.TextTertiary, label = "tabColor")
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(SfShapes.pill)
                            .semantics { this.selected = index == selected }
                            .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Tab) { onSelect(index) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(label, style = SoundframeTheme.type.caption, color = color, textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
}

/** Palette preview: the palette's own sweep of light, with a ring when selected. */
@Composable
fun PaletteSwatch(
    colors: List<Color>,
    name: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scale by animateFloatAsState(if (selected) 1.08f else 1f, SfMotion.snappy(), label = "swatch")
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier
            .pressFeedback(interaction)
            .clickable(interaction, indication = null, role = Role.RadioButton, onClickLabel = name, onClick = onClick)
            .semantics { this.selected = selected },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(66.dp)
                .scale(scale)
                .border(2.dp, if (selected) Color.White else Color.Transparent, CircleShape)
                .padding(5.dp)
                .clip(CircleShape)
                .background(Brush.sweepGradient(colors + colors.first())),
        ) {
            Box(
                Modifier
                    .size(56.dp)
                    .background(Brush.radialGradient(listOf(Color(0x66FFFFFF), Color.Transparent), radius = 70f, center = Offset(40f, 36f))),
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(name, style = SoundframeTheme.type.caption, color = if (selected) SfColors.TextPrimary else SfColors.TextTertiary)
    }
}

/** Title / subtitle row with a trailing control, used in settings and option sheets. */
@Composable
fun SettingRow(title: String, subtitle: String?, modifier: Modifier = Modifier, trailing: @Composable () -> Unit) {
    Row(modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 16.dp)) {
            Text(title, style = SoundframeTheme.type.label, color = SfColors.TextPrimary)
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(subtitle, style = SoundframeTheme.type.bodySmall, color = SfColors.TextTertiary)
            }
        }
        trailing()
    }
}

package com.mutissx.soundframe.core.designsystem.component

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mutissx.soundframe.R
import com.mutissx.soundframe.core.designsystem.icon.SfIcons
import com.mutissx.soundframe.core.designsystem.theme.SfColors
import com.mutissx.soundframe.core.designsystem.theme.SfMotion
import com.mutissx.soundframe.core.designsystem.theme.SfShapes
import com.mutissx.soundframe.core.designsystem.theme.SfSpacing
import com.mutissx.soundframe.core.designsystem.theme.SoundframeTheme

@Composable
fun Wordmark(modifier: Modifier = Modifier, color: Color = SfColors.TextPrimary) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        GradientDot(9.dp)
        Spacer(Modifier.width(10.dp))
        Text("SOUNDFRAME", style = SoundframeTheme.type.wordmark, color = color)
    }
}

/** Elongating-dot page indicator. */
@Composable
fun PageIndicator(count: Int, current: Int, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(count) { index ->
            val width by animateDpAsState(if (index == current) 26.dp else 6.dp, SfMotion.snappy(), label = "dot")
            Box(
                Modifier
                    .height(6.dp)
                    .width(width)
                    .clip(SfShapes.pill)
                    .background(if (index == current) SfColors.SignatureBrush else Brush.linearGradient(listOf(Color(0x40FFFFFF), Color(0x40FFFFFF)))),
            )
        }
    }
}

/** Top bar for pushed screens: glass back button, centred title, optional trailing action. */
@Composable
fun SfTopBar(
    title: String?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit = { Spacer(Modifier.width(48.dp)) },
) {
    Row(
        modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = SfSpacing.md, vertical = SfSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlassIconButton(SfIcons.Back, stringResource(R.string.action_back), onBack)
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            if (title != null) {
                Text(
                    title, style = SoundframeTheme.type.label, color = SfColors.TextPrimary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                )
            }
        }
        trailing()
    }
}

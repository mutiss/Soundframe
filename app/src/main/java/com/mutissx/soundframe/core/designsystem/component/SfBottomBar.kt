package com.mutissx.soundframe.core.designsystem.component

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.mutissx.soundframe.R
import com.mutissx.soundframe.core.designsystem.icon.SfIcons
import com.mutissx.soundframe.core.designsystem.theme.SfColors
import com.mutissx.soundframe.core.designsystem.theme.SfShapes
import com.mutissx.soundframe.core.designsystem.theme.SoundframeTheme

enum class TopLevelDestination(@StringRes val label: Int, val icon: ImageVector) {
    STUDIO(R.string.nav_studio, SfIcons.Studio),
    GALLERY(R.string.nav_gallery, SfIcons.Gallery),
    SETTINGS(R.string.nav_settings, SfIcons.Settings),
}

/** Floating glass navigation pill; the active destination expands to reveal its label. */
@Composable
fun SfBottomBar(selected: TopLevelDestination, onSelect: (TopLevelDestination) -> Unit, modifier: Modifier = Modifier) {
    GlassSurface(modifier.height(64.dp), shape = SfShapes.pill, strong = true) {
        Row(
            Modifier.padding(6.dp).fillMaxHeight(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TopLevelDestination.entries.forEach { destination ->
                val isSelected = destination == selected
                val label = stringResource(destination.label)
                val interaction = remember { MutableInteractionSource() }
                Box(
                    Modifier
                        .pressFeedback(interaction)
                        .fillMaxHeight()
                        .clip(SfShapes.pill)
                        .background(if (isSelected) Brush.horizontalGradient(listOf(Color(0x33FFFFFF), Color(0x1AFFFFFF))) else Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent)))
                        .semantics { this.selected = isSelected }
                        .clickable(interaction, indication = null, role = Role.Tab, onClickLabel = label) { onSelect(destination) }
                        .padding(horizontal = 18.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            destination.icon,
                            contentDescription = label,
                            tint = if (isSelected) SfColors.TextPrimary else SfColors.TextTertiary,
                            modifier = Modifier.size(22.dp),
                        )
                        AnimatedVisibility(isSelected, enter = fadeIn() + expandHorizontally(), exit = fadeOut() + shrinkHorizontally()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Spacer(Modifier.width(8.dp))
                                Text(label, style = SoundframeTheme.type.label, color = SfColors.TextPrimary)
                            }
                        }
                    }
                }
            }
        }
    }
}

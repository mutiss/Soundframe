package com.mutissx.soundframe.feature.onboarding

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.mutissx.soundframe.R
import com.mutissx.soundframe.core.audio.DemoSignal
import com.mutissx.soundframe.core.designsystem.art.ArtworkCanvas
import com.mutissx.soundframe.core.designsystem.component.LuminousButton
import com.mutissx.soundframe.core.designsystem.component.Overline
import com.mutissx.soundframe.core.designsystem.component.PageIndicator
import com.mutissx.soundframe.core.designsystem.component.TextAction
import com.mutissx.soundframe.core.designsystem.component.Wordmark
import com.mutissx.soundframe.core.designsystem.icon.SfIcons
import com.mutissx.soundframe.core.designsystem.theme.SfColors
import com.mutissx.soundframe.core.designsystem.theme.SfSpacing
import com.mutissx.soundframe.core.designsystem.theme.SoundframeTheme
import com.mutissx.soundframe.domain.model.ArtStyle
import com.mutissx.soundframe.domain.model.ArtworkSettings
import com.mutissx.soundframe.domain.model.BackgroundTone
import com.mutissx.soundframe.domain.model.PaletteId
import com.mutissx.soundframe.domain.repository.PreferencesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import kotlin.math.abs

class OnboardingViewModel(private val preferences: PreferencesRepository) : ViewModel() {
    private val _completed = MutableStateFlow(false)
    val completed: StateFlow<Boolean> = _completed.asStateFlow()

    fun complete() {
        viewModelScope.launch {
            preferences.setOnboardingCompleted(true)
            _completed.value = true
        }
    }
}

private data class OnboardingPage(
    @StringRes val overline: Int,
    @StringRes val title: Int,
    @StringRes val body: Int,
    val preset: DemoSignal.Preset,
    val settings: ArtworkSettings,
)

private val pages = listOf(
    OnboardingPage(
        R.string.onboarding_1_overline, R.string.onboarding_1_title, R.string.onboarding_1_body,
        DemoSignal.Preset.VOICE, ArtworkSettings(ArtStyle.AURA, PaletteId.AURORA, complexity = 0.65f),
    ),
    OnboardingPage(
        R.string.onboarding_2_overline, R.string.onboarding_2_title, R.string.onboarding_2_body,
        DemoSignal.Preset.HEARTBEAT, ArtworkSettings(ArtStyle.ECHO, PaletteId.ROSE_QUARTZ, BackgroundTone.VOID),
    ),
    OnboardingPage(
        R.string.onboarding_3_overline, R.string.onboarding_3_title, R.string.onboarding_3_body,
        DemoSignal.Preset.MUSIC, ArtworkSettings(ArtStyle.BLOOM, PaletteId.SOLAR_FLARE, BackgroundTone.EMBER),
    ),
    OnboardingPage(
        R.string.onboarding_4_overline, R.string.onboarding_4_title, R.string.onboarding_4_body,
        DemoSignal.Preset.RAIN, ArtworkSettings(ArtStyle.PARTICLES, PaletteId.ELECTRIC_VIOLET, BackgroundTone.MIDNIGHT),
    ),
)

@Composable
fun OnboardingScreen(onFinished: () -> Unit, viewModel: OnboardingViewModel = koinViewModel()) {
    val completed by viewModel.completed.collectAsStateWithLifecycle()
    LaunchedEffect(completed) { if (completed) onFinished() }
    OnboardingContent(onComplete = viewModel::complete)
}

@Composable
private fun OnboardingContent(onComplete: () -> Unit) {
    val pager = rememberPagerState { pages.size }
    val scope = rememberCoroutineScope()
    val isLast = pager.currentPage == pages.lastIndex

    Box(Modifier.fillMaxSize().background(SfColors.Ink0)) {
        AnimatedContent(
            targetState = pager.currentPage,
            transitionSpec = { fadeIn(tween(900)) togetherWith fadeOut(tween(900)) },
            label = "onboardingArt",
        ) { index ->
            val page = pages[index]
            val features = remember(page.preset) { DemoSignal.features(page.preset) }
            ArtworkCanvas(
                features, page.settings,
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        // Gentle parallax while swiping.
                        translationX = -pager.currentPageOffsetFraction * size.width * 0.15f
                        scaleX = 1.08f
                        scaleY = 1.08f
                    },
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to SfColors.Ink0.copy(alpha = 0.55f),
                        0.22f to Color.Transparent,
                        0.5f to Color.Transparent,
                        0.78f to SfColors.Ink0.copy(alpha = 0.88f),
                        1f to SfColors.Ink0,
                    ),
                ),
        )

        HorizontalPager(pager, Modifier.fillMaxSize()) { index ->
            val page = pages[index]
            Column(
                Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
                    .padding(start = SfSpacing.screen, end = SfSpacing.screen, bottom = 196.dp)
                    .graphicsLayer {
                        val offset = pager.getOffsetDistanceInPages(index)
                        alpha = (1f - abs(offset) * 1.6f).coerceIn(0f, 1f)
                        translationX = offset * size.width * 0.35f
                    },
                verticalArrangement = Arrangement.Bottom,
            ) {
                Overline(stringResource(page.overline), dot = true, color = SfColors.TextPrimary.copy(alpha = 0.8f))
                Spacer(Modifier.height(SfSpacing.md))
                Text(stringResource(page.title), style = SoundframeTheme.type.hero, color = SfColors.TextPrimary)
                Spacer(Modifier.height(SfSpacing.md))
                Text(stringResource(page.body), style = SoundframeTheme.type.body, color = SfColors.TextSecondary)
            }
        }

        // Drawn after the full-screen pager so it stays on top and receives touches.
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = SfSpacing.screen, vertical = SfSpacing.sm).heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Wordmark()
            Spacer(Modifier.weight(1f))
            if (!isLast) TextAction(stringResource(R.string.action_skip), onComplete)
        }

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = SfSpacing.screen, vertical = SfSpacing.lg),
        ) {
            PageIndicator(pages.size, pager.currentPage)
            Spacer(Modifier.height(SfSpacing.lg))
            LuminousButton(
                text = stringResource(if (isLast) R.string.onboarding_cta else R.string.action_continue),
                onClick = {
                    if (isLast) onComplete() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(SfSpacing.md))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Icon(SfIcons.Shield, contentDescription = null, tint = SfColors.Aurora, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.privacy_footer),
                    style = SoundframeTheme.type.caption, color = SfColors.TextTertiary,
                )
            }
        }
    }
}

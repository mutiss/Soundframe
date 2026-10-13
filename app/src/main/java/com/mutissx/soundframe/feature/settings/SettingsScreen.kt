package com.mutissx.soundframe.feature.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.mutissx.soundframe.R
import com.mutissx.soundframe.core.designsystem.component.AmbientBackground
import com.mutissx.soundframe.core.designsystem.component.GhostButton
import com.mutissx.soundframe.core.designsystem.component.GlassSurface
import com.mutissx.soundframe.core.designsystem.component.Overline
import com.mutissx.soundframe.core.designsystem.component.SettingRow
import com.mutissx.soundframe.core.designsystem.component.SfDialog
import com.mutissx.soundframe.core.designsystem.component.SfSwitch
import com.mutissx.soundframe.core.designsystem.component.TextAction
import com.mutissx.soundframe.core.designsystem.component.Wordmark
import com.mutissx.soundframe.core.designsystem.icon.SfIcons
import com.mutissx.soundframe.core.designsystem.theme.SfColors
import com.mutissx.soundframe.core.designsystem.theme.SfSpacing
import com.mutissx.soundframe.core.designsystem.theme.SoundframeTheme
import com.mutissx.soundframe.domain.repository.PreferencesRepository
import com.mutissx.soundframe.domain.repository.UserPreferences
import com.mutissx.soundframe.domain.usecase.DeleteAllDataUseCase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

class SettingsViewModel(
    private val preferences: PreferencesRepository,
    private val deleteAll: DeleteAllDataUseCase,
) : ViewModel() {
    val state: StateFlow<UserPreferences> = preferences.preferences
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserPreferences(onboardingCompleted = true))

    fun setKeepAudio(value: Boolean) { viewModelScope.launch { preferences.setKeepOriginalAudio(value) } }
    fun setSignature(value: Boolean) { viewModelScope.launch { preferences.setWatermarkExports(value) } }
    fun setReduceMotion(value: Boolean) { viewModelScope.launch { preferences.setReduceMotion(value) } }
    fun deleteEverything() { viewModelScope.launch { deleteAll() } }
}

private val privacyPoints = listOf(
    R.string.settings_privacy_1,
    R.string.settings_privacy_2,
    R.string.settings_privacy_3,
    R.string.settings_privacy_4,
)

@Composable
fun SettingsScreen(viewModel: SettingsViewModel = koinViewModel()) {
    val prefs by viewModel.state.collectAsStateWithLifecycle()
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val version = remember(context) {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "1.0"
    }

    Box(Modifier.fillMaxSize()) {
        AmbientBackground()
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(horizontal = SfSpacing.screen)
                .padding(bottom = 120.dp),
        ) {
            Spacer(Modifier.height(SfSpacing.lg))
            Overline(stringResource(R.string.settings_overline), dot = true)
            Spacer(Modifier.height(SfSpacing.xs))
            Text(stringResource(R.string.nav_settings), style = SoundframeTheme.type.hero, color = SfColors.TextPrimary)
            Spacer(Modifier.height(SfSpacing.lg))

            GlassSurface(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(SfSpacing.lg)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(SfIcons.Shield, contentDescription = null, tint = SfColors.Aurora, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(stringResource(R.string.private_by_design), style = SoundframeTheme.type.title, color = SfColors.TextPrimary)
                    }
                    Spacer(Modifier.height(SfSpacing.sm))
                    privacyPoints.forEach { point ->
                        Row(Modifier.padding(vertical = 4.dp)) {
                            Icon(SfIcons.Check, contentDescription = null, tint = SfColors.TextSecondary, modifier = Modifier.size(16.dp).padding(top = 2.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(stringResource(point), style = SoundframeTheme.type.bodySmall, color = SfColors.TextSecondary)
                        }
                    }
                    val policyUrl = stringResource(R.string.privacy_policy_url)
                    TextAction(
                        stringResource(R.string.settings_privacy_policy),
                        onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(policyUrl))) },
                        color = SfColors.Aurora,
                        modifier = Modifier.offset(x = (-14).dp),
                    )
                }
            }
            Spacer(Modifier.height(SfSpacing.md))

            GlassSurface(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(horizontal = SfSpacing.lg, vertical = SfSpacing.sm)) {
                    SettingRow(stringResource(R.string.settings_keep_audio), stringResource(R.string.settings_keep_audio_body)) {
                        SfSwitch(prefs.keepOriginalAudio, viewModel::setKeepAudio)
                    }
                    SettingRow(stringResource(R.string.settings_sign), stringResource(R.string.settings_sign_body)) {
                        SfSwitch(prefs.watermarkExports, viewModel::setSignature)
                    }
                    SettingRow(stringResource(R.string.settings_reduce_motion), stringResource(R.string.settings_reduce_motion_body)) {
                        SfSwitch(prefs.reduceMotion, viewModel::setReduceMotion)
                    }
                }
            }
            Spacer(Modifier.height(SfSpacing.md))

            GlassSurface(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(SfSpacing.lg)) {
                    Text(stringResource(R.string.settings_data_title), style = SoundframeTheme.type.title, color = SfColors.TextPrimary)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.settings_data_body),
                        style = SoundframeTheme.type.bodySmall, color = SfColors.TextSecondary,
                    )
                    Spacer(Modifier.height(SfSpacing.md))
                    GhostButton(stringResource(R.string.settings_delete_all), { confirmDelete = true }, icon = SfIcons.Trash, contentColor = SfColors.Danger)
                }
            }
            Spacer(Modifier.height(SfSpacing.xl))
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Wordmark()
                Text(stringResource(R.string.settings_version, version), style = SoundframeTheme.type.caption, color = SfColors.TextTertiary)
            }
        }
    }

    if (confirmDelete) {
        SfDialog(
            title = stringResource(R.string.settings_delete_title),
            onDismiss = { confirmDelete = false },
            confirmText = stringResource(R.string.settings_delete_confirm),
            destructive = true,
            onConfirm = {
                confirmDelete = false
                viewModel.deleteEverything()
            },
        ) {
            Text(
                stringResource(R.string.settings_delete_body),
                style = SoundframeTheme.type.body, color = SfColors.TextSecondary,
            )
        }
    }
}

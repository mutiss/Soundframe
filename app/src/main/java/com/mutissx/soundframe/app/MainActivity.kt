package com.mutissx.soundframe.app

import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.mutissx.soundframe.app.navigation.SoundframeNavHost
import com.mutissx.soundframe.core.designsystem.theme.SfColors
import com.mutissx.soundframe.core.designsystem.theme.SoundframeTheme
import com.mutissx.soundframe.domain.repository.PreferencesRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.koin.compose.viewmodel.koinViewModel

sealed interface MainUiState {
    data object Loading : MainUiState
    data class Ready(val onboardingCompleted: Boolean, val reduceMotion: Boolean) : MainUiState
}

class MainViewModel(preferences: PreferencesRepository) : ViewModel() {
    val state: StateFlow<MainUiState> = preferences.preferences
        .map { MainUiState.Ready(it.onboardingCompleted, it.reduceMotion) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, MainUiState.Loading)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        val systemReducedMotion = Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        setContent {
            val viewModel: MainViewModel = koinViewModel()
            val state by viewModel.state.collectAsStateWithLifecycle()
            when (val s = state) {
                MainUiState.Loading -> Box(Modifier.fillMaxSize().background(SfColors.Ink0))
                is MainUiState.Ready -> SoundframeTheme(reduceMotion = s.reduceMotion || systemReducedMotion) {
                    Box(Modifier.fillMaxSize().background(SfColors.Ink0)) {
                        SoundframeNavHost(startWithOnboarding = !s.onboardingCompleted)
                    }
                }
            }
        }
    }
}

package com.hapticks.app.features.edge

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.hapticks.app.features.main.HapticksApp
import com.hapticks.app.data.model.AppSettings
import com.hapticks.app.data.repository.SettingsRepository
import com.hapticks.app.core.haptics.HapticPattern
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class EdgeHapticsViewModel(
    application: Application,
    private val preferences: SettingsRepository,
) : AndroidViewModel(application) {

    sealed class TestEvent {
        object Fired : TestEvent()
        object NoVibrator : TestEvent()
    }

    val settings: StateFlow<AppSettings> = preferences.settings
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = AppSettings.Default,
        )

    private val _testEvent = MutableStateFlow<TestEvent?>(null)
    val testEvent: StateFlow<TestEvent?> = _testEvent.asStateFlow()

    fun setEdgePattern(pattern: HapticPattern) {
        viewModelScope.launch {
            preferences.setEdgePattern(pattern)
        }
    }

    fun setEdgeIntensity(intensity: Float) {
        viewModelScope.launch {
            preferences.setEdgeIntensity(intensity)
        }
    }

    fun setA11yScrollBoundEdge(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setA11yScrollBoundEdge(enabled)
        }
    }

    /** Edge waveform test (dedicated test button only). */
    fun testEdgeHaptic() {
        viewModelScope.launch {
            val result = withContext(Dispatchers.Default) {
                EdgeHapticEffectProvider.testEdgeHaptic(getApplication())
            }
            _testEvent.value = when (result) {
                EdgeHapticEffectProvider.TestResult.Fired -> TestEvent.Fired
                EdgeHapticEffectProvider.TestResult.NoVibrator -> TestEvent.NoVibrator
            }
        }
    }

    fun consumeTestEvent() {
        _testEvent.value = null
    }

    companion object {
        fun factory(application: Application): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val app = application as HapticksApp
                    return EdgeHapticsViewModel(app, app.preferences) as T
                }
            }
    }
}


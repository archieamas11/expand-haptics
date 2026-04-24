package com.hapticks.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.hapticks.app.HapticksApp
import com.hapticks.app.data.HapticsPreferences
import com.hapticks.app.data.HapticsSettings
import com.hapticks.app.edge.EdgeHapticsBridge
import com.hapticks.app.haptics.HapticPattern
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
    private val preferences: HapticsPreferences,
) : AndroidViewModel(application) {

    sealed class TestEvent {
        object Fired : TestEvent()
        object NoVibrator : TestEvent()
        data class Unavailable(val reason: EdgeHapticsBridge.AvailabilityStatus) : TestEvent()
    }

    val settings: StateFlow<HapticsSettings> = preferences.settings
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = HapticsSettings.Default,
        )

    private val _availability = MutableStateFlow(EdgeHapticsBridge.AvailabilityStatus.LSPOSED_INACTIVE)
    val availability: StateFlow<EdgeHapticsBridge.AvailabilityStatus> = _availability.asStateFlow()

    private val _testEvent = MutableStateFlow<TestEvent?>(null)
    val testEvent: StateFlow<TestEvent?> = _testEvent.asStateFlow()

    init {
        refreshAvailability()
        viewModelScope.launch(Dispatchers.IO) {
            EdgeHapticsBridge.syncXposedPrefs(getApplication())
        }
    }

    fun refreshAvailability() {
        viewModelScope.launch(Dispatchers.IO) {
            _availability.value = EdgeHapticsBridge.isAvailable(getApplication())
        }
    }

    fun setEdgeEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setEdgeEnabled(enabled)
            if (enabled) {
                EdgeHapticsBridge.enable(getApplication())
            } else {
                EdgeHapticsBridge.disable(getApplication())
            }
        }
    }

    fun setEdgePattern(pattern: HapticPattern) {
        viewModelScope.launch {
            EdgeHapticsBridge.updatePattern(getApplication(), pattern)
        }
    }

    fun setEdgeIntensity(intensity: Float) {
        viewModelScope.launch {
            EdgeHapticsBridge.updateIntensity(getApplication(), intensity)
        }
    }

    fun testEdgeHaptic() {
        viewModelScope.launch {
            val result = withContext(Dispatchers.Default) {
                EdgeHapticsBridge.testEdgeHaptic(getApplication())
            }
            _testEvent.value = when (result) {
                EdgeHapticsBridge.TestResult.Fired -> TestEvent.Fired
                EdgeHapticsBridge.TestResult.NoVibrator -> TestEvent.NoVibrator
                is EdgeHapticsBridge.TestResult.Unavailable -> TestEvent.Unavailable(result.reason)
            }
        }
    }

    fun consumeTestEvent() {
        _testEvent.value = null
    }

    companion object {
        fun factory(application: Application): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val app = application as HapticksApp
                return EdgeHapticsViewModel(app, app.preferences) as T
            }
        }
    }
}

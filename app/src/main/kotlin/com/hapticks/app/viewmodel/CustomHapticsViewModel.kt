package com.hapticks.app.viewmodel

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.hapticks.app.HapticksApp
import com.hapticks.app.data.HapticsPreferences
import com.hapticks.app.data.HapticsSettings
import com.hapticks.app.data.ThemeMode
import com.hapticks.app.haptics.HapticEngine
import com.hapticks.app.haptics.HapticPattern
import com.hapticks.app.service.HapticsAccessibilityService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * UI state holder for [com.hapticks.app.ui.screens.CustomHapticsScreen].
 *
 * Reads from the same DataStore the accessibility service uses, so any UI edit propagates to the
 * system-wide haptic bridge on the next accessibility event. The "service enabled" state is
 * derived by querying [AccessibilityManager] and must be refreshed whenever the activity resumes,
 * since the user can toggle it outside the app.
 */
class CustomHapticsViewModel(
    application: Application,
    private val preferences: HapticsPreferences,
    private val engine: HapticEngine,
) : AndroidViewModel(application) {

    val settings: StateFlow<HapticsSettings> = preferences.settings
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = HapticsSettings.Default,
        )

    private val _isServiceEnabled = MutableStateFlow(false)
    val isServiceEnabled: StateFlow<Boolean> = _isServiceEnabled.asStateFlow()

    init {
        refreshServiceState()
    }

    /** Must be invoked from the hosting activity's onResume. */
    fun refreshServiceState() {
        _isServiceEnabled.value = isAccessibilityServiceEnabled(getApplication())
    }

    fun setTapEnabled(enabled: Boolean) {
        viewModelScope.launch { preferences.setTapEnabled(enabled) }
    }

    /** Persist the final intensity value and fire a preview so the user can feel the level. */
    fun commitIntensity(intensity: Float) {
        viewModelScope.launch { preferences.setIntensity(intensity) }
        engine.play(settings.value.pattern, intensity)
    }

    fun setPattern(pattern: HapticPattern) {
        viewModelScope.launch { preferences.setPattern(pattern) }
        // Fire an immediate preview so selecting a pattern feels alive.
        engine.play(pattern, settings.value.intensity)
    }

    fun setScrollEnabled(enabled: Boolean) {
        viewModelScope.launch { preferences.setScrollEnabled(enabled) }
    }

    fun commitScrollIntensity(intensity: Float) {
        viewModelScope.launch { preferences.setScrollIntensity(intensity) }
        engine.play(settings.value.scrollPattern, intensity)
    }

    fun setScrollPattern(pattern: HapticPattern) {
        viewModelScope.launch { preferences.setScrollPattern(pattern) }
        engine.play(pattern, settings.value.scrollIntensity)
    }

    fun testScrollHaptic() {
        val s = settings.value
        engine.play(s.scrollPattern, s.scrollIntensity)
    }

    fun testHaptic() {
        val s = settings.value
        engine.play(s.pattern, s.intensity)
    }

    fun setUseDynamicColors(enabled: Boolean) {
        viewModelScope.launch { preferences.setUseDynamicColors(enabled) }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { preferences.setThemeMode(mode) }
    }

    fun setAmoledBlack(enabled: Boolean) {
        viewModelScope.launch { preferences.setAmoledBlack(enabled) }
    }

    fun setSeedColor(color: Int) {
        viewModelScope.launch { preferences.setSeedColor(color) }
    }

    companion object {
        private fun isAccessibilityServiceEnabled(context: Context): Boolean {
            val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE)
                as? AccessibilityManager ?: return false
            if (!manager.isEnabled) return false

            // Read the canonical secure-setting source so we do not depend on feedback-type filters.
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ) ?: return false

            val expectedComponent = ComponentName(
                context,
                HapticsAccessibilityService::class.java,
            ).flattenToString()

            return enabledServices
                .split(':')
                .any { serviceId ->
                    val normalized = serviceId.trim()
                    normalized.equals(expectedComponent, ignoreCase = true) ||
                        normalized.endsWith(HapticsAccessibilityService::class.java.name, ignoreCase = true)
                }
        }

        fun factory(application: Application): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val app = application as HapticksApp
                return CustomHapticsViewModel(app, app.preferences, app.hapticEngine) as T
            }
        }
    }
}

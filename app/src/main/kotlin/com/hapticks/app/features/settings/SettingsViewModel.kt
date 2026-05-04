package com.hapticks.app.features.settings

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.hapticks.app.core.haptics.HapticEngine
import com.hapticks.app.core.haptics.HapticPattern
import com.hapticks.app.data.model.AppSettings
import com.hapticks.app.data.model.ThemeMode
import com.hapticks.app.data.repository.SettingsRepository
import com.hapticks.app.features.main.HapticksApp
import com.hapticks.app.service.accessibility.HapticsAccessibilityService
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * UI state for the Feel Every Tap flow: tap toggle, pattern, and intensity shared with
 * [HapticsAccessibilityService] via [SettingsRepository].
 */
class SettingsViewModel(
    application: Application,
    private val preferences: SettingsRepository,
    private val engine: HapticEngine,
) : AndroidViewModel(application) {

    val settings: StateFlow<AppSettings> = preferences.settings
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = AppSettings.Default,
        )

    private val _isServiceEnabled = MutableStateFlow(value = false)
    val isServiceEnabled: StateFlow<Boolean> = _isServiceEnabled.asStateFlow()

    init {
        refreshServiceState()
    }

    fun refreshServiceState() {
        _isServiceEnabled.value = isAccessibilityServiceEnabled(getApplication())
    }

    fun setTapEnabled(enabled: Boolean) {
        viewModelScope.launch { preferences.setTapEnabled(enabled) }
    }

    fun setHapticsEnabled(enabled: Boolean) {
        viewModelScope.launch { preferences.setHapticsEnabled(enabled) }
    }

    fun setHasCompletedOnboarding(completed: Boolean) {
        viewModelScope.launch { preferences.setHasCompletedOnboarding(completed) }
    }

    fun commitIntensity(intensity: Float) {
        viewModelScope.launch { preferences.setIntensity(intensity) }
    }

    fun setPattern(pattern: HapticPattern) {
        viewModelScope.launch { preferences.setPattern(pattern) }
    }

    fun setScrollEnabled(enabled: Boolean) {
        viewModelScope.launch { preferences.setScrollEnabled(enabled) }
    }

    fun commitScrollIntensity(intensity: Float) {
        viewModelScope.launch { preferences.setScrollIntensity(intensity) }
    }

    fun commitScrollHapticDensity(eventsPerHundredPx: Float) {
        viewModelScope.launch { preferences.setScrollHapticEventsPerHundredPx(eventsPerHundredPx) }
    }

    fun setScrollPattern(pattern: HapticPattern) {
        viewModelScope.launch { preferences.setScrollPattern(pattern) }
    }

    fun setEdgePattern(pattern: HapticPattern) {
        viewModelScope.launch { preferences.setEdgePattern(pattern) }
    }

    fun setEdgeIntensity(intensity: Float) {
        viewModelScope.launch { preferences.setEdgeIntensity(intensity) }
    }

    fun setA11yScrollBoundEdge(enabled: Boolean) {
        viewModelScope.launch { preferences.setA11yScrollBoundEdge(enabled) }
    }

    /** Plays the configured tap pattern (for the dedicated test control only). */
    fun testHaptic() {
        val s = settings.value
        engine.play(s.pattern, s.intensity)
    }

    /** Plays a short burst of scroll haptics (for the dedicated test control only). */
    fun testScrollHaptic() {
        val s = settings.value
        viewModelScope.launch {
            val i = s.scrollIntensity
            engine.play(s.scrollPattern, i, 0L)
            delay(52)
            engine.play(s.scrollPattern, i, 0L)
            delay(52)
            engine.play(s.scrollPattern, i, 0L)
        }
    }

    /** Plays the configured edge pattern (for the dedicated test control only). */
    fun testEdgeHaptic() {
        val s = settings.value
        engine.play(s.edgePattern, s.edgeIntensity)
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

    fun setLiquidGlass(enabled: Boolean) {
        viewModelScope.launch { preferences.setLiquidGlass(enabled) }
    }

    fun setSeedColor(color: Int) {
        viewModelScope.launch { preferences.setSeedColor(color) }
    }

    fun setLastDismissedUpdateVersion(version: String?) {
        viewModelScope.launch { preferences.setLastDismissedUpdateVersion(version) }
    }

    companion object {
        private fun isAccessibilityServiceEnabled(context: Context): Boolean {
            val manager = (context.getSystemService(Context.ACCESSIBILITY_SERVICE)
                    as? AccessibilityManager) ?: return false
            if (!manager.isEnabled) return false

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
                            normalized.endsWith(
                                HapticsAccessibilityService::class.java.name,
                                ignoreCase = true,
                            )
                }
        }

        fun factory(application: Application): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val app = application as HapticksApp
                    return SettingsViewModel(app, app.preferences, app.hapticEngine) as T
                }
            }
    }
}


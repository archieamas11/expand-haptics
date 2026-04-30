package com.hapticks.app.data

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.hapticks.app.haptics.HapticPattern
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.hapticsDataStore: DataStore<Preferences> by preferencesDataStore(name = "hapticks")

class HapticsPreferences(context: Context) {

    private val dataStore = context.applicationContext.hapticsDataStore

    val settings: Flow<AppSettings> = dataStore.data
        .catch { throwable ->
            if (throwable is IOException) {
                Log.w(TAG, "DataStore read failed; falling back to defaults", throwable)
                emit(emptyPreferences())
            } else {
                throw throwable
            }
        }
        .map { prefs ->
            AppSettings(
                hapticsEnabled = prefs[Keys.HAPTICS_ENABLED] ?: AppSettings.Default.hapticsEnabled,
                tapEnabled = prefs[Keys.TAP_ENABLED] ?: AppSettings.Default.tapEnabled,
                hasCompletedOnboarding = prefs[Keys.HAS_COMPLETED_ONBOARDING] ?: AppSettings.Default.hasCompletedOnboarding,
                intensity = (prefs[Keys.INTENSITY] ?: AppSettings.Default.intensity)
                    .coerceIn(0f, 1f),
                pattern = HapticPattern.fromStorageKey(prefs[Keys.PATTERN]),
                scrollEnabled = prefs[Keys.SCROLL_ENABLED] ?: AppSettings.Default.scrollEnabled,
                scrollHapticEventsPerHundredPx = (prefs[Keys.SCROLL_HAPTIC_EVENTS_PER_HUNDRED_PX]
                    ?: AppSettings.Default.scrollHapticEventsPerHundredPx).coerceIn(
                    AppSettings.MIN_SCROLL_EVENTS_PER_HUNDRED_PX,
                    AppSettings.MAX_SCROLL_EVENTS_PER_HUNDRED_PX,
                ),
                scrollIntensity = (prefs[Keys.SCROLL_INTENSITY] ?: AppSettings.Default.scrollIntensity)
                    .coerceIn(0f, 1f),
                scrollPattern = HapticPattern.fromStorageKey(prefs[Keys.SCROLL_PATTERN])
                    .takeIf { prefs.contains(Keys.SCROLL_PATTERN) } ?: AppSettings.Default.scrollPattern,
                edgePattern = HapticPattern.fromStorageKey(prefs[Keys.EDGE_PATTERN])
                    .takeIf { prefs.contains(Keys.EDGE_PATTERN) } ?: AppSettings.Default.edgePattern,
                edgeIntensity = (prefs[Keys.EDGE_INTENSITY] ?: AppSettings.Default.edgeIntensity)
                    .coerceIn(0f, 1f),
                a11yScrollBoundEdge = prefs[Keys.A11Y_SCROLL_BOUND_EDGE] ?: AppSettings.Default.a11yScrollBoundEdge,
                edgeLsposedLibxposedPath = prefs[Keys.EDGE_LSPOSED_LIBXPOSED_PATH]
                    ?: AppSettings.Default.edgeLsposedLibxposedPath,
                useDynamicColors = prefs[Keys.USE_DYNAMIC_COLORS] ?: AppSettings.Default.useDynamicColors,
                themeMode = try {
                    ThemeMode.valueOf(prefs[Keys.THEME_MODE] ?: AppSettings.Default.themeMode.name)
                } catch (_: Exception) {
                    ThemeMode.SYSTEM
                },
                amoledBlack = prefs[Keys.AMOLED_BLACK] ?: AppSettings.Default.amoledBlack,
                liquidGlass = prefs[Keys.LIQUID_GLASS] ?: AppSettings.Default.liquidGlass,
                seedColor = prefs[Keys.SEED_COLOR] ?: AppSettings.Default.seedColor,
            )
        }

    suspend fun setTapEnabled(enabled: Boolean) = edit { it[Keys.TAP_ENABLED] = enabled }
    suspend fun setHapticsEnabled(enabled: Boolean) = edit { it[Keys.HAPTICS_ENABLED] = enabled }
    suspend fun setHasCompletedOnboarding(completed: Boolean) = edit { it[Keys.HAS_COMPLETED_ONBOARDING] = completed }
    suspend fun setIntensity(intensity: Float) = edit {
        it[Keys.INTENSITY] = intensity.coerceIn(0f, 1f)
    }
    suspend fun setPattern(pattern: HapticPattern) = edit { it[Keys.PATTERN] = pattern.name }
    suspend fun setScrollEnabled(enabled: Boolean) = edit { it[Keys.SCROLL_ENABLED] = enabled }
    suspend fun setScrollPattern(pattern: HapticPattern) = edit { it[Keys.SCROLL_PATTERN] = pattern.name }
    suspend fun setScrollIntensity(intensity: Float) = edit {
        it[Keys.SCROLL_INTENSITY] = intensity.coerceIn(0f, 1f)
    }
    suspend fun setScrollHapticEventsPerHundredPx(value: Float) = edit {
        it[Keys.SCROLL_HAPTIC_EVENTS_PER_HUNDRED_PX] = value.coerceIn(
            AppSettings.MIN_SCROLL_EVENTS_PER_HUNDRED_PX,
            AppSettings.MAX_SCROLL_EVENTS_PER_HUNDRED_PX,
        )
    }
    suspend fun setEdgePattern(pattern: HapticPattern) = edit { it[Keys.EDGE_PATTERN] = pattern.name }
    suspend fun setEdgeIntensity(intensity: Float) = edit {
        it[Keys.EDGE_INTENSITY] = intensity.coerceIn(0f, 1f)
    }
    suspend fun setA11yScrollBoundEdge(enabled: Boolean) = edit { it[Keys.A11Y_SCROLL_BOUND_EDGE] = enabled }

    suspend fun setEdgeLsposedLibxposedPath(enabled: Boolean) = edit { it[Keys.EDGE_LSPOSED_LIBXPOSED_PATH] = enabled }

    suspend fun setUseDynamicColors(enabled: Boolean) = edit { it[Keys.USE_DYNAMIC_COLORS] = enabled }
    suspend fun setThemeMode(mode: ThemeMode) = edit { it[Keys.THEME_MODE] = mode.name }
    suspend fun setAmoledBlack(enabled: Boolean) = edit { it[Keys.AMOLED_BLACK] = enabled }
    suspend fun setLiquidGlass(enabled: Boolean) = edit { it[Keys.LIQUID_GLASS] = enabled }
    suspend fun setSeedColor(color: Int) = edit { it[Keys.SEED_COLOR] = color }

    private suspend inline fun edit(crossinline block: (MutablePreferences) -> Unit) {
        try {
            dataStore.edit { block(it) }
        } catch (e: IOException) {
            Log.w(TAG, "DataStore write failed; change will not persist", e)
        }
    }

    private object Keys {
        val HAPTICS_ENABLED = booleanPreferencesKey("haptics_enabled")
        val TAP_ENABLED = booleanPreferencesKey("tap_enabled")
        val HAS_COMPLETED_ONBOARDING = booleanPreferencesKey("has_completed_onboarding")
        val INTENSITY = floatPreferencesKey("intensity")
        val PATTERN = stringPreferencesKey("pattern")
        val SCROLL_ENABLED = booleanPreferencesKey("scroll_enabled")
        val SCROLL_PATTERN = stringPreferencesKey("scroll_pattern")
        val SCROLL_HAPTIC_EVENTS_PER_HUNDRED_PX = floatPreferencesKey("scroll_haptic_events_per_hundred_px")
        val SCROLL_INTENSITY = floatPreferencesKey("scroll_intensity")
        val EDGE_PATTERN = stringPreferencesKey("edge_pattern")
        val EDGE_INTENSITY = floatPreferencesKey("edge_intensity")
        val A11Y_SCROLL_BOUND_EDGE = booleanPreferencesKey("a11y_scroll_bound_edge")
        val EDGE_LSPOSED_LIBXPOSED_PATH = booleanPreferencesKey("edge_lsposed_libxposed_path")
        val USE_DYNAMIC_COLORS = booleanPreferencesKey("use_dynamic_colors")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val AMOLED_BLACK = booleanPreferencesKey("amoled_black")
        val LIQUID_GLASS = booleanPreferencesKey("liquid_glass")
        val SEED_COLOR = intPreferencesKey("seed_color")
    }

    private companion object {
        const val TAG = "HapticsPrefs"
    }
}

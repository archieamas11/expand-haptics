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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.hapticsDataStore: DataStore<Preferences> by preferencesDataStore(name = "hapticks")

class HapticsPreferences(context: Context) {

    private val dataStore = context.applicationContext.hapticsDataStore

    val settings: Flow<HapticsSettings> = dataStore.data
        .catch { throwable ->
            if (throwable is IOException) {
                Log.w(TAG, "DataStore read failed; falling back to defaults", throwable)
                emit(emptyPreferences())
            } else {
                throw throwable
            }
        }
        .map { prefs ->
            HapticsSettings(
                tapEnabled = prefs[Keys.TAP_ENABLED] ?: HapticsSettings.Default.tapEnabled,
                intensity = (prefs[Keys.INTENSITY] ?: HapticsSettings.Default.intensity)
                    .coerceIn(0f, 1f),
                pattern = HapticPattern.fromStorageKey(prefs[Keys.PATTERN]),
                scrollEnabled = prefs[Keys.SCROLL_ENABLED] ?: HapticsSettings.Default.scrollEnabled,
                scrollIntensity = (prefs[Keys.SCROLL_INTENSITY] ?: HapticsSettings.Default.scrollIntensity)
                    .coerceIn(0f, 1f),
                scrollPattern = HapticPattern.fromStorageKey(prefs[Keys.SCROLL_PATTERN])
                    .takeIf { prefs.contains(Keys.SCROLL_PATTERN) } ?: HapticsSettings.Default.scrollPattern,
                edgeEnabled = prefs[Keys.EDGE_ENABLED] ?: HapticsSettings.Default.edgeEnabled,
                edgePattern = HapticPattern.fromStorageKey(prefs[Keys.EDGE_PATTERN])
                    .takeIf { prefs.contains(Keys.EDGE_PATTERN) } ?: HapticsSettings.Default.edgePattern,
                edgeIntensity = (prefs[Keys.EDGE_INTENSITY] ?: HapticsSettings.Default.edgeIntensity)
                    .coerceIn(0f, 1f),
                useDynamicColors = prefs[Keys.USE_DYNAMIC_COLORS] ?: HapticsSettings.Default.useDynamicColors,
                themeMode = try {
                    ThemeMode.valueOf(prefs[Keys.THEME_MODE] ?: HapticsSettings.Default.themeMode.name)
                } catch (_: Exception) {
                    ThemeMode.SYSTEM
                },
                seedColor = prefs[Keys.SEED_COLOR] ?: HapticsSettings.Default.seedColor,
            )
        }

    suspend fun setTapEnabled(enabled: Boolean) = edit { it[Keys.TAP_ENABLED] = enabled }
    suspend fun setIntensity(intensity: Float) = edit {
        it[Keys.INTENSITY] = intensity.coerceIn(0f, 1f)
    }
    suspend fun setPattern(pattern: HapticPattern) = edit { it[Keys.PATTERN] = pattern.name }
    suspend fun setScrollEnabled(enabled: Boolean) = edit { it[Keys.SCROLL_ENABLED] = enabled }
    suspend fun setScrollPattern(pattern: HapticPattern) = edit { it[Keys.SCROLL_PATTERN] = pattern.name }
    suspend fun setScrollIntensity(intensity: Float) = edit {
        it[Keys.SCROLL_INTENSITY] = intensity.coerceIn(0f, 1f)
    }
    suspend fun setEdgeEnabled(enabled: Boolean) = edit { it[Keys.EDGE_ENABLED] = enabled }
    suspend fun setEdgePattern(pattern: HapticPattern) = edit { it[Keys.EDGE_PATTERN] = pattern.name }
    suspend fun setEdgeIntensity(intensity: Float) = edit {
        it[Keys.EDGE_INTENSITY] = intensity.coerceIn(0f, 1f)
    }

    suspend fun setUseDynamicColors(enabled: Boolean) = edit { it[Keys.USE_DYNAMIC_COLORS] = enabled }
    suspend fun setThemeMode(mode: ThemeMode) = edit { it[Keys.THEME_MODE] = mode.name }
    suspend fun setSeedColor(color: Int) = edit { it[Keys.SEED_COLOR] = color }

    /**
     * Single-shot read of the edge settings. Used by [com.hapticks.app.edge.EdgeHapticReceiver].
     */
    suspend fun getEdgeSettingsOnce(): Triple<Boolean, HapticPattern, Float> = 
        settings.first().let { Triple(it.edgeEnabled, it.edgePattern, it.edgeIntensity) }

    private suspend inline fun edit(crossinline block: (MutablePreferences) -> Unit) {
        try {
            dataStore.edit { block(it) }
        } catch (e: IOException) {
            Log.w(TAG, "DataStore write failed; change will not persist", e)
        }
    }

    private object Keys {
        val TAP_ENABLED = booleanPreferencesKey("tap_enabled")
        val INTENSITY = floatPreferencesKey("intensity")
        val PATTERN = stringPreferencesKey("pattern")
        val SCROLL_ENABLED = booleanPreferencesKey("scroll_enabled")
        val SCROLL_PATTERN = stringPreferencesKey("scroll_pattern")
        val SCROLL_INTENSITY = floatPreferencesKey("scroll_intensity")
        val EDGE_ENABLED = booleanPreferencesKey("edge_enabled")
        val EDGE_PATTERN = stringPreferencesKey("edge_pattern")
        val EDGE_INTENSITY = floatPreferencesKey("edge_intensity")
        val USE_DYNAMIC_COLORS = booleanPreferencesKey("use_dynamic_colors")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val SEED_COLOR = intPreferencesKey("seed_color")
    }

    private companion object {
        const val TAG = "HapticsPrefs"
    }
}

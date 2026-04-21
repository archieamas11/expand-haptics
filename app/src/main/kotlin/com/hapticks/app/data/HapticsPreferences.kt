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
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.hapticks.app.haptics.HapticPattern
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.hapticsDataStore: DataStore<Preferences> by preferencesDataStore(name = "hapticks")

/**
 * Thin repository over DataStore preferences. Reads from the same store the accessibility
 * service uses, so UI changes are reflected in the service without any IPC glue.
 *
 * All reads recover gracefully from [IOException] (e.g. preferences file corruption) by
 * emitting an empty snapshot; that way a single read failure cannot kill the accessibility
 * service's collector.
 */
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
            )
        }

    suspend fun setTapEnabled(enabled: Boolean) = edit { it[Keys.TAP_ENABLED] = enabled }
    suspend fun setIntensity(intensity: Float) = edit {
        it[Keys.INTENSITY] = intensity.coerceIn(0f, 1f)
    }
    suspend fun setPattern(pattern: HapticPattern) = edit { it[Keys.PATTERN] = pattern.name }

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
    }

    private companion object {
        const val TAG = "HapticsPrefs"
    }
}

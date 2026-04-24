package com.hapticks.app.edge

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.VibrationAttributes
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.content.edit
import com.hapticks.app.HapticksApp
import com.hapticks.app.haptics.HapticPattern
import kotlinx.coroutines.flow.first
import java.io.File

/**
 * The process-local source of truth for "is the LSPosed module wired into this
 * device?". Runs entirely inside the Hapticks app process.
 *
 * Detection strategy (most authoritative first):
 *
 *  1. **Activation stub.** [isModuleActive] returns `false` by default; when
 *     LSPosed has loaded the Hapticks module into our own process,
 *     [EdgeScrollHooks.hookOwnActivationStub] rewrites that return value to
 *     `true`. This is the classic pattern every LSPosed module uses and it is
 *     the only signal that proves scope + module activation are both set up.
 *  2. **Runtime class probe.** If the hook never ran (e.g. the module's
 *     hook process-load happens after UI inflation) we fall back to probing
 *     for `de.robv.android.xposed.XposedBridge` on the app's class loader. On
 *     devices where LSPosed has scoped Hapticks, that class is live in our
 *     process. On stock devices it does not exist.
 *
 * The old code only checked (1) and only flipped the flag via the hook — so
 * any ordering hiccup made the UI incorrectly show "LSPosed not active". The
 * probe fallback keeps us honest on real devices without introducing false
 * positives on vanilla Android.
 */
object EdgeHapticsBridge {

    enum class AvailabilityStatus {
        READY,
        ROOT_MISSING,
        LSPOSED_INACTIVE,
    }

    sealed class TestResult {
        object Fired : TestResult()
        data class Unavailable(val reason: AvailabilityStatus) : TestResult()
        object NoVibrator : TestResult()
    }

    fun isAvailable(context: Context): AvailabilityStatus {
        val rooted = isDeviceRooted()
        val xposedActive = isXposedActive()
        val hasRuntimeConfig = hasPersistedRuntimeConfig(context)
        return when {
            xposedActive -> AvailabilityStatus.READY
            rooted && hasRuntimeConfig -> AvailabilityStatus.READY
            !rooted -> AvailabilityStatus.ROOT_MISSING
            else -> AvailabilityStatus.LSPOSED_INACTIVE
        }
    }

    /**
     * Hook target. LSPosed rewrites the return value to `true` as soon as the
     * module is loaded inside the Hapticks process. Kept as a regular method
     * (not `const`/inline) so Xposed can find and hook it.
     *
     * `@JvmStatic` so the compiled artifact is a plain static method on
     * [EdgeHapticsBridge] — LSPosed's `findAndHookMethod` binds to it without
     * having to walk the synthetic `INSTANCE` field first, which eliminates a
     * class of "hook failed: method not found" edge cases.
     */
    @JvmStatic
    fun isModuleActive(): Boolean = false

    @JvmStatic
    fun isXposedActive(): Boolean {
        if (isModuleActive()) return true
        return isXposedClassLoadable()
    }

    private fun isXposedClassLoadable(): Boolean {
        val loader = EdgeHapticsBridge::class.java.classLoader ?: return false
        for (name in XPOSED_PROBE_CLASSES) {
            try {
                Class.forName(name, false, loader)
                return true
            } catch (_: Throwable) {
                // try next candidate
            }
        }
        return false
    }

    private fun hasPersistedRuntimeConfig(context: Context): Boolean = runCatching {
        val prefs = context.applicationContext.getSharedPreferences(XPOSED_PREFS_NAME, Context.MODE_PRIVATE)
        prefs.contains(KEY_EDGE_ENABLED) &&
            prefs.contains(KEY_EDGE_PATTERN) &&
            prefs.contains(KEY_EDGE_INTENSITY)
    }.getOrDefault(false)

    fun isDeviceRooted(): Boolean {
        for (path in SU_PATHS) {
            if (File(path).exists()) return true
        }
        return false
    }

    private val XPOSED_PROBE_CLASSES = arrayOf(
        "de.robv.android.xposed.XposedBridge",
        "de.robv.android.xposed.XposedHelpers",
        "org.lsposed.lspd.core.ApplicationServiceClient",
        "io.github.libxposed.api.XposedInterface",
    )

    private val SU_PATHS = arrayOf(
        "/system/bin/su",
        "/system/xbin/su",
        "/sbin/su",
        "/system/sbin/su",
        "/vendor/bin/su",
        "/su/bin/su",
        "/system/bin/magisk",
        "/sbin/magisk",
    )

    const val XPOSED_PREFS_NAME = "hapticks_xposed"
    const val KEY_EDGE_ENABLED = "edge_enabled"
    const val KEY_EDGE_PATTERN = "edge_pattern"
    const val KEY_EDGE_INTENSITY = "edge_intensity"

    const val ACTION_SETTINGS_CHANGED = "com.hapticks.app.edge.SETTINGS_CHANGED"
    const val EXTRA_EDGE_ENABLED = "extra_edge_enabled"
    const val EXTRA_EDGE_PATTERN = "extra_edge_pattern"
    const val EXTRA_EDGE_INTENSITY = "extra_edge_intensity"

    suspend fun enable(context: Context) {
        val prefs = context.hapticks().preferences
        prefs.setEdgeEnabled(enabled = true)
        val s = prefs.settings.first()
        writeXposedSettings(context, true, s.edgePattern, s.edgeIntensity)
    }

    suspend fun disable(context: Context) {
        val prefs = context.hapticks().preferences
        prefs.setEdgeEnabled(false)
        val s = prefs.settings.first()
        writeXposedSettings(context, false, s.edgePattern, s.edgeIntensity)
    }

    suspend fun updatePattern(context: Context, pattern: HapticPattern) {
        val prefs = context.hapticks().preferences
        prefs.setEdgePattern(pattern)
        val s = prefs.settings.first()
        writeXposedSettings(context, s.edgeEnabled, pattern, s.edgeIntensity)
    }

    suspend fun updateIntensity(context: Context, intensity: Float) {
        val prefs = context.hapticks().preferences
        prefs.setEdgeIntensity(intensity)
        val s = prefs.settings.first()
        writeXposedSettings(context, s.edgeEnabled, s.edgePattern, intensity)
    }

    suspend fun syncXposedPrefs(context: Context) {
        try {
            val s = context.hapticks().preferences.settings.first()
            writeXposedSettings(context, s.edgeEnabled, s.edgePattern, s.edgeIntensity)
        } catch (t: Throwable) {
            Log.w(TAG, "syncXposedPrefs failed", t)
        }
    }

    @SuppressLint("WorldReadableFiles")
    private fun writeXposedSettings(
        context: Context,
        enabled: Boolean,
        pattern: HapticPattern,
        intensity: Float,
    ) {
        val app = context.applicationContext
        val persisted = runCatching {
            @Suppress("DEPRECATION")
            val prefs = app.getSharedPreferences(XPOSED_PREFS_NAME, Context.MODE_WORLD_READABLE)
            prefs.edit(commit = true) {
                putBoolean(KEY_EDGE_ENABLED, enabled)
                putString(KEY_EDGE_PATTERN, pattern.name)
                putFloat(KEY_EDGE_INTENSITY, intensity)
            }
            true
        }.getOrElse { false }

        if (!persisted) {
            try {
                val prefs = app.getSharedPreferences(XPOSED_PREFS_NAME, Context.MODE_PRIVATE)
                prefs.edit(commit = true) {
                    putBoolean(KEY_EDGE_ENABLED, enabled)
                    putString(KEY_EDGE_PATTERN, pattern.name)
                    putFloat(KEY_EDGE_INTENSITY, intensity)
                }
            } catch (t: Throwable) {
                Log.w(TAG, "failed to persist xposed flag", t)
                return
            }
        }

        try {
            val prefsFile = File(app.dataDir, "shared_prefs/$XPOSED_PREFS_NAME.xml")
            if (prefsFile.exists()) {
                @Suppress("SetWorldReadable")
                prefsFile.setReadable(true, false)
                prefsFile.parentFile?.setReadable(true, false)
                prefsFile.parentFile?.setExecutable(true, false)
            }
            @Suppress("SetWorldReadable")
            app.dataDir.setExecutable(true, false)
        } catch (t: Throwable) {
            Log.w(TAG, "failed to relax prefs permissions", t)
        }

        // Notify module in other processes
        context.sendBroadcast(Intent(ACTION_SETTINGS_CHANGED).apply {
            setPackage(null) // System-wide if possible, or we can target specific apps if we knew them.
            putExtra(EXTRA_EDGE_ENABLED, enabled)
            putExtra(EXTRA_EDGE_PATTERN, pattern.name)
            putExtra(EXTRA_EDGE_INTENSITY, intensity)
        })
    }

    fun testEdgeHaptic(context: Context): TestResult {
        val vibrator = resolveVibrator(context) ?: return TestResult.NoVibrator
        if (!vibrator.hasVibrator()) return TestResult.NoVibrator

        val attrs = VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH)
        return try {
            val hapticksApp = context.applicationContext as HapticksApp
            val s = kotlinx.coroutines.runBlocking {
                hapticksApp.preferences.settings.first()
            }
            vibrator.vibrate(EdgeVibrator.edgeEffect(s.edgePattern, s.edgeIntensity), attrs)
            TestResult.Fired
        } catch (_: Throwable) {
            TestResult.NoVibrator
        }
    }

    private fun resolveVibrator(context: Context): Vibrator? = try {
        val mgr = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        mgr?.defaultVibrator
    } catch (_: Throwable) {
        null
    }

    private fun Context.hapticks(): HapticksApp =
        applicationContext as HapticksApp

    private const val TAG = "HapticksEdgeBridge"
}

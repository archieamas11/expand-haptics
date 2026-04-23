package com.hapticks.app.edge

import android.annotation.SuppressLint
import android.content.Context
import android.os.VibrationAttributes
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import com.hapticks.app.HapticksApp
import com.hapticks.app.data.HapticsSettings
import com.hapticks.app.haptics.HapticPattern
import kotlinx.coroutines.flow.first
import java.io.File

/**
 * Single entry point from the Hapticks UI into the Edge Haptics feature. Intentionally
 * free of any direct reference to `de.robv.android.xposed.*` so the class compiles
 * cleanly even when the Xposed API JAR has not been vendored — the Xposed-specific
 * bits live exclusively in [EdgeScrollHooks].
 *
 * Availability is a three-step cascade:
 *  1. Does the device look rooted? (su / magisk on PATH). This is a best-effort
 *     signal we surface in the UI but do not gate on, because "Edge Haptics works"
 *     is ultimately determined by whether LSPosed has loaded our module.
 *  2. Is LSPosed active in *this* process? LSPosed loads enabled modules into their
 *     own app process too, so a successful `Class.forName` on [XPOSED_BRIDGE_CLASS]
 *     is an authoritative signal that the hook is live.
 *  3. Has the user toggled Edge Haptics on in [com.hapticks.app.data.HapticsSettings]?
 */
object EdgeHapticsBridge {

    /** Fully-qualified name of the Xposed class we probe via reflection. */
    private const val XPOSED_BRIDGE_CLASS = "de.robv.android.xposed.XposedBridge"

    enum class AvailabilityStatus {
        /** Hook is live in this process and ready to fire. */
        READY,

        /** No root / no su binary detected. Module cannot be activated. */
        ROOT_MISSING,

        /** Rooted, but LSPosed has not loaded our module into this process. */
        LSPOSED_INACTIVE,
    }

    sealed class TestResult {
        /** The receiver-side vibrator fired the edge pattern successfully. */
        object Fired : TestResult()

        /** The feature is currently unavailable; UI should surface [reason]. */
        data class Unavailable(val reason: AvailabilityStatus) : TestResult()

        /** Device has no vibrator hardware. */
        object NoVibrator : TestResult()
    }

    /**
     * Snapshot the current availability. Cheap (< 1 ms): a `Class.forName` plus a
     * handful of `File.exists()` checks. Cache externally if you call it at UI
     * render frequency.
     */
    fun isAvailable(): AvailabilityStatus {
        val rooted = isDeviceRooted()
        val xposedActive = isXposedActive()
        return when {
            xposedActive -> AvailabilityStatus.READY
            !rooted -> AvailabilityStatus.ROOT_MISSING
            else -> AvailabilityStatus.LSPOSED_INACTIVE
        }
    }

    /** True iff LSPosed has injected the Xposed bridge class into this process. */
    fun isXposedActive(): Boolean = try {
        Class.forName(XPOSED_BRIDGE_CLASS)
        true
    } catch (_: ClassNotFoundException) {
        false
    } catch (_: Throwable) {
        false
    }

    /**
     * Heuristic root probe: look for the standard `su` locations and the Magisk daemon
     * binary. Intentionally conservative; a missed detection just shows the user
     * "LSPosed inactive" instead of "root missing", which is still actionable.
     */
    fun isDeviceRooted(): Boolean {
        for (path in SU_PATHS) {
            if (File(path).exists()) return true
        }
        return false
    }

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

    /** Name of the SharedPreferences file readable cross-process by the LSPosed hook. */
    const val XPOSED_PREFS_NAME = "hapticks_xposed"

    /** Key inside [XPOSED_PREFS_NAME] that the hook reads via [android.content.SharedPreferences]. */
    const val KEY_EDGE_ENABLED = "edge_enabled"

    /** Key for the haptic pattern selection in [XPOSED_PREFS_NAME]. */
    const val KEY_EDGE_PATTERN = "edge_pattern"

    /** Key for the haptic intensity selection in [XPOSED_PREFS_NAME]. */
    const val KEY_EDGE_INTENSITY = "edge_intensity"

    /** Persist the master enable flag. Callers should do this off the main thread. */
    suspend fun enable(context: Context) {
        val prefs = context.hapticks().preferences
        prefs.setEdgeEnabled(true)
        val s = prefs.settings.first()
        writeXposedSettings(context, true, s.edgePattern, s.edgeIntensity)
    }

    suspend fun disable(context: Context) {
        val prefs = context.hapticks().preferences
        prefs.setEdgeEnabled(false)
        val s = prefs.settings.first()
        writeXposedSettings(context, false, s.edgePattern, s.edgeIntensity)
    }

    /** Update only the pattern. Callers should do this off the main thread. */
    suspend fun updatePattern(context: Context, pattern: HapticPattern) {
        val prefs = context.hapticks().preferences
        prefs.setEdgePattern(pattern)
        val s = prefs.settings.first()
        writeXposedSettings(context, s.edgeEnabled, pattern, s.edgeIntensity)
    }

    /** Update only the intensity. Callers should do this off the main thread. */
    suspend fun updateIntensity(context: Context, intensity: Float) {
        val prefs = context.hapticks().preferences
        prefs.setEdgeIntensity(intensity)
        val s = prefs.settings.first()
        writeXposedSettings(context, s.edgeEnabled, s.edgePattern, intensity)
    }

    /**
     * Mirror the settings into a classic SharedPreferences file and mark it world-readable so
     * `XSharedPreferences` in the LSPosed-loaded [EdgeScrollHooks] can read it from any
     * hooked process. This is the standard Xposed/LSPosed IPC shim for module settings.
     *
     * World-readable shared prefs are deprecated on N+ for third-party use, but LSPosed
     * explicitly supports this pattern and file-mode `0644` on the generated `.xml` is
     * what makes the cross-process read possible.
     */
    @SuppressLint("WorldReadableFiles")
    private fun writeXposedSettings(
        context: Context,
        enabled: Boolean,
        pattern: HapticPattern,
        intensity: Float,
    ) {
        try {
            val app = context.applicationContext
            @Suppress("DEPRECATION")
            val prefs = app.getSharedPreferences(XPOSED_PREFS_NAME, Context.MODE_WORLD_READABLE)
            prefs.edit()
                .putBoolean(KEY_EDGE_ENABLED, enabled)
                .putString(KEY_EDGE_PATTERN, pattern.name)
                .putFloat(KEY_EDGE_INTENSITY, intensity)
                .commit()
        } catch (se: SecurityException) {
            // Some OEM SELinux profiles refuse MODE_WORLD_READABLE on N+. Fall back to
            // writing via MODE_PRIVATE and chmod'ing the resulting file. Best-effort.
            try {
                val app = context.applicationContext
                val prefs = app.getSharedPreferences(XPOSED_PREFS_NAME, Context.MODE_PRIVATE)
                prefs.edit()
                    .putBoolean(KEY_EDGE_ENABLED, enabled)
                    .putString(KEY_EDGE_PATTERN, pattern.name)
                    .putFloat(KEY_EDGE_INTENSITY, intensity)
                    .commit()
                val prefsFile = File(
                    app.dataDir,
                    "shared_prefs/$XPOSED_PREFS_NAME.xml",
                )
                if (prefsFile.exists()) {
                    @Suppress("SetWorldReadable")
                    prefsFile.setReadable(true, false)
                    prefsFile.parentFile?.setReadable(true, false)
                    prefsFile.parentFile?.setExecutable(true, false)
                }
            } catch (t: Throwable) {
                Log.w(TAG, "failed to persist xposed flag", t)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "failed to persist xposed flag", t)
        }
    }

    /**
     * Fire the edge haptic pattern on demand so the user can preview the feel from
     * the Edge Haptics screen. When the feature is unavailable this returns a clear
     * status instead of silently no-op'ing; the UI can then render a hint.
     *
     * Unlike the hook-driven path, this call is made from *the Hapticks process*,
     * so it always has VIBRATE and does not need the broadcast fallback. We still
     * respect the availability cascade so the test button visibly mirrors what the
     * real feature will do once the user scrolls a list in another app.
     */
    fun testEdgeHaptic(context: Context): TestResult {
        val status = isAvailable()
        if (status != AvailabilityStatus.READY) {
            return TestResult.Unavailable(status)
        }
        val vibrator = resolveVibrator(context) ?: return TestResult.NoVibrator
        if (!vibrator.hasVibrator()) return TestResult.NoVibrator

        val attrs = VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH)
        return try {
            val hapticksApp = context.applicationContext as HapticksApp
            // Block on first() to get the current selected pattern for the test fire.
            val s = kotlinx.coroutines.runBlocking<HapticsSettings> {
                hapticksApp.preferences.settings.first()
            }
            vibrator.vibrate(EdgeVibrator.edgeEffect(context, s.edgePattern, s.edgeIntensity), attrs)
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

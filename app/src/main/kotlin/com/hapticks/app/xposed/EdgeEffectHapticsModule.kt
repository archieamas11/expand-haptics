package com.hapticks.app.xposed

import android.app.Application
import android.os.Handler
import android.os.Looper
import android.os.VibrationAttributes
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.widget.EdgeEffect
import com.hapticks.app.edge.EdgeHapticsBridge
import com.hapticks.app.haptics.HapticPattern
import io.github.libxposed.api.XposedInterface.ExceptionMode
import io.github.libxposed.api.XposedInterface.PROP_CAP_REMOTE
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import java.util.WeakHashMap

class EdgeEffectHapticsModule : XposedModule() {

    @Volatile private var cachedApp: Application? = null
    @Volatile private var cachedVibrator: Vibrator? = null

    private val hapticFiredMap = WeakHashMap<EdgeEffect, Boolean>()

    @Volatile private var enabled = false
    @Volatile private var cachedPattern: HapticPattern? = null
    @Volatile private var cachedIntensity = 1f

    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onModuleLoaded(param: ModuleLoadedParam) {
        super.onModuleLoaded(param)
        log(Log.INFO, TAG, "onModuleLoaded process=${param.processName}")
        if ((getFrameworkProperties() and PROP_CAP_REMOTE) == 0L) {
            log(Log.WARN, TAG, "PROP_CAP_REMOTE missing; remote prefs may be unavailable")
        }

        setupPreferenceListener()
        installEdgeHooks()
    }

    private fun setupPreferenceListener() {
        val prefs = try {
            getRemotePreferences(XposedEdgeRemotePrefs.GROUP)
        } catch (t: Throwable) {
            log(Log.WARN, TAG, "Cannot access remote preferences: ${t.message}")
            return
        }

        updateCachedPrefs(prefs)
        prefs.registerOnSharedPreferenceChangeListener { _, _ ->
            updateCachedPrefs(prefs)
        }
    }

    private fun updateCachedPrefs(prefs: android.content.SharedPreferences) {
        enabled = prefs.getBoolean(XposedEdgeRemotePrefs.KEY_ENABLED, false)
        cachedPattern = HapticPattern.fromStorageKey(
            prefs.getString(XposedEdgeRemotePrefs.KEY_PATTERN, null)
        )
        cachedIntensity = prefs.getFloat(XposedEdgeRemotePrefs.KEY_INTENSITY, 1f)
            .coerceIn(0f, 1f)
    }

    private fun installEdgeHooks() {
        try {
            val edge = EdgeEffect::class.java

            edge.constructors.forEach { ctor ->
                hook(ctor)
                    .setExceptionMode(ExceptionMode.PROTECTIVE)
                    .intercept { chain ->
                        chain.proceed()
                        val context = chain.args.firstOrNull() as? android.content.Context
                        if (context != null && cachedApp == null) {
                            synchronized(this) {
                                if (cachedApp == null) {
                                    cachedApp = context.applicationContext as? Application
                                }
                            }
                        }
                        null
                    }
            }

            val onPull = edge.getDeclaredMethod(
                "onPull",
                Float::class.javaPrimitiveType,
                Float::class.javaPrimitiveType,
            )
            hook(onPull)
                .setExceptionMode(ExceptionMode.PROTECTIVE)
                .intercept { chain ->
                    chain.proceed()
                    val effect = chain.thisObject as? EdgeEffect
                    if (effect != null) afterEdgePull(effect)
                    null
                }

            val onAbsorb = edge.getDeclaredMethod("onAbsorb", Int::class.javaPrimitiveType)
            hook(onAbsorb)
                .setExceptionMode(ExceptionMode.PROTECTIVE)
                .intercept { chain ->
                    chain.proceed()
                    val effect = chain.thisObject as? EdgeEffect
                    if (effect != null) afterEdgeAbsorb(effect)
                    null
                }

            val setDistance = edge.getDeclaredMethod("setDistance", Float::class.javaPrimitiveType)
            hook(setDistance)
                .setExceptionMode(ExceptionMode.PROTECTIVE)
                .intercept { chain ->
                    chain.proceed()
                    val effect = chain.thisObject as? EdgeEffect
                    if (effect != null && effect.distance == 0f) {
                        resetHapticFlag(effect)
                    }
                    null
                }

            try {
                val onRelease = edge.getDeclaredMethod("onRelease")
                hook(onRelease)
                    .setExceptionMode(ExceptionMode.PROTECTIVE)
                    .intercept { chain ->
                        chain.proceed()
                        val effect = chain.thisObject as? EdgeEffect
                        if (effect != null) resetHapticFlag(effect)
                        null
                    }
            } catch (_: NoSuchMethodException) {
                log(Log.INFO, TAG, "onRelease not available, relying on setDistance reset.")
            }
        } catch (t: Throwable) {
            log(Log.ERROR, TAG, "EdgeEffect hook installation failed", t)
        }
    }

    private fun afterEdgePull(effect: EdgeEffect) {
        if (!enabled || effect.distance <= 0f) return
        synchronized(hapticFiredMap) {
            if (hapticFiredMap[effect] != true) {
                hapticFiredMap[effect] = true
                triggerHaptic()
            }
        }
    }

    private fun afterEdgeAbsorb(effect: EdgeEffect) {
        if (!enabled) return
        triggerHaptic()
    }

    private fun resetHapticFlag(effect: EdgeEffect) {
        synchronized(hapticFiredMap) {
            hapticFiredMap[effect] = false
        }
    }

    private fun triggerHaptic() {
        val pattern = cachedPattern ?: return
        val intensity = cachedIntensity

        val vibrationEffect = try {
            EdgeHapticsBridge.edgeVibrationEffect(pattern, intensity)
        } catch (t: Throwable) {
            log(Log.DEBUG, TAG, "Failed to create vibration effect: ${t.message}")
            return
        }

        val vibrator = resolveVibrator() ?: return
        if (!vibrator.hasVibrator()) return

        val attrs = VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH)
        mainHandler.post {
            try {
                vibrator.vibrate(vibrationEffect, attrs)
            } catch (t: Throwable) {
                log(Log.DEBUG, TAG, "Vibrate failed: ${t.message}")
            }
        }
    }

    @Synchronized
    private fun resolveVibrator(): Vibrator? {
        if (cachedVibrator != null) return cachedVibrator
        val app = cachedApp ?: return null
        cachedVibrator = try {
            app.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } catch (_: Throwable) {
            null
        }
        return cachedVibrator
    }

    companion object {
        private const val TAG = "HapticksEdgeXposed"
    }
}
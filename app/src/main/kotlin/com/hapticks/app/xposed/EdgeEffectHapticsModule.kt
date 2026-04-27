package com.hapticks.app.xposed

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.os.SystemClock
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.widget.EdgeEffect
import com.hapticks.app.edge.EdgeHapticsBridge
import com.hapticks.app.haptics.HapticPattern
import io.github.libxposed.api.XposedInterface.ExceptionMode
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.lang.reflect.Field
import java.util.WeakHashMap

class EdgeEffectHapticsModule : XposedModule() {
    private val edgeStates = WeakHashMap<Any, EdgeState>()

    @Volatile private var enabled = false
    @Volatile private var cachedEffect: VibrationEffect? = null
    @Volatile private var isProcessGame = false
    @Volatile private var vibrator: Vibrator? = null
    @Volatile private var vibratorResolved = false

    @Volatile private var contextField: Field? = null
    @Volatile private var contextFieldResolved = false

    private val touchAttrs: VibrationAttributes by lazy {
        VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH)
    }

    override fun onModuleLoaded(param: ModuleLoadedParam) {
        super.onModuleLoaded(param)
        loadRemotePrefs()
    }

    override fun onPackageLoaded(param: PackageLoadedParam) {
        super.onPackageLoaded(param)
        if (param.isFirstPackage) {
            isProcessGame = isGame(param.applicationInfo)
            hookEdgeEffect()
        }
    }

    private fun loadRemotePrefs() {
        val prefs = runCatching { getRemotePreferences(XposedEdgeRemotePrefs.GROUP) }
            .getOrNull() ?: return
        applyPrefs(prefs)
        prefs.registerOnSharedPreferenceChangeListener { p, _ -> applyPrefs(p) }
    }

    private fun applyPrefs(prefs: SharedPreferences) {
        enabled = prefs.getBoolean(XposedEdgeRemotePrefs.KEY_ENABLED, false)
        val pattern = HapticPattern.fromStorageKey(
            prefs.getString(XposedEdgeRemotePrefs.KEY_PATTERN, null)
        )
        val intensity = prefs.getFloat(XposedEdgeRemotePrefs.KEY_INTENSITY, 1f)
            .coerceIn(0f, 1f)
        cachedEffect = runCatching {
            EdgeHapticsBridge.edgeVibrationEffect(
                pattern, intensity, EdgeHapticsBridge.EdgeVibrationEvent.EDGE_HIT
            )
        }.getOrNull()
    }

    private fun hookEdgeEffect() {
        val cls = EdgeEffect::class.java

        val hasPullDistance = runCatching {
            hookAfter(
                cls, "onPullDistance",
                Float::class.javaPrimitiveType, Float::class.javaPrimitiveType,
            ) { effect, args ->
                if ((args[0] as Float) > 0f) tryFire(effect)
            }
        }.isSuccess

        if (!hasPullDistance) {
            runCatching {
                hookAfter(cls, "onPull", Float::class.javaPrimitiveType) { effect, args ->
                    if ((args[0] as Float) > 0f) tryFire(effect)
                }
            }
            runCatching {
                hookAfter(
                    cls, "onPull",
                    Float::class.javaPrimitiveType, Float::class.javaPrimitiveType,
                ) { effect, args ->
                    if ((args[0] as Float) > 0f) tryFire(effect)
                }
            }
        }

        runCatching {
            hookAfter(cls, "onAbsorb", Int::class.javaPrimitiveType) { effect, _ ->
                tryFire(effect)
            }
        }

        runCatching {
            hookAfter(cls, "onRelease") { effect, _ ->
                synchronized(edgeStates) { edgeStates[effect]?.fired = false }
            }
        }
    }

    private fun tryFire(effect: Any) {
        if (!enabled || isProcessGame) return
        val vfx = cachedEffect ?: return

        val state = synchronized(edgeStates) { edgeStates.getOrPut(effect) { EdgeState() } }
        if (state.fired) return

        val now = SystemClock.uptimeMillis()
        if (now - state.lastFireMs < COOLDOWN_MS) return

        state.fired = true
        state.lastFireMs = now

        val v = ensureVibrator(effect) ?: return
        try {
            v.vibrate(vfx, touchAttrs)
        } catch (_: Throwable) {}
    }

    private fun ensureVibrator(effect: Any): Vibrator? {
        vibrator?.let { return it }
        if (vibratorResolved) return null
        synchronized(this) {
            vibrator?.let { return it }
            val ctx = resolveContext(effect) ?: run { vibratorResolved = true; return null }
            val v = try {
                ctx.getSystemService(Vibrator::class.java)
            } catch (_: Throwable) { null }
            vibratorResolved = true
            if (v != null && v.hasVibrator()) { vibrator = v; return v }
            return null
        }
    }

    @SuppressLint("DiscouragedPrivateApi")
    private fun resolveContext(instance: Any): Context? {
        resolveContextField(instance)
        contextField?.let { f ->
            return try { f.get(instance) as? Context } catch (_: Throwable) { fallbackContext() }
        }
        return fallbackContext()
    }

    private fun resolveContextField(instance: Any) {
        if (contextFieldResolved) return
        synchronized(this) {
            if (contextFieldResolved) return
            contextField = try {
                instance.javaClass.getDeclaredField("mContext").also { it.isAccessible = true }
            } catch (_: Throwable) { null }
            contextFieldResolved = true
        }
    }

    private fun fallbackContext(): Context? = try {
        val at = Class.forName("android.app.ActivityThread")
        at.getDeclaredMethod("currentApplication").invoke(null) as? Context
    } catch (_: Throwable) { null }

    private fun isGame(info: ApplicationInfo?): Boolean {
        if (info == null) return false
        @Suppress("DEPRECATION")
        return info.category == ApplicationInfo.CATEGORY_GAME ||
                (info.flags and ApplicationInfo.FLAG_IS_GAME) != 0
    }

    private inline fun hookAfter(
        cls: Class<*>, name: String, vararg paramTypes: Class<*>?,
        crossinline block: (thisObj: Any, args: List<Any?>) -> Unit,
    ) {
        val method = cls.getDeclaredMethod(name, *paramTypes)
        hook(method).setExceptionMode(ExceptionMode.PROTECTIVE).intercept { chain ->
            val result = chain.proceed()
            try { block(chain.thisObject, chain.args) } catch (_: Throwable) {}
            result
        }
    }

    private class EdgeState {
        @Volatile var fired = false
        @Volatile var lastFireMs = 0L
    }

    private companion object {
        const val COOLDOWN_MS = 150L
    }
}
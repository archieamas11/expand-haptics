package com.hapticks.app.xposed

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.os.SystemClock
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.View
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
    private val viewStates = WeakHashMap<View, BoundaryState>()
    private val edgeEffectStates = WeakHashMap<Any, BoundaryState>()

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
            hookOverScrollBy()
            hookRecyclerView(param.defaultClassLoader)
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

    private fun hookOverScrollBy() {
        runCatching {
            val method = View::class.java.getDeclaredMethod(
                "overScrollBy",
                Int::class.javaPrimitiveType,   // deltaX
                Int::class.javaPrimitiveType,   // deltaY
                Int::class.javaPrimitiveType,   // scrollX
                Int::class.javaPrimitiveType,   // scrollY
                Int::class.javaPrimitiveType,   // scrollRangeX
                Int::class.javaPrimitiveType,   // scrollRangeY
                Int::class.javaPrimitiveType,   // maxOverScrollX
                Int::class.javaPrimitiveType,   // maxOverScrollY
                Boolean::class.javaPrimitiveType, // isTouchEvent
            )
            hook(method).setExceptionMode(ExceptionMode.PROTECTIVE).intercept { chain ->
                val result = chain.proceed()
                try {
                    val clamped = result as? Boolean == true
                    if (clamped) {
                        val view = chain.thisObject as? View
                        if (view != null) {
                            val deltaY = chain.args[1] as? Int ?: 0
                            val scrollY = chain.args[3] as? Int ?: 0
                            val scrollRangeY = chain.args[5] as? Int ?: 0
                            onViewBoundaryClamped(view, deltaY, scrollY, scrollRangeY)
                        }
                    }
                } catch (_: Throwable) {}
                result
            }
        }
    }

    private fun onViewBoundaryClamped(
        view: View,
        deltaY: Int,
        scrollY: Int,
        scrollRangeY: Int,
    ) {
        val edge = when {
            deltaY < 0 && scrollY <= 0 -> Edge.TOP
            deltaY > 0 && scrollY >= scrollRangeY -> Edge.BOTTOM
            else -> return
        }
        tryFireForView(view, edge)
    }

    private fun hookRecyclerView(classLoader: ClassLoader) {
        val rvClass = runCatching {
            classLoader.loadClass("androidx.recyclerview.widget.RecyclerView")
        }.getOrNull() ?: return
        runCatching {
            val scrollByInternal = rvClass.getDeclaredMethod(
                "scrollByInternal",
                Int::class.javaPrimitiveType,       // x
                Int::class.javaPrimitiveType,       // y
                android.view.MotionEvent::class.java, // ev
                Int::class.javaPrimitiveType,       // type
            )
            hook(scrollByInternal).setExceptionMode(ExceptionMode.PROTECTIVE).intercept { chain ->
                val result = chain.proceed()
                try {
                    val view = chain.thisObject as? View ?: return@intercept result
                    val dy = chain.args[1] as? Int ?: 0
                    if (dy != 0) {
                        val edge = when {
                            dy < 0 && !(view as Any).callCanScrollVertically(-1) -> Edge.TOP
                            dy > 0 && !(view as Any).callCanScrollVertically(1) -> Edge.BOTTOM
                            else -> null
                        }
                        if (edge != null) tryFireForView(view, edge)
                    }
                } catch (_: Throwable) {}
                result
            }
        }
        runCatching {
            val onScrolled = rvClass.getDeclaredMethod(
                "onScrolled",
                Int::class.javaPrimitiveType, // dx
                Int::class.javaPrimitiveType, // dy
            )
            hook(onScrolled).setExceptionMode(ExceptionMode.PROTECTIVE).intercept { chain ->
                val result = chain.proceed()
                try {
                    val view = chain.thisObject as? View ?: return@intercept result
                    val dy = chain.args[1] as? Int ?: 0
                    if (dy != 0) {
                        val edge = when {
                            dy < 0 && !(view as Any).callCanScrollVertically(-1) -> Edge.TOP
                            dy > 0 && !(view as Any).callCanScrollVertically(1) -> Edge.BOTTOM
                            else -> null
                        }
                        if (edge != null) tryFireForView(view, edge)
                    }
                } catch (_: Throwable) {}
                result
            }
        }
    }

    private fun Any.callCanScrollVertically(direction: Int): Boolean {
        return try {
            (this as View).canScrollVertically(direction)
        } catch (_: Throwable) { true }
    }

    private fun hookEdgeEffect() {
        val cls = EdgeEffect::class.java

        val hasPullDistance = runCatching {
            hookAfter(
                cls, "onPullDistance",
                Float::class.javaPrimitiveType, Float::class.javaPrimitiveType,
            ) { effect, _ ->
                tryFireForEdgeEffect(effect)
            }
        }.isSuccess

        if (!hasPullDistance) {
            runCatching {
                hookAfter(cls, "onPull", Float::class.javaPrimitiveType) { effect, _ ->
                    tryFireForEdgeEffect(effect)
                }
            }
            runCatching {
                hookAfter(
                    cls, "onPull",
                    Float::class.javaPrimitiveType, Float::class.javaPrimitiveType,
                ) { effect, _ ->
                    tryFireForEdgeEffect(effect)
                }
            }
        }

        runCatching {
            hookAfter(cls, "onAbsorb", Int::class.javaPrimitiveType) { effect, _ ->
                tryFireForEdgeEffect(effect)
            }
        }

        runCatching {
            hookAfter(cls, "onRelease") { effect, _ ->
                synchronized(edgeEffectStates) { edgeEffectStates[effect]?.reset() }
            }
        }
    }

    private fun tryFireForView(view: View, edge: Edge) {
        if (!enabled || isProcessGame) return
        val vfx = cachedEffect ?: return

        val state = synchronized(viewStates) {
            viewStates.getOrPut(view) { BoundaryState() }
        }

        val alreadyFired = when (edge) {
            Edge.TOP -> state.topFired
            Edge.BOTTOM -> state.bottomFired
        }
        if (alreadyFired) return

        val now = SystemClock.uptimeMillis()
        if (now - state.lastFireMs < COOLDOWN_MS) return

        when (edge) {
            Edge.TOP -> { state.topFired = true; state.bottomFired = false }
            Edge.BOTTOM -> { state.bottomFired = true; state.topFired = false }
        }
        state.lastFireMs = now

        vibrate(vfx, view)
    }

    private fun tryFireForEdgeEffect(effect: Any) {
        if (!enabled || isProcessGame) return
        val vfx = cachedEffect ?: return

        val state = synchronized(edgeEffectStates) {
            edgeEffectStates.getOrPut(effect) { BoundaryState() }
        }
        if (state.topFired) return

        val now = SystemClock.uptimeMillis()
        if (now - state.lastFireMs < COOLDOWN_MS) return

        state.topFired = true
        state.lastFireMs = now

        vibrate(vfx, effect)
    }

    private fun vibrate(vfx: VibrationEffect, contextSource: Any) {
        val v = ensureVibrator(contextSource) ?: return
        try {
            v.vibrate(vfx, touchAttrs)
        } catch (_: Throwable) {}
    }

    private fun ensureVibrator(instance: Any): Vibrator? {
        vibrator?.let { return it }
        if (vibratorResolved) return null
        synchronized(this) {
            vibrator?.let { return it }
            val ctx = resolveContext(instance)
                ?: run { vibratorResolved = true; return null }
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
        if (instance is View) {
            return try { instance.context } catch (_: Throwable) { fallbackContext() }
        }
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

    private enum class Edge { TOP, BOTTOM }
    private class BoundaryState {
        @Volatile var topFired = false
        @Volatile var bottomFired = false
        @Volatile var lastFireMs = 0L

        fun reset() {
            topFired = false
            bottomFired = false
        }
    }

    private companion object {
        /**
         * Minimum time between successive haptic fires for the same target.
         * Short enough for rapid flick-release-flick to feel responsive;
         * long enough to prevent vibrator API flooding.
         */
        const val COOLDOWN_MS = 80L
    }
}
package com.hapticks.app.edge

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.Build
import android.os.SystemClock
import android.view.View
import android.webkit.WebView
import com.hapticks.app.haptics.HapticPattern
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import java.lang.reflect.Method
import java.util.concurrent.atomic.AtomicBoolean

class EdgeScrollHooks : XposedModule() {

    @Volatile private var enabled: Boolean = false
    @Volatile private var pattern: HapticPattern = HapticPattern.Default
    @Volatile private var intensity: Float = 1.0f
    @Volatile private var lastPrefCheckMs: Long = 0L
    @Volatile private var remotePrefs: SharedPreferences? = null

    // AtomicBoolean ensures the check-and-set is atomic, preventing a race condition
    // where two threads both see `false` and both try to register the receiver.
    private val isSettingsReceiverRegistered = AtomicBoolean(false)

    @Volatile private var lastObservedPullEdge: Edge = Edge.BOTTOM

    private val edgeController = EdgeHapticController(
        isEnabled = ::isEnabled,
        minPullThreshold = PULL_THRESHOLD,
        cooldownMs = EDGE_COOLDOWN_MS,
    )

    override fun onPackageReady(param: XposedModuleInterface.PackageReadyParam) {
        try {
            if (param.packageName == OWN_PACKAGE) {
                hookOwnActivationStub(param)
                return
            }

            initRemotePrefs()
            ensureSettingsReceiverRegistered()
            hookRecyclerView(param)
            hookWebView(param)
            hookEdgeEffect(param)
        } catch (t: Throwable) {
            log("$TAG: handleLoadPackage failed for ${param.packageName}: $t")
        }
    }

    private fun hookOwnActivationStub(param: XposedModuleInterface.PackageReadyParam) {
        try {
            val bridgeClass = param.classLoader.loadClass("com.hapticks.app.edge.EdgeHapticsBridge")
            val method = bridgeClass.getDeclaredMethod("isModuleActive")
            hookMethod(method, object : MethodHooker() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    param.result = true
                }
            })
            log("$TAG: activation stub hooked in own process")
        } catch (t: Throwable) {
            log("$TAG: activation stub hook failed: ${t.message}")
        }
    }

    private fun initRemotePrefs() {
        if (remotePrefs != null) return
        try {
            remotePrefs = getRemotePreferences(EdgeHapticsBridge.XPOSED_PREFS_NAME)
            refreshPrefs()
        } catch (t: Throwable) {
            log("$TAG: remote prefs init failed: ${t.message}")
        }
    }

    private fun isEnabled(): Boolean {
        // onPackageReady can run before ActivityThread exposes an app context in some
        // processes; retrying here ensures we eventually subscribe to live settings
        // updates instead of staying pinned to the boot/default pattern forever.
        if (!isSettingsReceiverRegistered.get()) ensureSettingsReceiverRegistered()
        val now = SystemClock.uptimeMillis()
        if (now - lastPrefCheckMs > PREF_TTL_MS) refreshPrefs()
        return enabled
    }

    private fun ensureSettingsReceiverRegistered() {
        // compareAndSet(false, true) is atomic: only one thread will proceed past this point.
        if (!isSettingsReceiverRegistered.compareAndSet(false, true)) return

        val appContext = currentAppContext()
        if (appContext == null) {
            // Context not yet available — reset so we retry on next isEnabled() call.
            isSettingsReceiverRegistered.set(false)
            return
        }

        val filter = IntentFilter(EdgeHapticsBridge.ACTION_SETTINGS_CHANGED)
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                appContext.registerReceiver(
                    settingsReceiver,
                    filter,
                    Context.RECEIVER_EXPORTED
                )
            } else {
                @Suppress("DEPRECATION")
                appContext.registerReceiver(settingsReceiver, filter)
            }
            log("$TAG: settings receiver registered")
        }.onFailure { t ->
            // Registration failed — reset so we can retry.
            isSettingsReceiverRegistered.set(false)
            log("$TAG: settings receiver registration failed: ${t.message}")
        }
    }

    private fun refreshPrefs() {
        val prefs = remotePrefs ?: run {
            initRemotePrefs()
            remotePrefs
        } ?: run {
            // Fail closed: if we cannot read shared prefs in a hooked process, do not vibrate.
            enabled = false
            edgeController.onReleaseAll()
            return
        }
        try {
            enabled = if (prefs.contains(EdgeHapticsBridge.KEY_EDGE_ENABLED)) {
                prefs.getBoolean(EdgeHapticsBridge.KEY_EDGE_ENABLED, false)
            } else {
                false
            }
            if (prefs.contains(EdgeHapticsBridge.KEY_EDGE_PATTERN)) {
                val patternName = prefs.getString(EdgeHapticsBridge.KEY_EDGE_PATTERN, null)
                pattern = HapticPattern.fromStorageKey(patternName)
            }
            if (prefs.contains(EdgeHapticsBridge.KEY_EDGE_INTENSITY)) {
                intensity = prefs.getFloat(EdgeHapticsBridge.KEY_EDGE_INTENSITY, intensity)
                    .coerceIn(0f, 1f)
            }
            lastPrefCheckMs = SystemClock.uptimeMillis()
            if (!enabled) edgeController.onReleaseAll()
        } catch (t: Throwable) {
            log("$TAG: prefs reload failed: ${t.message}")
            enabled = false
            edgeController.onReleaseAll()
        }
    }

    private val settingsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != EdgeHapticsBridge.ACTION_SETTINGS_CHANGED) return
            val hasEnabled = intent.hasExtra(EdgeHapticsBridge.EXTRA_EDGE_ENABLED)
            val hasPattern = intent.hasExtra(EdgeHapticsBridge.EXTRA_EDGE_PATTERN)
            val hasIntensity = intent.hasExtra(EdgeHapticsBridge.EXTRA_EDGE_INTENSITY)
            if (!hasEnabled && !hasPattern && !hasIntensity) {
                refreshPrefs()
                return
            }

            if (hasEnabled) {
                enabled = intent.getBooleanExtra(EdgeHapticsBridge.EXTRA_EDGE_ENABLED, enabled)
            }
            val patternName = intent.getStringExtra(EdgeHapticsBridge.EXTRA_EDGE_PATTERN)
            if (!patternName.isNullOrBlank()) {
                pattern = HapticPattern.fromStorageKey(patternName)
            }
            if (hasIntensity) {
                val newIntensity = intent.getFloatExtra(EdgeHapticsBridge.EXTRA_EDGE_INTENSITY, intensity)
                intensity = newIntensity.coerceIn(0f, 1f)
            }
            lastPrefCheckMs = SystemClock.uptimeMillis()
            if (!enabled) edgeController.onReleaseAll()
        }
    }

    private fun hookRecyclerView(param: XposedModuleInterface.PackageReadyParam) {
        for (className in RV_CLASS_CANDIDATES) {
            val rvClass = try {
                param.classLoader.loadClass(className)
            } catch (_: ClassNotFoundException) {
                continue
            }
            try {
                val method = rvClass.getDeclaredMethod(
                    "dispatchOnScrollStateChanged",
                    Int::class.javaPrimitiveType
                )
                hookMethod(method, object : MethodHooker() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (!isEnabled()) return
                        val state = param.args[0] as? Int ?: return
                        if (state != STATE_IDLE) return
                        val rv = param.thisObject as? View ?: return
                        val atTop = !rv.canScrollVertically(-1)
                        val atBottom = !rv.canScrollVertically(1)
                        dispatchFallback(rv.context, atTop, atBottom)
                    }
                })
                log("$TAG: hooked $className in ${param.packageName}")
                return
            } catch (t: Throwable) {
                log("$TAG: RecyclerView hook failed on $className: ${t.message}")
            }
        }
    }

    private fun findScrollChangedMethod(viewClass: Class<*>): Method {
        return viewClass.getDeclaredMethod(
            "onScrollChanged",
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType
        )
    }

    private fun hookWebView(param: XposedModuleInterface.PackageReadyParam) {
        try {
            val method = findScrollChangedMethod(WebView::class.java)
            hookMethod(method, webViewHook)
        } catch (t: Throwable) {
            log("$TAG: WebView hook failed: ${t.message}")
        }
    }

    private val webViewHook = object : MethodHooker() {
        override fun afterHookedMethod(param: MethodHookParam) {
            if (!isEnabled()) return
            val wv = param.thisObject as? WebView ?: return
            val scrollY = param.args[1] as? Int ?: wv.scrollY
            val scale = try {
                @Suppress("DEPRECATION")
                wv.scale
            } catch (_: Throwable) {
                1.0f
            }
            val contentH = (wv.contentHeight * scale).toInt()
            val viewportH = wv.height
            val atTop = scrollY <= 0
            val atBottom = contentH > 0 && scrollY + viewportH >= contentH
            dispatchFallback(wv.context, atTop, atBottom)
        }
    }

    private fun dispatchFallback(context: Context, atTop: Boolean, atBottom: Boolean) {
        val now = SystemClock.uptimeMillis()
        if (atTop) {
            edgeController.onFallback(Edge.TOP, now)?.let { playDispatch(context, it) }
        } else {
            edgeController.onRelease(Edge.TOP)
        }

        if (atBottom) {
            edgeController.onFallback(Edge.BOTTOM, now)?.let { playDispatch(context, it) }
        } else {
            edgeController.onRelease(Edge.BOTTOM)
        }
    }

    private fun hookEdgeEffect(param: XposedModuleInterface.PackageReadyParam) {
        val cls = try {
            param.classLoader.loadClass("android.widget.EdgeEffect")
        } catch (_: ClassNotFoundException) {
            return
        }

        // Hook onAbsorb
        try {
            val absorbMethod = cls.getDeclaredMethod("onAbsorb", Int::class.javaPrimitiveType)
            hookMethod(absorbMethod, object : MethodHooker() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val velocity = param.args.firstOrNull() as? Int ?: 0
                    val now = SystemClock.uptimeMillis()
                    val dispatch = edgeController.onAbsorb(lastObservedPullEdge, velocity, now) ?: return
                    val ctx = currentAppContext() ?: return
                    playDispatch(ctx, dispatch)
                }
            })
            log("$TAG: hooked EdgeEffect.onAbsorb in ${param.packageName}")
        } catch (t: Throwable) {
            log("$TAG: EdgeEffect.onAbsorb hook failed: ${t.message}")
        }

        // Hook onPull (two signatures)
        val onPullHook = object : MethodHooker() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val deltaDistance = param.args.firstOrNull() as? Float ?: 0f
                val edge = if (deltaDistance < 0f) Edge.TOP else Edge.BOTTOM
                lastObservedPullEdge = edge
                val now = SystemClock.uptimeMillis()
                val dispatch = edgeController.onPull(edge, deltaDistance, now) ?: return
                val ctx = currentAppContext() ?: return
                playDispatch(ctx, dispatch)
            }
        }

        try {
            val pullMethod1 = cls.getDeclaredMethod(
                "onPull",
                Float::class.javaPrimitiveType,
                Float::class.javaPrimitiveType
            )
            hookMethod(pullMethod1, onPullHook)
        } catch (_: Throwable) { /* ignore */ }

        try {
            val pullMethod2 = cls.getDeclaredMethod(
                "onPull",
                Float::class.javaPrimitiveType
            )
            hookMethod(pullMethod2, onPullHook)
        } catch (_: Throwable) { /* ignore */ }

        try {
            val releaseMethod = cls.getDeclaredMethod("onRelease")
            hookMethod(releaseMethod, object : MethodHooker() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    edgeController.onReleaseAll()
                }
            })
            log("$TAG: hooked EdgeEffect.onRelease in ${param.packageName}")
        } catch (t: Throwable) {
            log("$TAG: EdgeEffect.onRelease hook failed: ${t.message}")
        }
    }

    private fun playDispatch(context: Context, dispatch: EdgeHapticDispatch) {
        if (!isEnabled()) return
        EdgeVibrator.play(
            context = context,
            edge = dispatch.edge,
            pattern = pattern,
            intensity = intensity,
            kind = dispatch.kind,
            intensityScale = dispatch.intensityScale,
        )
    }

    private fun currentAppContext(): Context? = try {
        val at = Class.forName("android.app.ActivityThread")
        at.getMethod("currentApplication").invoke(null) as? Context
    } catch (_: Throwable) {
        null
    }

    private fun hookMethod(method: Method, hooker: MethodHooker) {
        hook(method).intercept(hooker)
    }

    private abstract class MethodHooker : XposedInterface.Hooker {
        override fun intercept(chain: XposedInterface.Chain): Any? {
            val param = MethodHookParam(chain)
            beforeHookedMethod(param)
            if (param.returnEarly) return param.result
            val result = try {
                chain.proceed()
            } catch (t: Throwable) {
                param.throwable = t
                afterHookedMethod(param)
                throw t
            }
            param.result = result
            afterHookedMethod(param)
            return param.result
        }

        open fun beforeHookedMethod(param: MethodHookParam) {}
        open fun afterHookedMethod(param: MethodHookParam) {}
    }

    private class MethodHookParam(private val chain: XposedInterface.Chain) {
        val args: List<Any?> get() = chain.args
        val thisObject: Any? get() = chain.thisObject
        var result: Any? = null
            set(value) {
                field = value
                returnEarly = true
            }
        var throwable: Throwable? = null
        var returnEarly: Boolean = false
    }

    private fun log(message: String) {
        log(4, TAG, message.removePrefix("$TAG: "))
    }

    private companion object {
        const val TAG = "HapticksEdgeHooks"
        const val OWN_PACKAGE = "com.hapticks.app"

        const val PREF_TTL_MS = 2_000L
        const val STATE_IDLE = 0
        const val PULL_THRESHOLD = 0.015f
        const val EDGE_COOLDOWN_MS = 60L

        val RV_CLASS_CANDIDATES = arrayOf(
            "androidx.recyclerview.widget.RecyclerView",
            "android.support.v7.widget.RecyclerView"
        )
    }
}

package com.hapticks.app.edge

import android.content.Context
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.ScrollView
import com.hapticks.app.haptics.HapticPattern
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XSharedPreferences
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * LSPosed module entry point. Declared in `assets/xposed_init`.
 *
 * Responsibilities:
 *  - Skip our own package so Hapticks does not hook itself.
 *  - Load the cross-process enable flag via [XSharedPreferences] with a 5 s TTL refresh,
 *    so toggling the Edge Haptics switch in the Hapticks UI takes effect in already-
 *    running hooked apps within a few seconds instead of requiring a restart.
 *  - Install the three hook families required by the spec:
 *      1. `RecyclerView.dispatchOnScrollStateChanged(int)`: the central chokepoint every
 *         scroll-state transition flows through (functionally equivalent to attaching a
 *         global `OnScrollListener` via `setAdapter`, but without needing to subclass the
 *         listener's abstract class). On `SCROLL_STATE_IDLE`, inspect
 *         `canScrollVertically(-1/+1)` to detect the exact top/bottom edge.
 *      2. `ScrollView.onScrollChanged` & `NestedScrollView.onScrollChanged`: compare
 *         `scrollY` against `0` and `scrollY + height` against the child's total height.
 *      3. `WebView.onScrollChanged`: compare `scrollY` against `contentHeight * scale`.
 *
 * All three converge on [EdgeDetector.Shared] and [EdgeVibrator.play], so the 500 ms
 * debounce and `TOP <-> BOTTOM` transition logic are consistent regardless of which
 * hook fired.
 *
 * This class is the only file in the module that references `de.robv.android.xposed.*`.
 * When the Xposed API JAR is not vendored, the Gradle build excludes this source file
 * from compilation (see `app/build.gradle.kts`) and the rest of the app is unaffected.
 */
class EdgeScrollHooks : IXposedHookLoadPackage {

    @Volatile private var enabled: Boolean = true
    @Volatile private var pattern: HapticPattern = HapticPattern.TICK
    @Volatile private var intensity: Float = 1.0f
    @Volatile private var lastPrefCheckMs: Long = 0L
    private lateinit var sharedPrefs: XSharedPreferences

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName == OWN_PACKAGE) return

        initSharedPrefs()
        if (!isEnabled()) {
            XposedBridge.log("$TAG: edge haptics disabled by user, skipping ${lpparam.packageName}")
            return
        }

        hookRecyclerView(lpparam)
        hookScrollView(lpparam)
        hookNestedScrollView(lpparam)
        hookWebView(lpparam)
    }

    private fun initSharedPrefs() {
        sharedPrefs = XSharedPreferences(OWN_PACKAGE, EdgeHapticsBridge.XPOSED_PREFS_NAME).apply {
            @Suppress("DEPRECATION")
            makeWorldReadable()
        }
        refreshPrefs(force = true)
    }

    private fun isEnabled(): Boolean {
        val now = SystemClock.uptimeMillis()
        if (now - lastPrefCheckMs > PREF_TTL_MS) refreshPrefs(force = false)
        return enabled
    }

    private fun refreshPrefs(@Suppress("UNUSED_PARAMETER") force: Boolean) {
        try {
            // XSharedPreferences doesn't watch the file, so we explicitly reload. `reload()`
            // is a no-op fast path when the mtime hasn't changed, so this is safe to call
            // on every TTL tick.
            sharedPrefs.reload()
            enabled = sharedPrefs.getBoolean(EdgeHapticsBridge.KEY_EDGE_ENABLED, true)
            val patternName = XposedHelpers.callMethod(
                sharedPrefs,
                "getString",
                EdgeHapticsBridge.KEY_EDGE_PATTERN,
                "TICK"
            ) as String
            pattern = HapticPattern.fromStorageKey(patternName)
            intensity = XposedHelpers.callMethod(
                sharedPrefs,
                "getFloat",
                EdgeHapticsBridge.KEY_EDGE_INTENSITY,
                1.0f
            ) as Float
            lastPrefCheckMs = SystemClock.uptimeMillis()
        } catch (t: Throwable) {
            XposedBridge.log("$TAG: prefs reload failed: ${t.message}")
        }
    }

    // ------------------------------------------------------------------
    // RecyclerView
    // ------------------------------------------------------------------

    private fun hookRecyclerView(lpparam: XC_LoadPackage.LoadPackageParam) {
        // The user's spec calls for hooking `setAdapter` and attaching an OnScrollListener.
        // Because `RecyclerView.OnScrollListener` is an abstract class (not an interface), we
        // cannot implement it via `java.lang.reflect.Proxy` from the module JAR. Instead we
        // achieve an equivalent effect by hooking `dispatchOnScrollStateChanged(int)` —
        // RecyclerView itself calls this from `setScrollState`, and it is the single
        // chokepoint through which every scroll-state transition flows before any
        // listener sees it. Result: pixel-perfect edge detection on every IDLE state,
        // no abstract-class subclassing needed.
        for (className in RV_CLASS_CANDIDATES) {
            val rvClass = XposedHelpers.findClassIfExists(className, lpparam.classLoader) ?: continue
            try {
                XposedHelpers.findAndHookMethod(
                    rvClass, "dispatchOnScrollStateChanged",
                    Int::class.javaPrimitiveType,
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            if (!isEnabled()) return
                            val state = (param.args[0] as? Int) ?: return
                            if (state != STATE_IDLE) return
                            val rv = param.thisObject as? View ?: return
                            val atTop = !rv.canScrollVertically(-1)
                            val atBottom = !rv.canScrollVertically(1)
                            dispatchEdge(rv.context, atTop, atBottom)
                        }
                    },
                )
                XposedBridge.log("$TAG: hooked $className in ${lpparam.packageName}")
                // First candidate that resolved wins; don't double-hook a shaded second copy.
                return
            } catch (t: Throwable) {
                XposedBridge.log("$TAG: RecyclerView hook failed on $className: ${t.message}")
            }
        }
    }

    // ------------------------------------------------------------------
    // ScrollView / NestedScrollView
    // ------------------------------------------------------------------

    private fun hookScrollView(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            XposedHelpers.findAndHookMethod(
                ScrollView::class.java, "onScrollChanged",
                Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
                scrollViewHook,
            )
        } catch (t: Throwable) {
            XposedBridge.log("$TAG: ScrollView hook failed: ${t.message}")
        }
    }

    private fun hookNestedScrollView(lpparam: XC_LoadPackage.LoadPackageParam) {
        for (className in NSV_CLASS_CANDIDATES) {
            val cls = XposedHelpers.findClassIfExists(className, lpparam.classLoader) ?: continue
            try {
                XposedHelpers.findAndHookMethod(
                    cls, "onScrollChanged",
                    Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
                    scrollViewHook,
                )
                XposedBridge.log("$TAG: hooked $className")
            } catch (t: Throwable) {
                XposedBridge.log("$TAG: NSV hook failed on $className: ${t.message}")
            }
        }
    }

    private val scrollViewHook = object : XC_MethodHook() {
        override fun afterHookedMethod(param: MethodHookParam) {
            if (!isEnabled()) return
            val view = param.thisObject as? ViewGroup ?: return
            val scrollY = (param.args[1] as? Int) ?: view.scrollY
            val child = view.getChildAt(0) ?: return
            val viewportH = view.height
            val contentH = child.height
            val atTop = scrollY <= 0
            val atBottom = contentH > 0 && scrollY + viewportH >= contentH
            dispatchEdge(view.context, atTop, atBottom)
        }
    }

    // ------------------------------------------------------------------
    // WebView
    // ------------------------------------------------------------------

    private fun hookWebView(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            XposedHelpers.findAndHookMethod(
                WebView::class.java, "onScrollChanged",
                Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
                webViewHook,
            )
        } catch (t: Throwable) {
            XposedBridge.log("$TAG: WebView hook failed: ${t.message}")
        }
    }

    private val webViewHook = object : XC_MethodHook() {
        override fun afterHookedMethod(param: MethodHookParam) {
            if (!isEnabled()) return
            val wv = param.thisObject as? WebView ?: return
            val scrollY = (param.args[1] as? Int) ?: wv.scrollY
            val scale = try {
                // WebView.getScale() is deprecated but still returns the live zoom; prefer it
                // when available, fall back to 1.0f on the rare WebView impls that throw.
                @Suppress("DEPRECATION")
                wv.scale
            } catch (_: Throwable) {
                1.0f
            }
            val contentH = (wv.contentHeight * scale).toInt()
            val viewportH = wv.height
            val atTop = scrollY <= 0
            val atBottom = contentH > 0 && scrollY + viewportH >= contentH
            dispatchEdge(wv.context, atTop, atBottom)
        }
    }

    // ------------------------------------------------------------------
    // Shared edge dispatch
    // ------------------------------------------------------------------

    private fun dispatchEdge(context: Context, atTop: Boolean, atBottom: Boolean) {
        if (!atTop && !atBottom) return
        // Rare case: a tiny content height below the viewport makes both true. Prefer TOP
        // so the user gets a consistent sensation regardless of which first reached zero.
        val edge = if (atTop) Edge.TOP else Edge.BOTTOM
        val decision = EdgeDetector.Shared.detect(edge, SystemClock.uptimeMillis())
        if (decision == EdgeDetector.Decision.FIRE) {
            EdgeVibrator.play(context, edge, pattern, intensity)
        }
    }

    private companion object {
        const val TAG = "HapticksEdgeHooks"
        const val OWN_PACKAGE = "com.hapticks.app"

        /** Millis between XSharedPreferences re-reads. Cheap (an in-process file stat). */
        const val PREF_TTL_MS = 5_000L

        /** RecyclerView.SCROLL_STATE_IDLE — avoids a hard dep on the RV class. */
        const val STATE_IDLE = 0

        val RV_CLASS_CANDIDATES = arrayOf(
            "androidx.recyclerview.widget.RecyclerView",
            "android.support.v7.widget.RecyclerView",
        )

        val NSV_CLASS_CANDIDATES = arrayOf(
            "androidx.core.widget.NestedScrollView",
            "android.support.v4.widget.NestedScrollView",
        )
    }
}

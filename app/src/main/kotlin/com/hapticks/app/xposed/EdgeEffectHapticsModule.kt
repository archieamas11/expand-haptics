package com.hapticks.app.xposed

import android.app.Application
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

/**
 * Injects EdgeEffect haptic feedback into every hooked process.
 *
 * Interaction model:
 *  - PULL   : First frame the user drags into the edge boundary → single haptic.
 *             Holding longer never re-fires (deduped per drag session).
 *  - RELEASE: Finger lifted while at the edge → bounce-back haptic once.
 *  - ABSORB : Fling absorbed at the edge → immediate haptic (inherently one-shot).
 *
 * Thread-safety:
 *  [edgeState] is only ever read/written on the main thread — EdgeEffect is a UI
 *  component and all its callbacks are main-thread-only. No synchronisation is
 *  needed for the map itself.
 *
 *  Preference fields ([enabled], [cachedPattern], [cachedIntensity]) are @Volatile
 *  because [SharedPreferences.OnSharedPreferenceChangeListener] may fire on any thread.
 */
class EdgeEffectHapticsModule : XposedModule() {

    /**
     * Tracks per-instance "at edge" state. True means the current drag session
     * has already triggered a PULL haptic and the finger is still down.
     * Entries are automatically evicted when EdgeEffect instances are GC'd.
     */
    private val edgeState = WeakHashMap<EdgeEffect, Boolean>()

    @Volatile private var vibrator: Vibrator? = null

    @Volatile private var enabled = false
    @Volatile private var cachedPattern: HapticPattern? = null
    @Volatile private var cachedIntensity = 1f

    /**
     * Constructed lazily — [VibrationAttributes] is API 31+ and is only ever
     * accessed after we confirm we have a valid vibrator on the same API level.
     */
    private val touchAttrs: VibrationAttributes by lazy {
        VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Module entry point
    // ─────────────────────────────────────────────────────────────────────────

    override fun onModuleLoaded(param: ModuleLoadedParam) {
        super.onModuleLoaded(param)
        log(Log.INFO, TAG, "onModuleLoaded — process=${param.processName}")

        if ((getFrameworkProperties() and PROP_CAP_REMOTE) == 0L) {
            log(Log.WARN, TAG, "PROP_CAP_REMOTE missing — remote prefs unavailable in this process")
        }

        loadRemotePrefs()
        hookApplicationOnCreate()
        hookEdgeEffect()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Preferences
    // ─────────────────────────────────────────────────────────────────────────

    private fun loadRemotePrefs() {
        val prefs = runCatching { getRemotePreferences(XposedEdgeRemotePrefs.GROUP) }
            .onFailure { log(Log.WARN, TAG, "Remote prefs unavailable: ${it.message}") }
            .getOrNull() ?: return

        applyPrefs(prefs)
        prefs.registerOnSharedPreferenceChangeListener { p, _ -> applyPrefs(p) }
    }

    private fun applyPrefs(prefs: android.content.SharedPreferences) {
        enabled         = prefs.getBoolean(XposedEdgeRemotePrefs.KEY_ENABLED, false)
        cachedPattern   = HapticPattern.fromStorageKey(prefs.getString(XposedEdgeRemotePrefs.KEY_PATTERN, null))
        cachedIntensity = prefs.getFloat(XposedEdgeRemotePrefs.KEY_INTENSITY, 1f).coerceIn(0f, 1f)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Vibrator acquisition — hook Application.onCreate (guaranteed init point)
    // ─────────────────────────────────────────────────────────────────────────

    private fun hookApplicationOnCreate() {
        runCatching {
            hookAfter(Application::class.java, "onCreate") { app, _ ->
                if (vibrator != null) return@hookAfter
                vibrator = (app as Application)
                    .getSystemService(VibratorManager::class.java)
                    ?.defaultVibrator
            }
        }.onFailure { log(Log.ERROR, TAG, "Failed to hook Application.onCreate", it) }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // EdgeEffect hooks
    // ─────────────────────────────────────────────────────────────────────────

    private fun hookEdgeEffect() {
        try {
            val cls = EdgeEffect::class.java

            // ── onPull(deltaDistance, displacement) ───────────────────────────
            // Called every frame the user drags past the edge boundary.
            // deltaDistance: fractional pull delta for this frame (> 0 = moving into edge).
            // We rely on deltaDistance rather than effect.distance so this works on all
            // API levels — getDistance() is only public API from API 31.
            hookAfter(
                cls, "onPull",
                Float::class.javaPrimitiveType,
                Float::class.javaPrimitiveType,
            ) { effect, args ->
                val delta = args[0] as? Float ?: return@hookAfter
                handlePull(effect as EdgeEffect, delta)
            }

            // ── onRelease() ───────────────────────────────────────────────────
            // User lifted their finger; starts the bounce-back / collapse animation.
            // We fire the release haptic here if a pull haptic was previously fired.
            runCatching {
                hookAfter(cls, "onRelease") { effect, _ ->
                    handleRelease(effect as EdgeEffect)
                }
            }.onFailure {
                // onRelease is present from API 14; missing only on very obscure forks.
                log(Log.INFO, TAG, "onRelease not found — release haptic unavailable: ${it.message}")
            }

            // ── onAbsorb(velocity) ────────────────────────────────────────────
            // Called once when a fling is stopped at the edge (overscroll absorbed).
            // One-shot by nature — no per-session deduplication needed.
            hookAfter(cls, "onAbsorb", Int::class.javaPrimitiveType) { effect, _ ->
                handleAbsorb(effect as EdgeEffect)
            }

            // ── setDistance(distance) — Android 12+ (TYPE_STRETCH) ────────────
            // Stretch-type EdgeEffects drive their distance via setDistance rather than
            // (or in addition to) onPull. We mirror handlePull logic here, and use a
            // distance of 0 to detect session end as a fallback to onRelease.
            runCatching {
                hookAfter(cls, "setDistance", Float::class.javaPrimitiveType) { effect, args ->
                    val distance = args[0] as? Float ?: return@hookAfter
                    handleSetDistance(effect as EdgeEffect, distance)
                }
            }.onFailure {
                log(Log.DEBUG, TAG, "setDistance not available — API < 31 or fork: ${it.message}")
            }

        } catch (t: Throwable) {
            log(Log.ERROR, TAG, "EdgeEffect hook installation failed", t)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // hookAfter — post-execution hook helper
    //
    // Executes the original method first via chain.proceed(), then runs [block].
    // Critically, it RETURNS the original return value unchanged — required for
    // non-void methods such as setDistance(float) which returns consumed distance.
    // ─────────────────────────────────────────────────────────────────────────

    private inline fun hookAfter(
        cls: Class<*>,
        name: String,
        vararg paramTypes: Class<*>?,
        crossinline block: (thisObj: Any, args: Array<Any?>) -> Unit,
    ) {
        val method = cls.getDeclaredMethod(name, *paramTypes)
        hook(method)
            .setExceptionMode(ExceptionMode.PROTECTIVE)
            .intercept { chain ->
                val result = chain.proceed()   // run original; capture return value
                runCatching { block(chain.thisObject, chain.args) }
                    .onFailure { log(Log.DEBUG, TAG, "hookAfter [$name] threw: ${it.message}") }
                result                         // preserve original return value — do NOT return null
            }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Edge-event handlers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Fires a PULL haptic the first frame [deltaDistance] > 0 in a drag session.
     * Subsequent frames are suppressed until [handleRelease] or [handleSetDistance]
     * resets the flag for this [effect] instance.
     *
     * We intentionally do NOT read [EdgeEffect.distance] here — it is only public
     * API from API 31. Using [deltaDistance] from the call arguments is compatible
     * with all supported API levels.
     */
    private fun handlePull(effect: EdgeEffect, deltaDistance: Float) {
        if (!enabled || deltaDistance <= 0f) return
        if (edgeState[effect] != true) {
            edgeState[effect] = true
            triggerHaptic(HapticEventType.PULL)
        }
    }

    /**
     * Android 12+ stretch-type [EdgeEffect]s may call [EdgeEffect.setDistance] instead
     * of [EdgeEffect.onPull], or in addition to it. This handler mirrors [handlePull]
     * semantics so stretch-mode scrollers behave identically.
     *
     * A distance of exactly 0f signals session end (used as a fallback alongside
     * [handleRelease] for views that don't call onRelease reliably).
     */
    private fun handleSetDistance(effect: EdgeEffect, distance: Float) {
        if (!enabled) return
        when {
            distance > 0f && edgeState[effect] != true -> {
                edgeState[effect] = true
                triggerHaptic(HapticEventType.PULL)
            }
            distance == 0f -> edgeState[effect] = false
        }
    }

    /**
     * Fires a RELEASE haptic when the user lifts their finger at the edge, then
     * resets the session flag. The haptic is suppressed if we never reached the
     * edge during this gesture (i.e. no PULL haptic was fired).
     */
    private fun handleRelease(effect: EdgeEffect) {
        if (!enabled) return
        val wasAtEdge = edgeState[effect] == true
        edgeState[effect] = false  // always reset — next gesture starts clean
        if (wasAtEdge) triggerHaptic(HapticEventType.RELEASE)
    }

    /**
     * Fires an ABSORB haptic when a fling is stopped at the edge.
     * [EdgeEffect.onAbsorb] is inherently one-shot, so no deduplication is applied.
     * Resets [edgeState] so any manual drag that follows starts a fresh session.
     */
    private fun handleAbsorb(effect: EdgeEffect) {
        if (!enabled) return
        edgeState[effect] = false  // reset before firing so a follow-up drag re-fires PULL
        triggerHaptic(HapticEventType.ABSORB)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Haptic delivery
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Distinguishes event types for logging and future per-event effect tuning
     * (e.g. a softer/shorter waveform for RELEASE vs PULL).
     */
    private enum class HapticEventType { PULL, RELEASE, ABSORB }

    /**
     * Delivers the vibration effect synchronously on the calling thread.
     *
     * No [android.os.Handler.post] is used. All callers arrive from EdgeEffect
     * callbacks which are always dispatched on the main thread. Posting would add
     * a full message-queue round-trip (≥ 1 frame at 60–120 Hz) before the vibrator
     * is notified — exactly the latency we're here to eliminate.
     */
    private fun triggerHaptic(type: HapticEventType) {
        val pattern   = cachedPattern   ?: return
        val intensity = cachedIntensity
        val v         = vibrator        ?: return
        if (!v.hasVibrator()) return

        val effect = runCatching {
            EdgeHapticsBridge.edgeVibrationEffect(pattern, intensity)
        }.onFailure {
            log(Log.DEBUG, TAG, "VibrationEffect build failed [$type]: ${it.message}")
        }.getOrNull() ?: return

        runCatching {
            v.vibrate(effect, touchAttrs)
        }.onFailure {
            log(Log.DEBUG, TAG, "vibrate() failed [$type]: ${it.message}")
        }
    }

    companion object {
        private const val TAG = "HapticksEdgeXposed"
    }
}
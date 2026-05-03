@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.hapticks.app.core.ui.extensions

import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.OverscrollEffect
import androidx.compose.foundation.OverscrollFactory
import androidx.compose.foundation.rememberPlatformOverscrollFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Velocity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hapticks.app.core.haptics.HapticEngine
import com.hapticks.app.core.haptics.HapticPattern
import com.hapticks.app.data.model.AppSettings
import com.hapticks.app.features.main.HapticksApp
import kotlinx.coroutines.flow.flowOf
import kotlin.math.abs

private const val EdgePullSlopPx = 0.1f

private class HapticInstrumentedOverscrollEffect(
    private val delegate: OverscrollEffect,
    private val engine: HapticEngine?,
    private val pattern: HapticPattern,
    private val intensity: Float,
) : OverscrollEffect {
    @Volatile
    private var edgeFired = false

    override fun applyToScroll(
        delta: Offset,
        source: NestedScrollSource,
        performScroll: (Offset) -> Offset,
    ): Offset {
        val result = delegate.applyToScroll(delta, source) { scrollDelta ->
            val consumedByChild = performScroll(scrollDelta)

            if (source == NestedScrollSource.UserInput || source == NestedScrollSource.Fling) {
                val overscroll = scrollDelta - consumedByChild
                val hitEdge =
                    abs(overscroll.x) > EdgePullSlopPx || abs(overscroll.y) > EdgePullSlopPx

                if (hitEdge && !edgeFired) {
                    edgeFired = true
                    triggerHaptic()
                }
            }

            consumedByChild
        }

        if (!delegate.isInProgress) {
            edgeFired = false
        }

        return result
    }

    override suspend fun applyToFling(
        velocity: Velocity,
        performFling: suspend (Velocity) -> Velocity,
    ) {
        delegate.applyToFling(velocity, performFling)
        edgeFired = false
    }

    override val isInProgress: Boolean
        get() = delegate.isInProgress

    override val node: DelegatableNode
        get() = delegate.node

    private fun triggerHaptic() {
        engine?.play(
            pattern = pattern,
            intensity = intensity,
            throttleMs = 0L,
        )
    }
}

private class HapticInstrumentedOverscrollFactory(
    private val delegate: OverscrollFactory,
    private val engine: HapticEngine?,
    private val pattern: HapticPattern,
    private val intensity: Float,
) : OverscrollFactory {
    override fun createOverscrollEffect(): OverscrollEffect =
        HapticInstrumentedOverscrollEffect(
            delegate.createOverscrollEffect(),
            engine,
            pattern,
            intensity,
        )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is HapticInstrumentedOverscrollFactory) return false
        return delegate == other.delegate &&
                engine === other.engine &&
                pattern == other.pattern &&
                intensity == other.intensity
    }

    override fun hashCode(): Int {
        var result = delegate.hashCode()
        result = 31 * result + (engine?.hashCode() ?: 0)
        result = 31 * result + pattern.hashCode()
        result = 31 * result + intensity.hashCode()
        return result
    }
}

@Composable
fun HapticOverscrollProvider(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as? HapticksApp
    val engine = remember(app) { app?.hapticEngine }
    val settingsFlow = remember(app) {
        app?.preferences?.settings ?: flowOf(AppSettings.Default)
    }
    val settings by settingsFlow.collectAsStateWithLifecycle(AppSettings.Default)
    val baseFactory = rememberPlatformOverscrollFactory()
    val factory = remember(
        baseFactory,
        engine,
        settings.hapticsEnabled
    ) {
        if (settings.hapticsEnabled) {
            HapticInstrumentedOverscrollFactory(
                baseFactory,
                engine,
                HapticPattern.WOBBLE,
                0.2f
            )
        } else {
            baseFactory
        }
    }
    CompositionLocalProvider(LocalOverscrollFactory provides factory) {
        content()
    }
}


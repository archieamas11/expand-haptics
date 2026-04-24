package com.hapticks.app.ui.haptics

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.LocalIndication
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import com.hapticks.app.data.HapticsSettings
import com.hapticks.app.haptics.HapticEngine
import com.hapticks.app.haptics.HapticPattern
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map

@Stable
class AppHaptics(
    private val engine: HapticEngine,
    private val settingsProvider: () -> HapticsSettings,
) {
    fun tap() {
        val settings = settingsProvider()
        if (!settings.tapEnabled) return
        engine.play(
            pattern = settings.pattern,
            intensity = settings.intensity * TAP_INTENSITY_SCALE,
            throttleMs = TAP_THROTTLE_MS,
        )
    }

    fun listEdgeTick() {
        val settings = settingsProvider()
        if (!settings.scrollEnabled) return
        // Respect user's chosen scroll pattern instead of always using TICK
        engine.play(
            pattern = settings.scrollPattern,
            intensity = settings.scrollIntensity * EDGE_INTENSITY_SCALE,
            throttleMs = EDGE_THROTTLE_MS,
        )
    }

    private companion object {
        const val TAP_INTENSITY_SCALE = 0.55f
        const val EDGE_INTENSITY_SCALE = 0.70f
        const val TAP_THROTTLE_MS = 24L
        const val EDGE_THROTTLE_MS = 140L
    }
}

val LocalAppHaptics = staticCompositionLocalOf<AppHaptics?> { null }

@Composable
fun ProvideAppHaptics(
    engine: HapticEngine,
    settings: HapticsSettings,
    content: @Composable () -> Unit,
) {
    val settingsState = rememberUpdatedState(settings)
    val appHaptics = remember(engine) {
        AppHaptics(
            engine = engine,
            settingsProvider = { settingsState.value },
        )
    }
    CompositionLocalProvider(LocalAppHaptics provides appHaptics, content = content)
}

fun Modifier.hapticClickable(
    enabled: Boolean = true,
    onClick: () -> Unit,
): Modifier = composed {
    val appHaptics = LocalAppHaptics.current
    val interactionSource = remember { MutableInteractionSource() }
    clickable(
        enabled = enabled,
        interactionSource = interactionSource,
        indication = LocalIndication.current,
        onClick = {
            appHaptics?.tap()
            onClick()
        },
    )
}

@Composable
fun HapticListEdgeFeedback(
    state: LazyListState,
) {
    val appHaptics = LocalAppHaptics.current

    LaunchedEffect(state, appHaptics) {
        var lastEdge: ListEdge = ListEdge.NONE
        snapshotFlow { state.currentEdge() }
            .drop(1)
            .distinctUntilChanged()
            .collectLatest { edge ->
                if (edge != ListEdge.NONE && edge != lastEdge) {
                    appHaptics?.listEdgeTick()
                }
                // Always update lastEdge, including NONE, so that returning to
                // the same edge after scrolling away triggers the haptic again.
                lastEdge = edge
            }
    }
}

@Composable
fun HapticScrollEdgeFeedback(
    state: ScrollState,
) {
    val appHaptics = LocalAppHaptics.current

    LaunchedEffect(state, appHaptics) {
        var lastEdge: ListEdge = ListEdge.NONE
        snapshotFlow { state.currentEdge() }
            .drop(1)
            .distinctUntilChanged()
            .collectLatest { edge ->
                if (edge != ListEdge.NONE && edge != lastEdge) {
                    appHaptics?.listEdgeTick()
                }
                lastEdge = edge
            }
    }
}

private enum class ListEdge { TOP, BOTTOM, NONE }

private fun LazyListState.currentEdge(): ListEdge {
    val layoutInfo = layoutInfo
    if (layoutInfo.totalItemsCount == 0) return ListEdge.NONE

    val firstVisible = layoutInfo.visibleItemsInfo.firstOrNull()
    val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()
    val atTop = firstVisible?.index == 0 && firstVisible.offset >= 0
    val atBottom = lastVisible?.index == (layoutInfo.totalItemsCount - 1) &&
        (lastVisible.offset + lastVisible.size) <= layoutInfo.viewportEndOffset

    return when {
        atTop -> ListEdge.TOP
        atBottom -> ListEdge.BOTTOM
        else -> ListEdge.NONE
    }
}

private fun ScrollState.currentEdge(): ListEdge = when {
    value <= 0 -> ListEdge.TOP
    value >= maxValue && maxValue > 0 -> ListEdge.BOTTOM
    else -> ListEdge.NONE
}

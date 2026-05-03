package com.hapticks.app.core.ui.extensions

import android.content.Context
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hapticks.app.core.haptics.HapticEngine
import com.hapticks.app.core.haptics.HapticPattern
import com.hapticks.app.data.model.AppSettings
import com.hapticks.app.features.main.HapticksApp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlin.math.roundToInt

const val SliderTickStepsDefault = 19

fun slider01ToTickIndex(
    value: Float,
    steps: Int = SliderTickStepsDefault
): Int =
    (value.coerceIn(0f, 1f) * (steps + 1))
        .roundToInt()
        .coerceIn(0, steps + 1)

private fun Context.hapticEngine(): HapticEngine? =
    (applicationContext as? HapticksApp)?.hapticEngine

fun Context.performHapticClick() {
    val app = applicationContext as? HapticksApp ?: return
    val snapshot = try {
        runBlocking { app.preferences.settings.first() }
    } catch (_: Throwable) {
        AppSettings.Default
    }
    if (!snapshot.hapticsEnabled) return
    app.hapticEngine.play(HapticPattern.Default, 0.2f)
}

fun Context.performHapticPattern(
    pattern: HapticPattern,
    intensityOverride: Float? = null,
) {
    val app = applicationContext as? HapticksApp ?: return
    val snapshot = try {
        runBlocking { app.preferences.settings.first() }
    } catch (_: Throwable) {
        AppSettings.Default
    }
    if (!snapshot.hapticsEnabled) return
    val intensity = intensityOverride ?: snapshot.intensity
    hapticEngine()?.play(pattern, intensity)
}

fun Context.performHapticDoubleClick() {
    performHapticPattern(HapticPattern.DOUBLE_CLICK)
}

fun Context.performHapticSliderTick() {
    val app = applicationContext as? HapticksApp ?: return
    val snapshot = try {
        runBlocking { app.preferences.settings.first() }
    } catch (_: Throwable) {
        AppSettings.Default
    }
    if (!snapshot.hapticsEnabled) return
    hapticEngine()?.play(
        pattern = HapticPattern.TICK,
        intensity = 0.42f,
    )
}

@Composable
fun rememberHapticClickAction(onClick: () -> Unit): () -> Unit {
    val context = LocalContext.current
    return remember(context, onClick) {
        {
            context.performHapticClick()
            onClick()
        }
    }
}

@Composable
fun withDefaultHaptic(onClick: () -> Unit): () -> Unit = rememberHapticClickAction(onClick)

fun Modifier.hapticClickable(
    enabled: Boolean = true,
    disableRipple: Boolean = false,
    onClick: () -> Unit,
): Modifier = composed {
    val context = LocalContext.current
    val app = context.applicationContext as? HapticksApp
    val engine = remember(app) { app?.hapticEngine }
    val settingsFlow = remember(app) {
        app?.preferences?.settings ?: flowOf(AppSettings.Default)
    }
    val settings by settingsFlow.collectAsStateWithLifecycle(AppSettings.Default)
    val interactionSource = remember { MutableInteractionSource() }

    clickable(
        enabled = enabled,
        interactionSource = interactionSource,
        indication = if (disableRipple) null else LocalIndication.current,
        onClick = {
            if (settings.hapticsEnabled) {
                engine?.play(HapticPattern.Default, 0.2f)
            }
            onClick()
        },
    )
}


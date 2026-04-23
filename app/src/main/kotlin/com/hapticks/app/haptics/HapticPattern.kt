package com.hapticks.app.haptics

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BlurCircular
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.DensityMedium
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.ui.graphics.vector.ImageVector
import com.hapticks.app.R

/**
 * User-selectable haptic patterns shown in the Pattern section of the UI.
 *
 * Each pattern carries its label, a one-line description, and an icon so the selector UI can
 * surface meaningful differentiation beyond the name alone. Order of declaration is the order
 * rendered in the selector grid.
 */
enum class HapticPattern(
    @StringRes val labelRes: Int,
    @StringRes val descriptionRes: Int,
    val icon: ImageVector,
) {
    CLICK(
        labelRes = R.string.pattern_click,
        descriptionRes = R.string.pattern_click_desc,
        icon = Icons.Rounded.TouchApp,
    ),
    TICK(
        labelRes = R.string.pattern_tick,
        descriptionRes = R.string.pattern_tick_desc,
        icon = Icons.Rounded.GraphicEq,
    ),
    HEAVY_CLICK(
        labelRes = R.string.pattern_heavy_click,
        descriptionRes = R.string.pattern_heavy_click_desc,
        icon = Icons.Rounded.Bolt,
    ),
    DOUBLE_CLICK(
        labelRes = R.string.pattern_double_click,
        descriptionRes = R.string.pattern_double_click_desc,
        icon = Icons.Rounded.Repeat,
    ),
    SOFT_BUMP(
        labelRes = R.string.pattern_soft_bump,
        descriptionRes = R.string.pattern_soft_bump_desc,
        icon = Icons.Rounded.BlurCircular,
    ),
    DOUBLE_TICK(
        labelRes = R.string.pattern_double_tick,
        descriptionRes = R.string.pattern_double_tick_desc,
        icon = Icons.Rounded.DensityMedium,
    ),
    TENSION_RELEASE(
        labelRes = R.string.pattern_tension_release,
        descriptionRes = R.string.pattern_tension_release_desc,
        icon = Icons.Rounded.Waves,
    );

    companion object {
        val Default: HapticPattern = TICK

        fun fromStorageKey(key: String?): HapticPattern =
            entries.firstOrNull { it.name == key } ?: Default
    }
}

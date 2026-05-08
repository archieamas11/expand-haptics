package com.hapticks.app.core.haptics

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes

import com.hapticks.app.R

enum class HapticPattern(
    @StringRes val labelRes: Int,
    @StringRes val descriptionRes: Int,
    @DrawableRes val iconRes: Int,
) {
    CLICK(
        labelRes = R.string.pattern_click,
        descriptionRes = R.string.pattern_click_desc,
        iconRes = R.drawable.touch_long_24px,
    ),
    TICK(
        labelRes = R.string.pattern_tick,
        descriptionRes = R.string.pattern_tick_desc,
        iconRes = R.drawable.graphic_eq_24px,
    ),
    HEAVY_CLICK(
        labelRes = R.string.pattern_heavy_click,
        descriptionRes = R.string.pattern_heavy_click_desc,
        iconRes = R.drawable.brick_24px,
    ),
    DOUBLE_CLICK(
        labelRes = R.string.pattern_double_click,
        descriptionRes = R.string.pattern_double_click_desc,
        iconRes = R.drawable.repeat_24px,
    ),
    SOFT_BUMP(
        labelRes = R.string.pattern_soft_bump,
        descriptionRes = R.string.pattern_soft_bump_desc,
        iconRes = R.drawable.blur_on_24px,
    ),
    DOUBLE_TICK(
        labelRes = R.string.pattern_double_tick,
        descriptionRes = R.string.pattern_double_tick_desc,
        iconRes = R.drawable.density_small_24px,
    ),
    LOW_TICK(
        labelRes = R.string.pattern_low_tick,
        descriptionRes = R.string.pattern_low_tick_desc,
        iconRes = R.drawable.horizontal_rule_24px,
    ),
    THUD(
        labelRes = R.string.pattern_thud,
        descriptionRes = R.string.pattern_thud_desc,
        iconRes = R.drawable.step_into_24px,
    ),
    SPIN(
        labelRes = R.string.pattern_spin,
        descriptionRes = R.string.pattern_spin_desc,
        iconRes = R.drawable.three60_24px,
    ),
    ELASTIC(
        labelRes = R.string.pattern_elastic,
        descriptionRes = R.string.pattern_elastic_desc,
        iconRes = R.drawable.settings_ethernet_24px,
    ),
    WOBBLE(
        labelRes = R.string.pattern_wobble,
        descriptionRes = R.string.pattern_wobble_desc,
        iconRes = R.drawable.airwave_24px,
    ),
    RAPID_FIRE(
        labelRes = R.string.pattern_rapid_fire,
        descriptionRes = R.string.pattern_rapid_fire_desc,
        iconRes = R.drawable.slow_motion_video_24px,
    ),
    HEARTBEAT(
        labelRes = R.string.pattern_heartbeat,
        descriptionRes = R.string.pattern_heartbeat_desc,
        iconRes = R.drawable.favorite_24px,
    ),
    CASCADE(
        labelRes = R.string.pattern_cascade,
        descriptionRes = R.string.pattern_cascade_desc,
        iconRes = R.drawable.waterfall_chart_24px,
    ),
    RUMBLE(
        labelRes = R.string.pattern_rumble,
        descriptionRes = R.string.pattern_rumble_desc,
        iconRes = R.drawable.earthquake_24px,
    );

    companion object {
        val Default: HapticPattern = TICK

        fun fromStorageKey(key: String?): HapticPattern {
            val normalized = key?.trim().orEmpty()
            if (normalized.isEmpty()) return Default

            val canonical = when (normalized.lowercase()) {
                "default" -> Default.name
                "click" -> CLICK.name
                "tick" -> TICK.name
                "heavy_click", "heavy-click", "heavyclick" -> HEAVY_CLICK.name
                "double_click", "double-click", "doubleclick" -> DOUBLE_CLICK.name
                "soft_bump", "soft-bump", "softbump" -> SOFT_BUMP.name
                "double_tick", "double-tick", "doubletick" -> DOUBLE_TICK.name
                "low_tick", "low-tick", "lowtick" -> LOW_TICK.name
                "thud" -> THUD.name
                "spin" -> SPIN.name
                "elastic" -> ELASTIC.name
                "wobble" -> WOBBLE.name
                "rapid_fire", "rapid-fire", "rapidfire" -> RAPID_FIRE.name
                "heartbeat" -> HEARTBEAT.name
                "cascade" -> CASCADE.name
                "rumble" -> RUMBLE.name
                else -> normalized.uppercase()
            }

            return entries.firstOrNull { it.name == canonical } ?: Default
        }
    }
}


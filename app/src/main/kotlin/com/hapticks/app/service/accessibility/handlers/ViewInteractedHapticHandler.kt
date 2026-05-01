package com.hapticks.app.service.accessibility.handlers

import android.os.Build
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import com.hapticks.app.data.model.AppSettings
import com.hapticks.app.core.haptics.HapticEngine
import com.hapticks.app.core.haptics.HapticPattern

object ViewInteractedHapticHandler {

    private const val TOGGLE_COALESCE_THROTTLE_MS = 120L

    private val TOGGLE_CONTENT_CHANGE_MASK: Int
        get() = if (Build.VERSION.SDK_INT >= 36) {
            AccessibilityEvent.CONTENT_CHANGE_TYPE_STATE_DESCRIPTION or
                    AccessibilityEvent.CONTENT_CHANGE_TYPE_CHECKED
        } else {
            AccessibilityEvent.CONTENT_CHANGE_TYPE_STATE_DESCRIPTION
        }

    @JvmStatic
    fun eventTypeMask(settings: AppSettings): Int {
        return if (settings.tapEnabled) {
            AccessibilityEvent.TYPE_VIEW_CLICKED or AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        } else 0
    }

    @JvmStatic
    fun hasToggleLikeContentChange(event: AccessibilityEvent): Boolean {
        val types = event.contentChangeTypes
        return types != 0 && (types and TOGGLE_CONTENT_CHANGE_MASK) != 0
    }

    @JvmStatic
    fun handle(engine: HapticEngine, settings: AppSettings, event: AccessibilityEvent): Boolean {
        if (!settings.tapEnabled) return true

        return when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                val node = event.source
                if (node != null) {
                    val isToggle = try {
                        node.isCheckable &&
                                (node.isSwitchRole() ||
                                        node.isCheckBoxRole() ||
                                        node.isRadioRole() ||
                                        node.hasToggleActions())
                    } finally {
                        node.recycle()
                    }
                    if (isToggle) {
                        return true
                    }
                }
                engine.play(settings.pattern, settings.intensity)
                true
            }

            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                val changeTypes = event.contentChangeTypes
                if (changeTypes == 0) return true
                if (changeTypes and TOGGLE_CONTENT_CHANGE_MASK == 0) return true
                if (!isSwitchLikeToggleForWindowEvent(event, changeTypes)) return true
                engine.play(
                    HapticPattern.DOUBLE_CLICK,
                    1.0f,
                    throttleMs = TOGGLE_COALESCE_THROTTLE_MS,
                )
                true
            }

            else -> false
        }
    }

    private fun isSwitchLikeToggleForWindowEvent(
        event: AccessibilityEvent,
        changeTypes: Int
    ): Boolean {
        val hasChecked = if (Build.VERSION.SDK_INT >= 36) {
            (changeTypes and AccessibilityEvent.CONTENT_CHANGE_TYPE_CHECKED) != 0
        } else false
        val hasStateDescription =
            (changeTypes and AccessibilityEvent.CONTENT_CHANGE_TYPE_STATE_DESCRIPTION) != 0
        if (!hasChecked && !hasStateDescription) return false

        val node = event.source ?: return false
        if (!node.isCheckable) return false
        if (node.isRatingBar() || node.isChip()) return false
        if (node.isSwitchRole() || node.hasToggleActions()) return true
        if (hasChecked && !node.isCheckBoxRole() && !node.isRadioRole()) return true
        return false
    }

    @Suppress("NOTHING_TO_INLINE")
    private inline fun AccessibilityNodeInfo.isSwitchRole(): Boolean {
        val role = AccessibilityNodeInfoCompat.wrap(this).roleDescription?.toString()
        if (role != null) {
            if (role.equals("switch", ignoreCase = true) ||
                role.equals("toggle", ignoreCase = true)
            ) return true
        }
        return false
    }

    @Suppress("NOTHING_TO_INLINE")
    private inline fun AccessibilityNodeInfo.isCheckBoxRole(): Boolean {
        val role = AccessibilityNodeInfoCompat.wrap(this).roleDescription?.toString()
        return role != null && role.equals("checkbox", ignoreCase = true)
    }

    @Suppress("NOTHING_TO_INLINE")
    private inline fun AccessibilityNodeInfo.isRadioRole(): Boolean {
        val role = AccessibilityNodeInfoCompat.wrap(this).roleDescription?.toString()
        return role != null && (
                role.equals("radio button", ignoreCase = true) ||
                        role.equals("radio", ignoreCase = true)
                )
    }

    @Suppress("NOTHING_TO_INLINE")
    private inline fun AccessibilityNodeInfo.isRatingBar(): Boolean {
        val role = AccessibilityNodeInfoCompat.wrap(this).roleDescription?.toString()
        if (role != null && role.contains("rating", ignoreCase = true)) return true
        return this.rangeInfo != null
    }

    @Suppress("NOTHING_TO_INLINE")
    private inline fun AccessibilityNodeInfo.isChip(): Boolean {
        val role = AccessibilityNodeInfoCompat.wrap(this).roleDescription?.toString()
        return role != null && role.contains("chip", ignoreCase = true)
    }

    @Suppress("NOTHING_TO_INLINE")
    private inline fun AccessibilityNodeInfo.hasToggleActions(): Boolean {
        val compat = AccessibilityNodeInfoCompat.wrap(this)
        val actionList = compat.actionList
        var hasClick = false
        for (action in actionList) {
            val id = action.id
            if (id == AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_CLICK.id ||
                id == AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_SELECT.id
            ) {
                hasClick = true
                break
            }
        }
        return hasClick && this.isCheckable
    }
}


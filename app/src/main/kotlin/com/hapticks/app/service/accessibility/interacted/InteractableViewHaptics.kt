package com.hapticks.app.service.accessibility.interacted

import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.hapticks.app.data.AppSettings
import com.hapticks.app.haptics.HapticEngine
import com.hapticks.app.haptics.HapticPattern

object InteractableViewHaptics {

    private const val TOGGLE_COALESCE_THROTTLE_MS = 120L

    private const val TOGGLE_CONTENT_CHANGE_MASK: Int =
        AccessibilityEvent.CONTENT_CHANGE_TYPE_STATE_DESCRIPTION or
            AccessibilityEvent.CONTENT_CHANGE_TYPE_CHECKED

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

    @Suppress("NOTHING_TO_INLINE")
    private inline fun containsIgnoreCase(haystack: CharSequence?, needle: String): Boolean {
        if (haystack == null) return false
        val hLen = haystack.length
        val nLen = needle.length
        if (nLen == 0 || hLen < nLen) return false
        val last = hLen - nLen
        for (i in 0..last) {
            if (regionMatches(haystack, i, needle, 0, nLen)) return true
        }
        return false
    }

    @Suppress("NOTHING_TO_INLINE")
    private inline fun regionMatches(
        haystack: CharSequence, hayOffset: Int,
        needle: String, needleOffset: Int, len: Int
    ): Boolean {
        for (i in 0 until len) {
            val hc = haystack[hayOffset + i]
            val nc = needle[needleOffset + i]
            if (hc != nc && hc.uppercaseChar() != nc.uppercaseChar()) return false
        }
        return true
    }

    @Suppress("NOTHING_TO_INLINE")
    private inline fun endsWithIgnoreCase(haystack: CharSequence?, needle: String): Boolean {
        if (haystack == null) return false
        val hLen = haystack.length
        val nLen = needle.length
        if (hLen < nLen) return false
        return regionMatches(haystack, hLen - nLen, needle, 0, nLen)
    }

    @Suppress("NOTHING_TO_INLINE")
    private inline fun equalsIgnoreCase(haystack: CharSequence?, needle: String): Boolean {
        if (haystack == null) return false
        if (haystack.length != needle.length) return false
        return regionMatches(haystack, 0, needle, 0, needle.length)
    }

    private fun isSwitchLikeToggleForWindowEvent(event: AccessibilityEvent, changeTypes: Int): Boolean {
        if (isObviousSwitchClassName(event.className)) return true

        val hasChecked = (changeTypes and AccessibilityEvent.CONTENT_CHANGE_TYPE_CHECKED) != 0
        val hasStateDescription = (changeTypes and AccessibilityEvent.CONTENT_CHANGE_TYPE_STATE_DESCRIPTION) != 0
        if (!hasChecked && !hasStateDescription) return false

        val node = event.source
        if (node == null) return false
        try {
            if (isExcludedCheckable(node)) {
                return false
            }
            if (isObviousSwitchClassName(node.className)) return true
            if (!node.isCheckable) return false
            if (!isAmbiguousCheckableAsSwitch(node)) return false
            return hasChecked || hasStateDescription
        } finally {
            runCatching { node.recycle() }
        }
    }

    @Suppress("NOTHING_TO_INLINE")
    private inline fun isObviousSwitchClassName(className: CharSequence?): Boolean {
        if (className == null) return false
        if (containsIgnoreCase(className, "CheckBox")) return false
        if (containsIgnoreCase(className, "Radio")) return false
        if (containsIgnoreCase(className, "Switch")) return true
        if (endsWithIgnoreCase(className, "ToggleButton")) return true
        return false
    }

    @Suppress("NOTHING_TO_INLINE")
    private inline fun isAmbiguousCheckableAsSwitch(node: AccessibilityNodeInfo): Boolean {
        if (!node.isCheckable) return false
        val className = node.className ?: return false
        if (equalsIgnoreCase(className, "android.view.View")) return true
        if (containsIgnoreCase(className, "compose") &&
            containsIgnoreCase(className, "ui") &&
            containsIgnoreCase(className, "View")
        ) return true
        if (containsIgnoreCase(className, "CompoundButton") &&
            !containsIgnoreCase(className, "Check")
        ) return true
        return false
    }

    @Suppress("NOTHING_TO_INLINE")
    private inline fun isExcludedCheckable(node: AccessibilityNodeInfo): Boolean {
        val className = node.className ?: return false
        if (containsIgnoreCase(className, "CheckBox")) return true
        if (containsIgnoreCase(className, "Radio")) return true
        if (containsIgnoreCase(className, "Chip")) return true
        if (containsIgnoreCase(className, "Rating")) return true
        return false
    }
}
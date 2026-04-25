package com.hapticks.app.service.accessibility.typeviewscrolled

import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Stable key for grouping [android.view.accessibility.AccessibilityEvent.TYPE_VIEW_SCROLLED]
 * updates per scroll surface. Shared by scroll-content vibration and absolute-edge vibration.
 */
internal fun typeViewScrolledSurfaceKey(event: AccessibilityEvent): String? {
    val source: AccessibilityNodeInfo? = event.source
    return if (source != null) {
        try {
            val viewId = source.viewIdResourceName
            "w${event.windowId}\u001f${event.className}\u001f$viewId"
        } finally {
            source.recycle()
        }
    } else {
        event.className?.toString()?.let { cn -> "w${event.windowId}\u001f$cn" }
    }
}

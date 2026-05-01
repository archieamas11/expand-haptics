package com.hapticks.app.service.accessibility.handlers

import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

internal fun scrolledSurfaceKey(event: AccessibilityEvent): String? {
    val pkg = event.packageName?.toString()?.ifBlank { null } ?: "unknown"
    val windowId = event.windowId
    val base = "pkg=$pkg|win=$windowId"

    // First choice: concrete node id when present.
    // Better than class-only keys because many apps host multiple lists with same class.
    val nodeId = resolveViewResourceId(event)
    if (nodeId != null) {
        return "$base|id=$nodeId"
    }

    // Fallback: class name + item count hint.
    val className = event.className?.toString()?.ifBlank { null }
    if (!className.isNullOrBlank()) {
        return if (event.itemCount > 0) {
            "$base|cls=$className|items=${event.itemCount}"
        } else {
            "$base|cls=$className"
        }
    }

    // Last resort: broad key to avoid dropping unsupported app events entirely.
    return "$base|event=${event.eventType}"
}

private fun resolveViewResourceId(event: AccessibilityEvent): String? {
    val node: AccessibilityNodeInfo = event.source ?: return null
    return try {
        val id = node.viewIdResourceName
        if (id.isNullOrBlank() || id.endsWith("/no_id") || id == "null") null else id
    } finally {
        // recycle() is required before API 33 and a no-op after.
        if (android.os.Build.VERSION.SDK_INT < 33) {
            try {
                node.recycle()
            } catch (_: Exception) {
            }
        }
    }
}


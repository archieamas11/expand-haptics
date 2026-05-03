package com.hapticks.app.service.accessibility

import android.view.accessibility.AccessibilityEvent

internal val AccessibilityEvent.surfaceKey: Long
    get() {
        val pkgHash = packageName?.hashCode() ?: 0
        val clsHash = className?.hashCode() ?: 0
        val mixed = pkgHash * 31 + clsHash
        return (windowId.toLong() shl 32) or (mixed.toLong() and 0xFFFFFFFFL)
    }
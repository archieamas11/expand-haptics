package com.hapticks.app.service.accessibility.handlers

import android.view.accessibility.AccessibilityEvent

internal val AccessibilityEvent.surfaceKey: Long
    get() {
        val pkgHash = packageName?.hashCode() ?: 0
        val clsHash = className?.hashCode() ?: 0
        val mixed = pkgHash * 31 + clsHash
        return (windowId.toLong() shl 32) or (mixed.toLong() and 0xFFFFFFFFL)
    }

internal val AccessibilityEvent.sourceKey: Long
    get() {
        val base = surfaceKey
        val contentHash = contentDescription?.hashCode()?.toLong() ?: 0L
        val textHash = text?.joinToString("") { it.toString() }?.hashCode()?.toLong() ?: 0L
        return base xor (contentHash shl 16) xor (textHash shl 24)
    }
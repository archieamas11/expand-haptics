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
        val textHash = text?.fold(0) { acc, seq ->
            31 * acc + seq.hashCode()
        }?.toLong() ?: 0L
        val scrollComp = (scrollX.toLong() shl 16) xor scrollY.toLong()
        return base xor (contentHash shl 16) xor (textHash shl 24) xor scrollComp
    }
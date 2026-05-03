package com.hapticks.app.service.accessibility.events

import android.view.accessibility.AccessibilityEvent

const val HAPTICKS_APPLICATION_ID = "com.hapticks.app"

inline val AccessibilityEvent.isFromOwnApp: Boolean
    get() = packageName?.toString() == HAPTICKS_APPLICATION_ID
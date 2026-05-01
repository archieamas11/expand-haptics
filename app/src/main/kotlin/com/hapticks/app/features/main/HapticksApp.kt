package com.hapticks.app.features.main

import android.app.Application
import com.hapticks.app.data.repository.SettingsRepository
import com.hapticks.app.core.haptics.HapticEngine

class HapticksApp : Application() {

    val preferences: SettingsRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        SettingsRepository(this)
    }

    val hapticEngine: HapticEngine by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        HapticEngine(this)
    }
}


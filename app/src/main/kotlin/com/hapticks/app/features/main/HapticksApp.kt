package com.hapticks.app.features.main

import android.app.Application
import com.hapticks.app.data.repository.SettingsRepository
import com.hapticks.app.core.haptics.HapticEngine
import com.hapticks.app.data.model.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class HapticksApp : Application() {

    @Volatile
    var cachedSettings: AppSettings = AppSettings.Default

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    val preferences: SettingsRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        SettingsRepository(this)
    }

    val hapticEngine: HapticEngine by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        HapticEngine(this)
    }

    override fun onCreate() {
        super.onCreate()
        preferences.settings
            .onEach { cachedSettings = it }
            .launchIn(appScope)
    }
}


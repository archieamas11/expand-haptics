package com.hapticks.app

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hapticks.app.data.HapticsSettings
import com.hapticks.app.data.ThemeMode
import com.hapticks.app.ui.components.BottomTab
import com.hapticks.app.ui.components.FloatingBottomBar
import com.hapticks.app.ui.screens.CustomHapticsScreen
import com.hapticks.app.ui.screens.EdgeHapticsScreen
import com.hapticks.app.ui.screens.HomeScreen
import com.hapticks.app.ui.screens.SettingsScreen
import com.hapticks.app.ui.theme.HapticksTheme
import com.hapticks.app.viewmodel.CustomHapticsViewModel
import com.hapticks.app.viewmodel.EdgeHapticsViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: CustomHapticsViewModel by viewModels {
        CustomHapticsViewModel.factory(application)
    }

    private val edgeViewModel: EdgeHapticsViewModel by viewModels {
        EdgeHapticsViewModel.factory(application)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            val isServiceEnabled by viewModel.isServiceEnabled.collectAsStateWithLifecycle()
            val edgeAvailability by edgeViewModel.availability.collectAsStateWithLifecycle()
            val edgeTestEvent by edgeViewModel.testEvent.collectAsStateWithLifecycle()

            HapticksTheme(
                themeMode = settings.themeMode,
                useDynamicColors = settings.useDynamicColors,
                seedColor = settings.seedColor,
            ) {
                var route by rememberSaveable { mutableStateOf(Route.HOME) }

                Box(modifier = Modifier.fillMaxSize()) {
                    when (route) {
                        Route.FEEL_EVERY_TAP -> {
                            BackHandler { route = Route.HOME }
                            CustomHapticsScreen(
                                settings = settings,
                                isServiceEnabled = isServiceEnabled,
                                onTapEnabledChange = viewModel::setTapEnabled,
                                onIntensityCommit = viewModel::commitIntensity,
                                onPatternSelected = viewModel::setPattern,
                                onTestHaptic = viewModel::testHaptic,
                                onOpenAccessibilitySettings = ::openAccessibilitySettings,
                                onBack = { route = Route.HOME },
                            )
                        }
                        Route.EDGE_HAPTICS -> {
                            BackHandler { route = Route.HOME }
                            val edgeSettings by edgeViewModel.settings.collectAsStateWithLifecycle()
                            EdgeHapticsScreen(
                                settings = edgeSettings,
                                availability = edgeAvailability,
                                testEvent = edgeTestEvent,
                                onEdgeEnabledChange = edgeViewModel::setEdgeEnabled,
                                onPatternSelected = edgeViewModel::setEdgePattern,
                                onIntensityCommit = edgeViewModel::setEdgeIntensity,
                                onTestEdgeHaptic = edgeViewModel::testEdgeHaptic,
                                onTestEventConsumed = edgeViewModel::consumeTestEvent,
                                onBack = { route = Route.HOME },
                            )
                        }
                        Route.HOME -> {
                            HomeScreen(
                                onOpenFeelEveryTap = { route = Route.FEEL_EVERY_TAP },
                                onOpenEdgeHaptics = { route = Route.EDGE_HAPTICS },
                            )
                        }
                        Route.SETTINGS -> {
                            SettingsScreen(
                                settings = settings,
                                onUseDynamicColorsChange = viewModel::setUseDynamicColors,
                                onThemeModeChange = viewModel::setThemeMode,
                                onSeedColorChange = viewModel::setSeedColor
                            )
                        }
                    }

                    if (route == Route.HOME || route == Route.SETTINGS) {
                        FloatingBottomBar(
                            selectedTab = if (route == Route.HOME) BottomTab.HOME else BottomTab.SETTINGS,
                            onTabSelected = { tab ->
                                route = if (tab == BottomTab.HOME) Route.HOME else Route.SETTINGS
                            },
                            modifier = Modifier.align(Alignment.BottomCenter)
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // User may have toggled the service while away; re-check so the onboarding banner hides
        // itself once enabled without a cold restart.
        viewModel.refreshServiceState()
        // LSPosed state can change out-of-band (module enabled/disabled, scope changed) so
        // re-probe availability whenever the screen returns to the foreground.
        edgeViewModel.refreshAvailability()
    }

    private enum class Route { HOME, FEEL_EVERY_TAP, EDGE_HAPTICS, SETTINGS }

    private fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
    }
}

package com.hapticks.app

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hapticks.app.ui.screens.CustomHapticsScreen
import com.hapticks.app.ui.theme.HapticksTheme
import com.hapticks.app.viewmodel.CustomHapticsViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: CustomHapticsViewModel by viewModels {
        CustomHapticsViewModel.factory(application)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HapticksTheme {
                val settings by viewModel.settings.collectAsStateWithLifecycle()
                val isServiceEnabled by viewModel.isServiceEnabled.collectAsStateWithLifecycle()

                CustomHapticsScreen(
                    settings = settings,
                    isServiceEnabled = isServiceEnabled,
                    onTapEnabledChange = viewModel::setTapEnabled,
                    onIntensityCommit = viewModel::commitIntensity,
                    onPatternSelected = viewModel::setPattern,
                    onTestHaptic = viewModel::testHaptic,
                    onOpenAccessibilitySettings = ::openAccessibilitySettings,
                    onBack = ::finish,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // User may have toggled the service while away; re-check so the onboarding banner hides
        // itself once enabled without a cold restart.
        viewModel.refreshServiceState()
    }

    private fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
    }
}

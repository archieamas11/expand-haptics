package com.hapticks.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hapticks.app.R
import com.hapticks.app.data.HapticsSettings
import com.hapticks.app.haptics.HapticPattern
import com.hapticks.app.ui.components.EnableServiceCard
import com.hapticks.app.ui.components.HapticToggleRow
import com.hapticks.app.ui.components.PatternChipRow
import com.hapticks.app.ui.components.SectionCard
import kotlin.math.roundToInt

/**
 * Top-level screen rendered by [com.hapticks.app.MainActivity]. Matches the reference
 * screenshot: back arrow, "Custom Haptics" title, two toggle rows + intensity slider, pattern
 * chip row, and a Test Haptic button. When the accessibility service isn't enabled, a
 * primary-container onboarding card is pinned above the content.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomHapticsScreen(
    settings: HapticsSettings,
    isServiceEnabled: Boolean,
    onTapEnabledChange: (Boolean) -> Unit,
    onIntensityCommit: (Float) -> Unit,
    onPatternSelected: (HapticPattern) -> Unit,
    onTestHaptic: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(id = R.string.screen_title),
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(id = R.string.back),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            if (!isServiceEnabled) {
                EnableServiceCard(onOpenSettings = onOpenAccessibilitySettings)
            }

            HapticFeedbackSection(
                settings = settings,
                onTapEnabledChange = onTapEnabledChange,
                onIntensityCommit = onIntensityCommit,
            )

            PatternSection(
                settings = settings,
                onPatternSelected = onPatternSelected,
                onTestHaptic = onTestHaptic,
            )

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun HapticFeedbackSection(
    settings: HapticsSettings,
    onTapEnabledChange: (Boolean) -> Unit,
    onIntensityCommit: (Float) -> Unit,
) {
    SectionCard(
        title = stringResource(id = R.string.section_haptic_feedback),
        contentPadding = PaddingValues(vertical = 4.dp),
    ) {
        HapticToggleRow(
            title = stringResource(id = R.string.toggle_tap_title),
            subtitle = stringResource(id = R.string.toggle_tap_subtitle),
            checked = settings.tapEnabled,
            onCheckedChange = onTapEnabledChange,
        )
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant,
            thickness = 0.5.dp,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        IntensityControl(
            intensity = settings.intensity,
            onIntensityCommit = onIntensityCommit,
        )
    }
}

@Composable
private fun IntensityControl(
    intensity: Float,
    onIntensityCommit: (Float) -> Unit,
) {
    // Local draft keeps the slider responsive without writing to DataStore on every drag frame;
    // persistence and the preview haptic are deferred to `onValueChangeFinished`.
    var draft by remember(intensity) { mutableFloatStateOf(intensity) }
    val percent = (draft * 100f).roundToInt()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(id = R.string.intensity_label, percent),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Slider(
            value = draft,
            onValueChange = { draft = it },
            onValueChangeFinished = { onIntensityCommit(draft) },
            valueRange = 0f..1f,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.onSurface,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.primaryContainer,
                activeTickColor = MaterialTheme.colorScheme.primary,
                inactiveTickColor = MaterialTheme.colorScheme.primaryContainer,
            ),
        )
    }
}

@Composable
private fun PatternSection(
    settings: HapticsSettings,
    onPatternSelected: (HapticPattern) -> Unit,
    onTestHaptic: () -> Unit,
) {
    SectionCard(
        title = stringResource(id = R.string.section_pattern),
        contentPadding = PaddingValues(vertical = 8.dp),
    ) {
        PatternChipRow(
            selected = settings.pattern,
            onPatternSelected = onPatternSelected,
        )
        Spacer(modifier = Modifier.height(24.dp))
        OutlinedButton(
            onClick = onTestHaptic,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp),
        ) {
            Text(
                text = stringResource(id = R.string.test_haptic),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

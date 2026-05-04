package com.hapticks.app.features.scroll

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hapticks.app.R
import com.hapticks.app.core.haptics.HapticPattern
import com.hapticks.app.core.ui.components.BackPill
import com.hapticks.app.core.ui.components.EnableServiceCard
import com.hapticks.app.core.ui.components.HapticIntensityControl
import com.hapticks.app.core.ui.components.HapticTestButton
import com.hapticks.app.core.ui.components.HapticToggleRow
import com.hapticks.app.core.ui.components.PatternSelector
import com.hapticks.app.core.ui.components.SectionCard
import com.hapticks.app.core.ui.extensions.SliderTickStepsDefault
import com.hapticks.app.core.ui.extensions.performHapticSliderTick
import com.hapticks.app.core.ui.extensions.slider01ToTickIndex
import com.hapticks.app.data.model.AppSettings
import java.util.Locale

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ScrollHapticsScreen(
    settings: AppSettings,
    isServiceEnabled: Boolean,
    onScrollEnabledChange: (Boolean) -> Unit,
    onScrollHapticDensityCommit: (Float) -> Unit,
    onIntensityCommit: (Float) -> Unit,
    onPatternSelected: (HapticPattern) -> Unit,
    onTestHaptic: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val topAppBarState = rememberTopAppBarState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(topAppBarState)
    val listState = rememberLazyListState()

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            LargeFlexibleTopAppBar(
                title = {
                    Text(
                        text = stringResource(id = R.string.scroll_haptics_title),
                        style = MaterialTheme.typography.displaySmall,
                    )
                },
                navigationIcon = { BackPill(onBack = onBack) },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        floatingActionButton = {
            HapticTestButton(
                onClick = onTestHaptic,
            )
        },
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                top = padding.calculateTopPadding() + 4.dp,
                end = 16.dp,
                bottom = padding.calculateBottomPadding() + 10.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            if (!isServiceEnabled) {
                item(key = "enable_service") {
                    EnableServiceCard(onOpenSettings = onOpenAccessibilitySettings)
                }
            }

            item(key = "scroll_toggle_section") {
                SectionCard {
                    HapticToggleRow(
                        title = stringResource(id = R.string.scroll_toggle_title),
                        subtitle = stringResource(id = R.string.scroll_toggle_subtitle),
                        checked = settings.scrollEnabled,
                        onCheckedChange = onScrollEnabledChange,
                    )

                    BatteryWarning()

                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant,
                        thickness = 0.5.dp,
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )
                    ScrollPulseDensityControl(
                        eventsPerHundredPx = settings.scrollHapticEventsPerHundredPx,
                        onCommit = onScrollHapticDensityCommit,
                    )
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant,
                        thickness = 0.5.dp,
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )
                    HapticIntensityControl(
                        title = stringResource(id = R.string.scroll_intensity_title),
                        subtitle = stringResource(id = R.string.scroll_intensity_subtitle),
                        intensity = settings.scrollIntensity,
                        onIntensityCommit = onIntensityCommit,
                    )
                }
            }

            item(key = "scroll_pattern_section") {
                SectionCard {
                    PatternSelector(
                        selected = settings.scrollPattern,
                        onPatternSelected = onPatternSelected,
                    )
                }
            }
        }
    }
}

private fun scrollDensitySliderToEvents(slider01: Float): Float {
    val t = slider01.coerceIn(0f, 1f)
    return AppSettings.MIN_SCROLL_EVENTS_PER_HUNDRED_PX +
            t * (AppSettings.MAX_SCROLL_EVENTS_PER_HUNDRED_PX - AppSettings.MIN_SCROLL_EVENTS_PER_HUNDRED_PX)
}

private fun eventsToScrollDensitySlider(eventsPerHundredPx: Float): Float {
    val e = eventsPerHundredPx.coerceIn(
        AppSettings.MIN_SCROLL_EVENTS_PER_HUNDRED_PX,
        AppSettings.MAX_SCROLL_EVENTS_PER_HUNDRED_PX,
    )
    return ((e - AppSettings.MIN_SCROLL_EVENTS_PER_HUNDRED_PX) /
            (AppSettings.MAX_SCROLL_EVENTS_PER_HUNDRED_PX - AppSettings.MIN_SCROLL_EVENTS_PER_HUNDRED_PX))
        .coerceIn(0f, 1f)
}

@Composable
private fun BatteryWarning() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(top = 8.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(id = R.string.scroll_warning_title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(id = R.string.scroll_warning_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ScrollPulseDensityControl(
    eventsPerHundredPx: Float,
    onCommit: (Float) -> Unit,
) {
    val context = LocalContext.current
    val initialSlider = eventsToScrollDensitySlider(eventsPerHundredPx)
    var draftSlider by remember(eventsPerHundredPx) { mutableFloatStateOf(initialSlider) }
    var lastTickIndex by remember(eventsPerHundredPx) {
        mutableIntStateOf(
            slider01ToTickIndex(
                initialSlider
            )
        )
    }
    val draftEvents = scrollDensitySliderToEvents(draftSlider)
    val eventsLabel = String.format(Locale.US, "%.2f", draftEvents)

    val sliderColors = SliderDefaults.colors(
        thumbColor = MaterialTheme.colorScheme.primary,
        activeTrackColor = MaterialTheme.colorScheme.primary,
        inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        activeTickColor = MaterialTheme.colorScheme.primary,
        inactiveTickColor = MaterialTheme.colorScheme.surfaceContainerHighest,
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(id = R.string.scroll_events_per_unit_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(id = R.string.scroll_events_per_unit_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End,
        ) {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = CircleShape,
            ) {
                Text(
                    text = stringResource(id = R.string.scroll_events_per_unit_value, eventsLabel),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }
        }
        Slider(
            value = draftSlider,
            onValueChange = { newValue ->
                draftSlider = newValue
                val tickIndex = slider01ToTickIndex(newValue)
                if (tickIndex != lastTickIndex) {
                    lastTickIndex = tickIndex
                    context.performHapticSliderTick()
                }
            },
            onValueChangeFinished = { onCommit(scrollDensitySliderToEvents(draftSlider)) },
            valueRange = 0f..1f,
            steps = SliderTickStepsDefault,
            colors = sliderColors,
        )
    }
}


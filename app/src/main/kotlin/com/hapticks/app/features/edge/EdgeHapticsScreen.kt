package com.hapticks.app.features.edge

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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.hapticks.app.core.ui.components.HapticTestButton
import com.hapticks.app.core.ui.components.HapticToggleRow
import com.hapticks.app.core.ui.components.PatternSelector
import com.hapticks.app.core.ui.components.SectionCard
import com.hapticks.app.core.ui.extensions.SliderTickStepsDefault
import com.hapticks.app.core.ui.extensions.performHapticSliderTick
import com.hapticks.app.core.ui.extensions.slider01ToTickIndex
import com.hapticks.app.data.model.AppSettings
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EdgeHapticsScreen(
    settings: AppSettings,
    testEvent: EdgeHapticsViewModel.TestEvent?,
    isServiceEnabled: Boolean,
    onA11yScrollBoundEdgeChange: (Boolean) -> Unit,
    onPatternSelected: (HapticPattern) -> Unit,
    onIntensityCommit: (Float) -> Unit,
    onTestEdgeHaptic: () -> Unit,
    onTestEventConsumed: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val topAppBarState = rememberTopAppBarState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(topAppBarState)
    val listState = rememberLazyListState()

    TestEventSnackbar(
        testEvent = testEvent,
        snackbarHostState = snackbarHostState,
        onConsumed = onTestEventConsumed,
    )

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        text = stringResource(id = R.string.edge_screen_title),
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
                onClick = onTestEdgeHaptic,
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                top = padding.calculateTopPadding() + 4.dp,
                end = 16.dp,
                bottom = padding.calculateBottomPadding() + 10.dp
            ),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            if (!isServiceEnabled) {
                item(key = "enable_service") {
                    EnableServiceCard(onOpenSettings = onOpenAccessibilitySettings)
                }
            }

            item(key = "edge_toggles_section") {
                SectionCard {
                    HapticToggleRow(
                        title = stringResource(id = R.string.edge_a11y_scroll_bound_title),
                        subtitle = stringResource(id = R.string.edge_a11y_scroll_bound_subtitle),
                        checked = settings.a11yScrollBoundEdge,
                        onCheckedChange = onA11yScrollBoundEdgeChange,
                    )
                    A11yScrollBoundEdgeGuideBlock()
                    IntensityControl(
                        intensity = settings.edgeIntensity,
                        onIntensityCommit = onIntensityCommit,
                    )
                }
            }

            item(key = "edge_pattern_section") {
                SectionCard {
                    PatternSelector(
                        selected = settings.edgePattern,
                        onPatternSelected = onPatternSelected,
                    )
                }
            }
        }
    }
}

@Composable
private fun A11yScrollBoundEdgeGuideBlock() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(top = 4.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(id = R.string.edge_a11y_guide_title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(id = R.string.edge_a11y_guide_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun IntensityControl(
    intensity: Float,
    onIntensityCommit: (Float) -> Unit,
) {
    val context = LocalContext.current
    var draft by remember(intensity) { mutableFloatStateOf(intensity) }
    var lastTickIndex by remember(intensity) {
        mutableIntStateOf(slider01ToTickIndex(intensity))
    }
    val percent = (draft * 100f).roundToInt()

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
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(id = R.string.intensity_label),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            IntensityBadge(percent = percent)
        }
        Slider(
            value = draft,
            onValueChange = { newValue ->
                draft = newValue
                val tickIndex = slider01ToTickIndex(newValue)
                if (tickIndex != lastTickIndex) {
                    lastTickIndex = tickIndex
                    context.performHapticSliderTick()
                }
            },
            onValueChangeFinished = { onIntensityCommit(draft) },
            valueRange = 0f..1f,
            steps = SliderTickStepsDefault,
            colors = sliderColors,
        )
    }
}

@Composable
private fun IntensityBadge(percent: Int) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = CircleShape,
    ) {
        Text(
            text = stringResource(id = R.string.intensity_value, percent),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun TestEventSnackbar(
    testEvent: EdgeHapticsViewModel.TestEvent?,
    snackbarHostState: SnackbarHostState,
    onConsumed: () -> Unit,
) {
    val noVibratorLabel = stringResource(id = R.string.edge_test_no_vibrator)

    LaunchedEffect(testEvent) {
        when (testEvent) {
            null -> return@LaunchedEffect
            EdgeHapticsViewModel.TestEvent.NoVibrator -> snackbarHostState.showSnackbar(
                noVibratorLabel
            )

            EdgeHapticsViewModel.TestEvent.Fired -> Unit
        }
        onConsumed()
    }
}


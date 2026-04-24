package com.hapticks.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hapticks.app.R
import com.hapticks.app.data.HapticsSettings
import com.hapticks.app.edge.EdgeHapticsBridge
import com.hapticks.app.haptics.HapticPattern
import com.hapticks.app.ui.components.HapticTestButton
import com.hapticks.app.ui.components.HapticToggleRow
import com.hapticks.app.ui.components.PatternSelector
import com.hapticks.app.ui.components.SectionCard
import com.hapticks.app.ui.haptics.HapticListEdgeFeedback
import com.hapticks.app.ui.haptics.LocalAppHaptics
import com.hapticks.app.viewmodel.EdgeHapticsViewModel
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EdgeHapticsScreen(
    settings: HapticsSettings,
    availability: EdgeHapticsBridge.AvailabilityStatus,
    testEvent: EdgeHapticsViewModel.TestEvent?,
    onEdgeEnabledChange: (Boolean) -> Unit,
    onPatternSelected: (HapticPattern) -> Unit,
    onIntensityCommit: (Float) -> Unit,
    onTestEdgeHaptic: () -> Unit,
    onTestEventConsumed: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val topAppBarState = rememberTopAppBarState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(topAppBarState)
    val listState = rememberLazyListState()
    HapticListEdgeFeedback(state = listState)

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
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item {
                AvailabilityBanner(availability = availability)
            }

            item {
                SectionCard {
                    HapticToggleRow(
                        title = stringResource(id = R.string.edge_toggle_title),
                        subtitle = stringResource(id = R.string.edge_toggle_subtitle),
                        checked = settings.edgeEnabled,
                        onCheckedChange = onEdgeEnabledChange,
                        leadingIcon = Icons.Rounded.Bolt,
                    )
                    IntensityControl(
                        intensity = settings.edgeIntensity,
                        onIntensityCommit = onIntensityCommit,
                    )
                }
            }

            item {
                SectionCard {
                    PatternSelector(
                        selected = settings.edgePattern,
                        onPatternSelected = onPatternSelected,
                    )
                }
            }

            item {
                HapticTestButton(
                    label = stringResource(id = R.string.edge_test_button),
                    enabled = settings.edgeEnabled,
                    onClick = onTestEdgeHaptic,
                )
            }

            if (availability == EdgeHapticsBridge.AvailabilityStatus.READY) {
                item {
                    Text(
                        text = stringResource(id = R.string.edge_note_scope),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
            }
            item {
                Spacer(modifier = Modifier.height(4.dp))
            }
        }
    }
}

@Composable
private fun IntensityControl(
    intensity: Float,
    onIntensityCommit: (Float) -> Unit,
) {
    var draft by remember(intensity) { mutableFloatStateOf(intensity) }
    val percent = (draft * 100f).roundToInt()

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
            onValueChange = { draft = it },
            onValueChangeFinished = { onIntensityCommit(draft) },
            valueRange = 0f..1f,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                activeTickColor = MaterialTheme.colorScheme.primary,
                inactiveTickColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            ),
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
    val readyLabel = stringResource(id = R.string.edge_status_ready_title)
    val noRootLabel = stringResource(id = R.string.edge_status_root_missing_title)
    val inactiveLabel = stringResource(id = R.string.edge_status_lsposed_inactive_title)
    val unavailableFormat = stringResource(id = R.string.edge_test_unavailable)

    LaunchedEffect(testEvent) {
        val message = when (testEvent) {
            null -> null
            EdgeHapticsViewModel.TestEvent.NoVibrator -> noVibratorLabel
            is EdgeHapticsViewModel.TestEvent.Unavailable -> {
                val reason = when (testEvent.reason) {
                    EdgeHapticsBridge.AvailabilityStatus.READY -> readyLabel
                    EdgeHapticsBridge.AvailabilityStatus.ROOT_MISSING -> noRootLabel
                    EdgeHapticsBridge.AvailabilityStatus.LSPOSED_INACTIVE -> inactiveLabel
                }
                unavailableFormat.format(reason)
            }
            EdgeHapticsViewModel.TestEvent.Fired -> null
        }
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            onConsumed()
        }
    }
}

@Composable
private fun AvailabilityBanner(availability: EdgeHapticsBridge.AvailabilityStatus) {
    val spec = when (availability) {
        EdgeHapticsBridge.AvailabilityStatus.READY -> BannerSpec(
            titleRes = R.string.edge_status_ready_title,
            bodyRes = R.string.edge_status_ready_body,
            icon = Icons.Rounded.CheckCircle,
            accent = MaterialTheme.colorScheme.primaryContainer,
            onAccent = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        EdgeHapticsBridge.AvailabilityStatus.LSPOSED_INACTIVE -> BannerSpec(
            titleRes = R.string.edge_status_lsposed_inactive_title,
            bodyRes = R.string.edge_status_lsposed_inactive_body,
            icon = Icons.Rounded.WarningAmber,
            accent = MaterialTheme.colorScheme.tertiaryContainer,
            onAccent = MaterialTheme.colorScheme.onTertiaryContainer,
        )
        EdgeHapticsBridge.AvailabilityStatus.ROOT_MISSING -> BannerSpec(
            titleRes = R.string.edge_status_root_missing_title,
            bodyRes = R.string.edge_status_root_missing_body,
            icon = Icons.Rounded.ErrorOutline,
            accent = MaterialTheme.colorScheme.errorContainer,
            onAccent = MaterialTheme.colorScheme.onErrorContainer,
        )
    }

    Surface(
        color = spec.accent,
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        color = spec.onAccent.copy(alpha = 0.12f),
                        shape = CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = spec.icon,
                    contentDescription = null,
                    tint = spec.onAccent,
                    modifier = Modifier.size(22.dp),
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = stringResource(id = spec.titleRes),
                    style = MaterialTheme.typography.titleMedium,
                    color = spec.onAccent,
                )
                Text(
                    text = stringResource(id = spec.bodyRes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = spec.onAccent.copy(alpha = 0.86f),
                )
            }
        }
    }
}

private data class BannerSpec(
    val titleRes: Int,
    val bodyRes: Int,
    val icon: ImageVector,
    val accent: Color,
    val onAccent: Color,
)

@Composable
private fun BackPill(onBack: () -> Unit) {
    val appHaptics = LocalAppHaptics.current
    IconButton(
        onClick = {
            appHaptics?.tap()
            onBack()
        },
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
            contentDescription = stringResource(id = R.string.back),
            tint = MaterialTheme.colorScheme.onSurface,
        )
    }
}

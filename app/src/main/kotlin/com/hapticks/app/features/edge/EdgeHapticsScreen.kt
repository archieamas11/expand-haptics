package com.hapticks.app.features.edge

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
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
import com.hapticks.app.data.model.AppSettings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EdgeHapticsScreen(
    settings: AppSettings,
    isServiceEnabled: Boolean,
    onA11yScrollBoundEdgeChange: (Boolean) -> Unit,
    onPatternSelected: (HapticPattern) -> Unit,
    onIntensityCommit: (Float) -> Unit,
    onTestEdgeHaptic: () -> Unit,
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
                    HapticIntensityControl(
                        title = stringResource(id = R.string.intensity_label),
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


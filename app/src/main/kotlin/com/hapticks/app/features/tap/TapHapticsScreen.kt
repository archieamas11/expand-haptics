package com.hapticks.app.features.tap

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
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
import com.hapticks.app.core.ui.components.HapticTestButton
import com.hapticks.app.data.model.AppSettings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TapHapticsScreen(
    settings: AppSettings,
    isServiceEnabled: Boolean,
    onTapEnabledChange: (Boolean) -> Unit,
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
            LargeTopAppBar(
                title = {
                    Text(
                        text = stringResource(id = R.string.screen_title),
                        style = MaterialTheme.typography.displaySmall,
                    )
                },
                navigationIcon = {
                    BackPill(onBack = onBack)
                },
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

            item(key = "interaction_section") {
                TapHapticsInteractionSection(
                    settings = settings,
                    onTapEnabledChange = onTapEnabledChange,
                    onIntensityCommit = onIntensityCommit,
                )
            }

            item(key = "pattern_section") {
                TapHapticsPatternSection(
                    settings = settings,
                    onPatternSelected = onPatternSelected,
                )
            }
        }
    }
}


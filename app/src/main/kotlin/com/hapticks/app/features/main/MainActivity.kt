package com.hapticks.app.features.main

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hapticks.app.R
import com.hapticks.app.core.ui.components.BottomTab
import com.hapticks.app.core.ui.components.FloatingBottomBar
import com.hapticks.app.core.ui.components.LiquidGlassBottomBar
import com.hapticks.app.core.ui.components.SlidingBottomTabHost
import com.hapticks.app.core.ui.extensions.HapticOverscrollProvider
import com.hapticks.app.core.ui.theme.HapticksTheme
import com.hapticks.app.data.model.AppSettings
import com.hapticks.app.features.edge.EdgeHapticsScreen
import com.hapticks.app.features.onboarding.OnboardingScreen
import com.hapticks.app.features.scroll.ScrollHapticsScreen
import com.hapticks.app.features.settings.SettingsScreen
import com.hapticks.app.features.settings.SettingsViewModel
import com.hapticks.app.features.tap.TapHapticsScreen
import com.hapticks.app.features.update.LatestRelease
import com.hapticks.app.features.update.UpdateCheckResult
import com.hapticks.app.features.update.UpdateCheckScreen
import com.hapticks.app.features.update.UpdateCheckUiState
import com.hapticks.app.features.update.fetchUpdateStatus
import com.hapticks.app.features.update.startApkDownload
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import dev.jeziellago.compose.markdowntext.MarkdownText
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: SettingsViewModel by viewModels {
        SettingsViewModel.factory(application)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            val isServiceEnabled by viewModel.isServiceEnabled.collectAsStateWithLifecycle()
            val nativeEasing = FastOutSlowInEasing
            val animDuration = 400

            HapticksTheme(
                themeMode = settings.themeMode,
                useDynamicColors = settings.useDynamicColors,
                amoledBlack = settings.amoledBlack,
                seedColor = settings.seedColor,
            ) {
                val backgroundColor = MaterialTheme.colorScheme.background
                val backdrop = rememberLayerBackdrop()

                HapticOverscrollProvider {
                    val context = LocalContext.current
                    val scope = rememberCoroutineScope()
                    var route by rememberSaveable { mutableStateOf(Route.UNINITIALIZED) }
                    val lastRootRoute = rememberSaveable { mutableStateOf(Route.HOME) }
                    var updateCheckUiState by remember {
                        mutableStateOf<UpdateCheckUiState>(
                            UpdateCheckUiState.Idle
                        )
                    }
                    var availableUpdate by remember { mutableStateOf<LatestRelease?>(null) }

                    LaunchedEffect(Unit) {
                        val result = fetchUpdateStatus()
                        if (result is UpdateCheckResult.UpdateAvailable) {
                            if (result.release.tagName != settings.lastDismissedUpdateVersion) {
                                availableUpdate = result.release
                            }
                        }
                    }

                    fun checkForUpdates() {
                        scope.launch {
                            updateCheckUiState = UpdateCheckUiState.Loading
                            updateCheckUiState = when (val result = fetchUpdateStatus()) {
                                UpdateCheckResult.UpToDate -> {
                                    UpdateCheckUiState.UpToDate
                                }

                                is UpdateCheckResult.UpdateAvailable -> {
                                    UpdateCheckUiState.UpdateAvailable(result.release)
                                }

                                UpdateCheckResult.Error -> {
                                    UpdateCheckUiState.Error
                                }
                            }
                        }
                    }

                    LaunchedEffect(settings) {
                        if (route == Route.UNINITIALIZED && settings !== AppSettings.Default) {
                            route =
                                if (settings.hasCompletedOnboarding) Route.HOME else Route.ONBOARDING
                        }
                    }

                    if (route == Route.UNINITIALIZED) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(backgroundColor)
                        )
                        return@HapticOverscrollProvider
                    }

                    val currentRootRoute =
                        if (route == Route.HOME || route == Route.SETTINGS) route else lastRootRoute.value
                    SideEffect {
                        if (route == Route.HOME || route == Route.SETTINGS) {
                            lastRootRoute.value = route
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(backgroundColor)
                    ) {
                        val transitionRoute =
                            if (route == Route.HOME || route == Route.SETTINGS) Route.HOME else route

                        AnimatedContent(
                            targetState = transitionRoute,
                            transitionSpec = {
                                if ((initialState == Route.HOME) && (targetState != Route.HOME)) {
                                    (slideInHorizontally(
                                        tween(
                                            animDuration,
                                            easing = nativeEasing,
                                        )
                                    ) { it } +
                                            fadeIn(tween(animDuration)) +
                                            scaleIn(
                                                initialScale = 0.92f,
                                                animationSpec = tween(
                                                    animDuration,
                                                    easing = nativeEasing,
                                                )
                                            ))
                                        .togetherWith(
                                            slideOutHorizontally(
                                                tween(
                                                    animDuration,
                                                    easing = nativeEasing,
                                                )
                                            ) { -it / 3 } +
                                                    fadeOut(tween(animDuration / 2)) +
                                                    scaleOut(
                                                        targetScale = 0.95f,
                                                        animationSpec = tween(
                                                            animDuration,
                                                            easing = nativeEasing,
                                                        )
                                                    )
                                        )
                                } else if ((initialState != Route.HOME) && (targetState == Route.HOME)) {
                                    (slideInHorizontally(
                                        tween(
                                            animDuration,
                                            easing = nativeEasing,
                                        )
                                    ) { -it / 3 } +
                                            fadeIn(tween(animDuration)) +
                                            scaleIn(
                                                initialScale = 0.95f,
                                                animationSpec = tween(
                                                    animDuration,
                                                    easing = nativeEasing,
                                                )
                                            ))
                                        .togetherWith(
                                            slideOutHorizontally(
                                                tween(
                                                    animDuration,
                                                    easing = nativeEasing,
                                                )
                                            ) { it } +
                                                    fadeOut(tween(animDuration / 2)) +
                                                    scaleOut(
                                                        targetScale = 0.92f,
                                                        animationSpec = tween(
                                                            animDuration,
                                                            easing = nativeEasing,
                                                        )
                                                    )
                                        )
                                } else {
                                    fadeIn(tween(animDuration)) togetherWith fadeOut(
                                        tween(
                                            animDuration
                                        )
                                    )
                                }
                            },
                            label = "screen_transition",
                            modifier = Modifier
                                .fillMaxSize()
                                .layerBackdrop(backdrop)
                                .background(backgroundColor)
                        ) { targetTransitionRoute ->
                            val currentRoute =
                                if (targetTransitionRoute == Route.HOME) currentRootRoute else targetTransitionRoute

                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(backgroundColor)
                            ) {
                                when (currentRoute) {
                                    Route.UNINITIALIZED -> {}
                                    Route.ONBOARDING -> {
                                        BackHandler { finish() }
                                        OnboardingScreen(
                                            onComplete = {
                                                viewModel.setHasCompletedOnboarding(completed = true)
                                                route = Route.HOME
                                            }
                                        )
                                    }

                                    Route.FEEL_EVERY_TAP -> {
                                        BackHandler { route = Route.HOME }
                                        TapHapticsScreen(
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
                                        EdgeHapticsScreen(
                                            settings = settings,
                                            isServiceEnabled = isServiceEnabled,
                                            onA11yScrollBoundEdgeChange = viewModel::setA11yScrollBoundEdge,
                                            onPatternSelected = viewModel::setEdgePattern,
                                            onIntensityCommit = viewModel::setEdgeIntensity,
                                            onTestEdgeHaptic = viewModel::testEdgeHaptic,
                                            onOpenAccessibilitySettings = ::openAccessibilitySettings,
                                            onBack = { route = Route.HOME },
                                        )
                                    }

                                    Route.HOME, Route.SETTINGS -> {
                                        val bottomTab =
                                            if (currentRoute == Route.HOME) BottomTab.HOME else BottomTab.SETTINGS
                                        SlidingBottomTabHost(
                                            selectedTab = bottomTab,
                                            modifier = Modifier.fillMaxSize(),
                                        ) { tab ->
                                            when (tab) {
                                                BottomTab.HOME -> HomeScreen(
                                                    onOpenFeelEveryTap = {
                                                        route = Route.FEEL_EVERY_TAP
                                                    },
                                                    onOpenEdgeHaptics = {
                                                        route = Route.EDGE_HAPTICS
                                                    },
                                                    onOpenTactileScrolling = {
                                                        route = Route.TACTILE_SCROLLING
                                                    },
                                                )

                                                BottomTab.SETTINGS -> SettingsScreen(
                                                    settings = settings,
                                                    onHapticsEnabledChange = viewModel::setHapticsEnabled,
                                                    onUseDynamicColorsChange = viewModel::setUseDynamicColors,
                                                    onThemeModeChange = viewModel::setThemeMode,
                                                    onAmoledBlackChange = viewModel::setAmoledBlack,
                                                    onLiquidGlassChange = viewModel::setLiquidGlass,
                                                    onOpenUpdateCheck = {
                                                        updateCheckUiState = UpdateCheckUiState.Idle
                                                        route = Route.UPDATE_CHECK
                                                    },
                                                )
                                            }
                                        }
                                    }

                                    Route.UPDATE_CHECK -> {
                                        BackHandler { route = Route.SETTINGS }
                                        UpdateCheckScreen(
                                            uiState = updateCheckUiState,
                                            onBack = { route = Route.SETTINGS },
                                            onCheckForUpdates = { checkForUpdates() },
                                        ) {
                                            val intent = Intent(
                                                Intent.ACTION_VIEW,
                                                "https://github.com/archieamas11/hapticks".toUri(),
                                            )
                                            context.startActivity(intent)
                                        }
                                    }

                                    Route.TACTILE_SCROLLING -> {
                                        BackHandler { route = Route.HOME }
                                        ScrollHapticsScreen(
                                            settings = settings,
                                            isServiceEnabled = isServiceEnabled,
                                            onScrollEnabledChange = viewModel::setScrollEnabled,
                                            onScrollHapticDensityCommit = viewModel::commitScrollHapticDensity,
                                            onIntensityCommit = viewModel::commitScrollIntensity,
                                            onPatternSelected = viewModel::setScrollPattern,
                                            onTestHaptic = viewModel::testScrollHaptic,
                                            onOpenAccessibilitySettings = ::openAccessibilitySettings,
                                            onBack = { route = Route.HOME },
                                        )
                                    }
                                }
                            }
                        }

                        AnimatedVisibility(
                            visible = (route == Route.HOME) || (route == Route.SETTINGS),
                            enter = slideInVertically(
                                tween(
                                    animDuration,
                                    easing = nativeEasing,
                                )
                            ) { it / 2 } + fadeIn(tween(animDuration)),
                            exit = slideOutVertically(
                                tween(
                                    animDuration,
                                    easing = nativeEasing,
                                )
                            ) { it / 2 } + fadeOut(tween(animDuration)),
                            modifier = Modifier.align(Alignment.BottomCenter)
                        ) {
                            val currentTab =
                                if (currentRootRoute == Route.HOME) BottomTab.HOME else BottomTab.SETTINGS
                            val onTab = { tab: BottomTab ->
                                route = if (tab == BottomTab.HOME) Route.HOME else Route.SETTINGS
                            }

                            if (settings.liquidGlass) {
                                LiquidGlassBottomBar(
                                    selectedTab = currentTab,
                                    onTabSelected = onTab,
                                    backdrop = backdrop,
                                )
                            } else {
                                FloatingBottomBar(
                                    selectedTab = currentTab,
                                    onTabSelected = onTab,
                                )
                            }
                        }

                        availableUpdate?.let { release ->
                            UpdateAvailableDialog(
                                release = release,
                                onDismiss = {
                                    viewModel.setLastDismissedUpdateVersion(release.tagName)
                                    availableUpdate = null
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshServiceState()
    }

    private enum class Route { UNINITIALIZED, ONBOARDING, HOME, FEEL_EVERY_TAP, EDGE_HAPTICS, TACTILE_SCROLLING, SETTINGS, UPDATE_CHECK }

    @OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
    @Composable
    private fun UpdateAvailableDialog(
        release: LatestRelease,
        onDismiss: () -> Unit,
    ) {
        val context = LocalContext.current
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .widthIn(max = 560.dp),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = stringResource(R.string.settings_check_updates_available_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )

                    Text(
                        text = stringResource(
                            R.string.settings_check_updates_available_subtitle,
                            release.tagName
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Text(
                        text = release.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )

                    MarkdownText(
                        markdown = release.body,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 300.dp)
                            .verticalScroll(rememberScrollState()),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        linkColor = MaterialTheme.colorScheme.primary,
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(text = stringResource(R.string.settings_changelog_close))
                        }

                        Button(
                            onClick = {
                                release.apkDownloadUrl?.let { url ->
                                    startApkDownload(context, release, url)
                                }
                                onDismiss()
                            },
                            enabled = release.apkDownloadUrl != null,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(text = stringResource(R.string.settings_check_updates_download_now))
                        }
                    }
                }
            }
        }
    }

    private fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
    }
}
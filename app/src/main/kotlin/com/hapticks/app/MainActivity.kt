package com.hapticks.app

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hapticks.app.ui.components.BottomTab
import com.hapticks.app.ui.components.FloatingBottomBar
import com.hapticks.app.ui.components.LiquidGlassBottomBar
import com.hapticks.app.ui.components.SlidingBottomTabHost
import com.hapticks.app.ui.screens.tapHaptics.FeelEveryTapScreen
import com.hapticks.app.ui.screens.HomeScreen
import com.hapticks.app.ui.screens.edgehaptics.EdgeHapticsScreen
import com.hapticks.app.ui.screens.fetchUpdateStatus
import com.hapticks.app.ui.screens.scrollhaptics.ScrollHapticsScreen
import com.hapticks.app.ui.screens.SettingsScreen
import com.hapticks.app.ui.screens.OnboardingScreen
import com.hapticks.app.ui.screens.UpdateCheckResult
import com.hapticks.app.ui.screens.UpdateCheckScreen
import com.hapticks.app.ui.screens.UpdateCheckUiState
import com.hapticks.app.data.AppSettings
import com.hapticks.app.ui.haptics.ProvideHapticksEdgeOverscrollHaptics
import com.hapticks.app.ui.theme.HapticksTheme
import com.hapticks.app.viewmodel.SettingsViewModel
import com.hapticks.app.viewmodel.EdgeHapticsViewModel
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import androidx.core.net.toUri
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: SettingsViewModel by viewModels {
        SettingsViewModel.factory(application)
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

                ProvideHapticksEdgeOverscrollHaptics {
                    val context = LocalContext.current
                    val scope = rememberCoroutineScope()
                    var route by rememberSaveable { mutableStateOf(Route.UNINITIALIZED) }
                    val lastRootRoute = rememberSaveable { mutableStateOf(Route.HOME) }
                    var updateCheckUiState by remember { mutableStateOf<UpdateCheckUiState>(UpdateCheckUiState.Idle) }

                    fun checkForUpdates() {
                        scope.launch {
                            updateCheckUiState = UpdateCheckUiState.Loading
                            when (val result = fetchUpdateStatus()) {
                                UpdateCheckResult.UpToDate -> {
                                    updateCheckUiState = UpdateCheckUiState.UpToDate
                                }
                                is UpdateCheckResult.UpdateAvailable -> {
                                    updateCheckUiState = UpdateCheckUiState.UpdateAvailable(result.release)
                                }
                                UpdateCheckResult.Error -> {
                                    updateCheckUiState = UpdateCheckUiState.Error
                                }
                            }
                        }
                    }

                    LaunchedEffect(settings) {
                        if (route == Route.UNINITIALIZED && settings !== AppSettings.Default) {
                            route = if (settings.hasCompletedOnboarding) Route.HOME else Route.ONBOARDING
                        }
                    }

                    if (route == Route.UNINITIALIZED) {
                        // Wait for DataStore to load
                        Box(modifier = Modifier.fillMaxSize().background(backgroundColor))
                        return@ProvideHapticksEdgeOverscrollHaptics
                    }

                    if (route == Route.HOME || route == Route.SETTINGS) {
                        lastRootRoute.value = route
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(backgroundColor)
                    ) {
                        val transitionRoute = if (route == Route.HOME || route == Route.SETTINGS) Route.HOME else route

                        AnimatedContent(
                            targetState = transitionRoute,
                            transitionSpec = {
                                if (initialState == Route.HOME && targetState != Route.HOME) {
                                    (slideInHorizontally(tween(animDuration, easing = nativeEasing)) { it } +
                                            fadeIn(tween(animDuration)) +
                                            scaleIn(initialScale = 0.92f, animationSpec = tween(animDuration, easing = nativeEasing)))
                                        .togetherWith(
                                            slideOutHorizontally(tween(animDuration, easing = nativeEasing)) { -it / 3 } +
                                                    fadeOut(tween(animDuration / 2)) +
                                                    scaleOut(targetScale = 0.95f, animationSpec = tween(animDuration, easing = nativeEasing))
                                        )
                                } else if (initialState != Route.HOME && targetState == Route.HOME) {
                                    (slideInHorizontally(tween(animDuration, easing = nativeEasing)) { -it / 3 } +
                                            fadeIn(tween(animDuration)) +
                                            scaleIn(initialScale = 0.95f, animationSpec = tween(animDuration, easing = nativeEasing)))
                                        .togetherWith(
                                            slideOutHorizontally(tween(animDuration, easing = nativeEasing)) { it } +
                                                    fadeOut(tween(animDuration / 2)) +
                                                    scaleOut(targetScale = 0.92f, animationSpec = tween(animDuration, easing = nativeEasing))
                                        )
                                } else {
                                    fadeIn(tween(animDuration)) togetherWith fadeOut(tween(animDuration))
                                }
                            },
                            label = "screen_transition",
                            modifier = Modifier
                                .fillMaxSize()
                                .layerBackdrop(backdrop)
                                .background(backgroundColor)
                        ) { targetTransitionRoute ->
                            val currentRoute = if (targetTransitionRoute == Route.HOME) lastRootRoute.value else targetTransitionRoute

                            Box(modifier = Modifier.fillMaxSize().background(backgroundColor)) {
                                when (currentRoute) {
                                    Route.UNINITIALIZED -> {}
                                    Route.ONBOARDING -> {
                                        BackHandler { finish() }
                                        OnboardingScreen(
                                            onComplete = {
                                                viewModel.setHasCompletedOnboarding(true)
                                                route = Route.HOME
                                            }
                                        )
                                    }
                                    Route.FEEL_EVERY_TAP -> {
                                        BackHandler { route = Route.HOME }
                                        FeelEveryTapScreen(
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
                                        EdgeHapticsFlowHost(
                                            edgeViewModel = edgeViewModel,
                                            isServiceEnabled = isServiceEnabled,
                                            onOpenAccessibilitySettings = ::openAccessibilitySettings,
                                            onBack = { route = Route.HOME },
                                        )
                                    }
                                    Route.HOME, Route.SETTINGS -> {
                                        val bottomTab = if (currentRoute == Route.HOME) BottomTab.HOME else BottomTab.SETTINGS
                                        SlidingBottomTabHost(
                                            selectedTab = bottomTab,
                                            modifier = Modifier.fillMaxSize(),
                                        ) { tab ->
                                            when (tab) {
                                                BottomTab.HOME -> HomeScreen(
                                                    onOpenFeelEveryTap = { route = Route.FEEL_EVERY_TAP },
                                                    onOpenEdgeHaptics = { route = Route.EDGE_HAPTICS },
                                                    onOpenTactileScrolling = { route = Route.TACTILE_SCROLLING },
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
                                            onOpenSourceCode = {
                                                val intent = Intent(
                                                    Intent.ACTION_VIEW,
                                                    "https://github.com/archieamas11/hapticks".toUri(),
                                                )
                                                context.startActivity(intent)
                                            },
                                        )
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
                            enter = slideInVertically(tween(animDuration, easing = nativeEasing)) { it / 2 } + fadeIn(tween(animDuration)),
                            exit = slideOutVertically(tween(animDuration, easing = nativeEasing)) { it / 2 } + fadeOut(tween(animDuration)),
                            modifier = Modifier.align(Alignment.BottomCenter)
                        ) {
                            val currentTab = if (route == Route.HOME) BottomTab.HOME else BottomTab.SETTINGS
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

    private fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
    }
}

@Composable
private fun EdgeHapticsFlowHost(
    edgeViewModel: EdgeHapticsViewModel,
    isServiceEnabled: Boolean,
    onOpenAccessibilitySettings: () -> Unit,
    onBack: () -> Unit,
) {
    val edgeSettings by edgeViewModel.settings.collectAsStateWithLifecycle()
    val edgeTestEvent by edgeViewModel.testEvent.collectAsStateWithLifecycle()
    val isLsposedXposedBridgeActive by edgeViewModel.isLsposedXposedBridgeActive.collectAsStateWithLifecycle()
    EdgeHapticsScreen(
        settings = edgeSettings,
        testEvent = edgeTestEvent,
        isServiceEnabled = isServiceEnabled,
        isLsposedXposedBridgeActive = isLsposedXposedBridgeActive,
        onA11yScrollBoundEdgeChange = edgeViewModel::setA11yScrollBoundEdge,
        onEdgeLsposedLibxposedPathChange = edgeViewModel::setEdgeLsposedLibxposedPath,
        onPatternSelected = edgeViewModel::setEdgePattern,
        onIntensityCommit = edgeViewModel::setEdgeIntensity,
        onTestEdgeHaptic = edgeViewModel::testEdgeHaptic,
        onTestEventConsumed = edgeViewModel::consumeTestEvent,
        onOpenAccessibilitySettings = onOpenAccessibilitySettings,
        onBack = onBack,
    )
}
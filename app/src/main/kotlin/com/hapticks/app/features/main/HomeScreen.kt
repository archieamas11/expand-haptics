package com.hapticks.app.features.main

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hapticks.app.R
import com.hapticks.app.core.ui.extensions.hapticClickable
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeProgressive
import dev.chrisbanes.haze.blur.blurEffect
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenFeelEveryTap: () -> Unit,
    onOpenEdgeHaptics: () -> Unit,
    onOpenTactileScrolling: () -> Unit,
    onOpenChargeHaptics: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val topAppBarState = rememberTopAppBarState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(topAppBarState)
    val hazeState = remember { HazeState() }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            val collapsedFraction = scrollBehavior.state.collapsedFraction
            val isScrolled = collapsedFraction > 0f

            LargeTopAppBar(
                modifier = Modifier.hazeEffect(state = hazeState) {
                    if (isScrolled) {
                        blurEffect {
                            blurRadius = 12.dp
                            progressive = HazeProgressive.verticalGradient(
                                startIntensity = 1f,
                                endIntensity = 0f,
                            )
                        }
                    }
                },
                title = {
                    if (collapsedFraction > 0.5f) {
                        Text(
                            text = stringResource(id = R.string.app_name),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    } else {
                        HomeHeader()
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent,
                ),
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(state = hazeState)
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(horizontal = 20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                FeatureCard(
                    title = stringResource(id = R.string.home_feel_every_tap_title),
                    subtitle = stringResource(id = R.string.home_feel_every_tap_subtitle),
                    icon = R.drawable.touch_long_24px,
                    accent = MaterialTheme.colorScheme.secondaryContainer,
                    onAccent = MaterialTheme.colorScheme.onSecondaryContainer,
                    iconBg = MaterialTheme.colorScheme.secondary,
                    iconTint = MaterialTheme.colorScheme.onSecondary,
                    onClick = onOpenFeelEveryTap,
                )
                FeatureCard(
                    title = stringResource(id = R.string.home_tactile_scrolling_title),
                    subtitle = stringResource(id = R.string.home_tactile_scrolling_subtitle),
                    icon = R.drawable.swipe_up_24px,
                    accent = MaterialTheme.colorScheme.secondaryContainer,
                    onAccent = MaterialTheme.colorScheme.onSecondaryContainer,
                    iconBg = MaterialTheme.colorScheme.secondary,
                    iconTint = MaterialTheme.colorScheme.onSecondary,
                    onClick = onOpenTactileScrolling,
                    isBeta = true,
                )
                FeatureCard(
                    title = stringResource(id = R.string.home_edge_haptics_title),
                    subtitle = stringResource(id = R.string.home_edge_haptics_subtitle),
                    icon = R.drawable.swipe_vertical_24px,
                    accent = MaterialTheme.colorScheme.secondaryContainer,
                    onAccent = MaterialTheme.colorScheme.onSecondaryContainer,
                    iconBg = MaterialTheme.colorScheme.secondary,
                    iconTint = MaterialTheme.colorScheme.onSecondary,
                    onClick = onOpenEdgeHaptics,
                    isBeta = true,
                )

                FeatureCard(
                    title = stringResource(id = R.string.charge_haptics_title),
                    subtitle = stringResource(id = R.string.charge_haptics_subtitle),
                    icon = R.drawable.charger_24px,
                    accent = MaterialTheme.colorScheme.secondaryContainer,
                    onAccent = MaterialTheme.colorScheme.onSecondaryContainer,
                    iconBg = MaterialTheme.colorScheme.secondary,
                    iconTint = MaterialTheme.colorScheme.onSecondary,
                    onClick = onOpenChargeHaptics,
                )

                FeatureCard(
                    title = stringResource(id = R.string.home_coming_soon_title),
                    subtitle = stringResource(id = R.string.home_coming_soon_subtitle),
                    icon = R.drawable.star_shine_24px,
                    accent = MaterialTheme.colorScheme.surfaceContainer,
                    onAccent = MaterialTheme.colorScheme.onSurface,
                    iconBg = MaterialTheme.colorScheme.surfaceContainerHighest,
                    iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                    enabled = false,
                    onClick = {},
                )
            }
            Spacer(modifier = Modifier.height(120.dp))
        }
    }
}

@Composable
private fun HomeHeader() {
    val junicodeFontFamily = remember { FontFamily(Font(R.font.junicode_italic)) }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(id = R.string.home_greeting),
            style = MaterialTheme.typography.labelLarge.copy(
                fontFamily = junicodeFontFamily,
                fontSize = 15.sp,
            ),
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = stringResource(id = R.string.app_name),
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

@Composable
private fun FeatureCard(
    title: String,
    subtitle: String,
    @DrawableRes icon: Int,
    accent: Color,
    onAccent: Color,
    iconBg: Color,
    iconTint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isBeta: Boolean = false,
) {
    val alpha = if (enabled) 1f else 0.65f

    Surface(
        color = accent,
        shape = RoundedCornerShape(28.dp),
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (enabled) Modifier.hapticClickable(onClick = onClick)
                else Modifier
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 22.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(
                        color = iconBg,
                        shape = RoundedCornerShape(18.dp)
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(id = icon),
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(28.dp),
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        color = onAccent.copy(alpha = alpha),
                    )

                    if (isBeta) {
                        BetaTag(
                            containerColor = iconBg,
                            contentColor = iconTint
                        )
                    }
                }

                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = onAccent.copy(alpha = alpha * 0.78f),
                )
            }

            if (enabled) {
                ChevronPill()
            }
        }
    }
}

@Composable
private fun BetaTag(
    containerColor: Color,
    contentColor: Color,
) {
    Surface(
        color = containerColor,
        shape = CircleShape,
    ) {
        Text(
            text = stringResource(id = R.string.feature_beta).uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            ),
            color = contentColor,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun ChevronPill() {
    Box(
        modifier = Modifier
            .size(40.dp)
            .background(
                color = MaterialTheme.colorScheme.primary,
                shape = CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.arrow_forward_24px),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(20.dp),
        )
    }
}


package com.hapticks.app.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.hapticks.app.ui.haptics.hapticClickable

enum class BottomTab { HOME, SETTINGS }

private val nativeEasing = FastOutSlowInEasing
private const val duration = 400

@Composable
fun SlidingBottomTabHost(
    selectedTab: BottomTab,
    modifier: Modifier = Modifier,
    content: @Composable (BottomTab) -> Unit,
) {
    val tabBg = MaterialTheme.colorScheme.background
    AnimatedContent(
        targetState = selectedTab,
        modifier = modifier.background(tabBg),
        transitionSpec = {
            val direction = if (targetState.ordinal > initialState.ordinal) 1 else -1
            (slideInHorizontally(tween(duration, easing = nativeEasing)) { direction * it / 4 } +
                    fadeIn(tween(duration)) +
                    scaleIn(initialScale = 0.95f, animationSpec = tween(duration, easing = nativeEasing)))
                .togetherWith(
                    slideOutHorizontally(tween(duration, easing = nativeEasing)) { -direction * it / 4 } +
                            fadeOut(tween(duration / 2)) +
                            scaleOut(targetScale = 0.95f, animationSpec = tween(duration, easing = nativeEasing))
                )
        },
        label = "slidingBottomTabHost",
    ) { tab ->
        Box(
            Modifier
                .fillMaxSize()
                .background(tabBg),
        ) {
            content(tab)
        }
    }
}

@Composable
fun FloatingBottomBar(
    selectedTab: BottomTab,
    onTabSelected: (BottomTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .padding(bottom = 25.dp)
            .height(64.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.8f),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxHeight()
                .padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BottomTabItem(
                selected = selectedTab == BottomTab.HOME,
                icon = Icons.Rounded.Home,
                label = "Home",
                onClick = { onTabSelected(BottomTab.HOME) },
            )
            BottomTabItem(
                selected = selectedTab == BottomTab.SETTINGS,
                icon = Icons.Rounded.Settings,
                label = "Settings",
                onClick = { onTabSelected(BottomTab.SETTINGS) },
            )
        }
    }
}

@Composable
private fun BottomTabItem(
    selected: Boolean,
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    val tabWidth = 100.dp

    val containerColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        animationSpec = tween(250, easing = nativeEasing),
        label = "bottomBarContainerColor",
    )

    val contentColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(250, easing = nativeEasing),
        label = "bottomBarContentColor",
    )

    val horizontalPadding by animateDpAsState(
        targetValue = if (selected) 24.dp else 20.dp,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f),
        label = "bottomBarHorizontalPadding",
    )

    Box(
        modifier = Modifier
            .fillMaxHeight()
            .width(tabWidth)
            .clip(CircleShape)
            .background(containerColor)
            .hapticClickable(onClick = onClick)
            .padding(horizontal = horizontalPadding),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = contentColor,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = contentColor,
            )
        }
    }
}
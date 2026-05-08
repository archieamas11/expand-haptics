package com.hapticks.app.features.onboarding

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hapticks.app.R
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    onComplete: () -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { 3 })
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            OnboardingBottomBar(
                currentPage = pagerState.currentPage,
                pageCount = 3,
                onNext = {
                    if (pagerState.currentPage < 2) {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                        }
                    } else {
                        onComplete()
                    }
                }
            )
        }
    ) { paddingValues ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) { page ->
            val pageOffset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
            val absOffset = pageOffset.absoluteValue

            val alpha by animateFloatAsState(
                targetValue = 1f - (absOffset * 0.8f).coerceIn(0f, 1f),
                label = "alpha"
            )
            val scale by animateFloatAsState(
                targetValue = 1f - (absOffset * 0.2f).coerceIn(0f, 1f),
                label = "scale"
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        this.alpha = alpha
                        this.scaleX = scale
                        this.scaleY = scale
                    },
                contentAlignment = Alignment.Center
            ) {
                when (page) {
                    0 -> OnboardingPage(
                        iconRes = R.drawable.touch_long_24px,
                        title = "Feel the Difference",
                        description = "Make your phone feel tactile and responsive with satisfying haptic feedback."
                    )

                    1 -> OnboardingPage(
                        iconRes = R.drawable.shield_24px,
                        title = "Privacy-First",
                        description = "Hapticks uses Accessibility Services strictly to trigger feedback. Your data is private and never leaves your device."
                    )

                    2 -> OnboardingPage(
                        iconRes = R.drawable.mobile_vibrate_24px,
                        title = "Ready to Vibe?",
                        description = "Jump into the dashboard to customize your tactile experience."
                    )
                }
            }
        }
    }
}

@Composable
private fun OnboardingPage(
    iconRes: Int,
    title: String,
    description: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(120.dp)
        ) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(28.dp)
            )
        }

        Spacer(modifier = Modifier.height(48.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = description,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun OnboardingBottomBar(
    currentPage: Int,
    pageCount: Int,
    onNext: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            repeat(pageCount) { index ->
                val isSelected = currentPage == index
                val width by animateFloatAsState(
                    targetValue = if (isSelected) 24f else 8f,
                    animationSpec = spring(stiffness = Spring.StiffnessLow),
                    label = "indicator_width"
                )
                val color = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                }

                Box(
                    modifier = Modifier
                        .height(8.dp)
                        .width(width.dp)
                        .clip(CircleShape)
                        .background(color)
                )
            }
        }

        FloatingActionButton(
            onClick = onNext,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp)
        ) {
            Icon(
                painter = painterResource(if (currentPage == pageCount - 1) R.drawable.check_24px else R.drawable.arrow_forward_24px),
                contentDescription = if (currentPage == pageCount - 1) "Start" else "Next"
            )
        }
    }
}


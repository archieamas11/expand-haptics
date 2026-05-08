package com.hapticks.app.core.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.hapticks.app.R

@Composable
fun HapticTestButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val currentOnClick by rememberUpdatedState(onClick)

    FloatingActionButton(
        onClick = { if (enabled) currentOnClick() },
        modifier = modifier.size(64.dp),
        shape = RoundedCornerShape(16.dp),
        containerColor = if (enabled) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        contentColor = if (enabled) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        elevation = FloatingActionButtonDefaults.elevation(
            defaultElevation = 0.dp,
            pressedElevation = 0.dp,
        ),
    ) {
        Icon(
            painter = painterResource(R.drawable.play_arrow_24px),
            contentDescription = null,
            modifier = Modifier.size(32.dp),
        )
    }
}
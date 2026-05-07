package com.hapticks.app.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hapticks.app.R
import com.hapticks.app.core.ui.extensions.rememberHapticClickAction

@Composable
fun BackPill(onBack: () -> Unit) {
    val onBackWithHaptics = rememberHapticClickAction(onBack)

    Box(
        modifier = Modifier
            .padding(start = 12.dp, top = 8.dp, bottom = 8.dp)
            .size(44.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center
    ) {
        IconButton(
            onClick = onBackWithHaptics,
            modifier = Modifier.size(44.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = stringResource(id = R.string.back),
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}
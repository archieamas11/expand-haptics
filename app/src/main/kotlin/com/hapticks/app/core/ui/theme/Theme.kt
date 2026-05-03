package com.hapticks.app.core.ui.theme

import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import com.hapticks.app.data.model.ThemeMode

private val HapticksDarkColorScheme = darkColorScheme(
    primary = Color.White,
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF2E2E2E),
    onPrimaryContainer = Color(0xFFE3E3E3),
    secondary = Color(0xFFC6C6C6),
    onSecondary = Color(0xFF303030),
    secondaryContainer = Color(0xFF474747),
    onSecondaryContainer = Color(0xFFE3E3E3),
    tertiary = Color(0xFFE6E6E6),
    onTertiary = Color(0xFF262626),
    tertiaryContainer = Color(0xFF3D3D3D),
    onTertiaryContainer = Color(0xFFD9D9D9),
    background = Color(0xFF121212),
    onBackground = Color(0xFFE3E3E3),
    surface = Color(0xFF121212),
    onSurface = Color(0xFFE3E3E3),
    surfaceVariant = Color(0xFF444444),
    onSurfaceVariant = Color(0xFFC4C4C4),
    surfaceContainerLowest = Color(0xFF0F0F0F),
    surfaceContainerLow = Color(0xFF1D1D1D),
    surfaceContainer = Color(0xFF212121),
    surfaceContainerHigh = Color(0xFF2B2B2B),
    surfaceContainerHighest = Color(0xFF363636),
    outline = Color(0xFF8E8E8E),
    outlineVariant = Color(0xFF444444),
)

private val HapticksLightColorScheme = lightColorScheme(
    primary = Color.Black,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE6E6E6),
    onPrimaryContainer = Color(0xFF1A1A1A),
    secondary = Color(0xFF5E5E5E),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE8E8E8),
    onSecondaryContainer = Color(0xFF1C1C1C),
    tertiary = Color(0xFF404040),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD9D9D9),
    onTertiaryContainer = Color(0xFF141414),
    background = Color(0xFFF9F9F9),
    onBackground = Color(0xFF1C1C1C),
    surface = Color(0xFFF9F9F9),
    onSurface = Color(0xFF1C1C1C),
    surfaceVariant = Color(0xFFE0E0E0),
    onSurfaceVariant = Color(0xFF494949),
    outline = Color(0xFF7A7A7A),
)

private fun ColorScheme.withAmoledSurfaces(): ColorScheme {
    val black = Color.Black
    return copy(
        background = black,
    )
}

@Composable
fun HapticksTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    useDynamicColors: Boolean = true,
    amoledBlack: Boolean = false,
    seedColor: Int? = null,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val context = LocalContext.current
    val baseColorScheme = remember(darkTheme, useDynamicColors, seedColor, context) {
        when {
            useDynamicColors && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            }

            darkTheme -> {
                HapticksDarkColorScheme
            }

            else -> {
                HapticksLightColorScheme
            }
        }
    }
    val colorScheme = remember(baseColorScheme, amoledBlack, darkTheme) {
        if (amoledBlack && darkTheme) baseColorScheme.withAmoledSurfaces() else baseColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val activity = view.context as? ComponentActivity ?: return@SideEffect
            activity.enableEdgeToEdge(
                statusBarStyle = SystemBarStyle.auto(
                    android.graphics.Color.TRANSPARENT,
                    android.graphics.Color.TRANSPARENT,
                ) { darkTheme },
                navigationBarStyle = SystemBarStyle.auto(
                    android.graphics.Color.TRANSPARENT,
                    android.graphics.Color.TRANSPARENT,
                ) { darkTheme }
            )
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = HapticksTypography,
        shapes = HapticksShapes,
        content = content,
    )
}


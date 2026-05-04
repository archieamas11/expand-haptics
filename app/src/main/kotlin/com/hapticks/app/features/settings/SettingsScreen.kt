package com.hapticks.app.features.settings

import android.content.ComponentName
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AccessibilityNew
import androidx.compose.material.icons.rounded.Brightness6
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material3.Button
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import com.hapticks.app.BuildConfig
import com.hapticks.app.R
import com.hapticks.app.core.ui.components.HapticToggleRow
import com.hapticks.app.core.ui.extensions.hapticClickable
import com.hapticks.app.core.ui.extensions.performHapticDoubleClick
import com.hapticks.app.data.model.AppSettings
import com.hapticks.app.data.model.ThemeMode
import com.hapticks.app.features.update.LatestRelease
import com.hapticks.app.features.update.fetchReleaseForVersion
import com.hapticks.app.service.accessibility.HapticsAccessibilityService
import dev.jeziellago.compose.markdowntext.MarkdownText
import kotlinx.coroutines.launch

private var cachedLatestRelease: LatestRelease? = null

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    onHapticsEnabledChange: (Boolean) -> Unit,
    onUseDynamicColorsChange: (Boolean) -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
    onAmoledBlackChange: (Boolean) -> Unit,
    onLiquidGlassChange: (Boolean) -> Unit,
    onOpenUpdateCheck: () -> Unit,
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var isChangelogModalVisible by rememberSaveable { mutableStateOf(false) }
    var changelogUiState by remember {
        mutableStateOf(
            cachedLatestRelease?.let { ChangelogUiState.Success(it) } ?: ChangelogUiState.Idle
        )
    }
    val appInDarkTheme = when (settings.themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    fun loadInstalledReleaseChangelog() {
        if (changelogUiState is ChangelogUiState.Success || changelogUiState is ChangelogUiState.Loading) return

        scope.launch {
            changelogUiState = ChangelogUiState.Loading
            val release = fetchReleaseForVersion(BuildConfig.VERSION_NAME)
            if (release != null) {
                cachedLatestRelease = release
                changelogUiState = ChangelogUiState.Success(release)
            } else {
                changelogUiState = ChangelogUiState.Error
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp)
                .padding(top = padding.calculateTopPadding() + 20.dp)
        ) {
            SettingsHeader()
            Spacer(modifier = Modifier.height(5.dp))

            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(
                    bottom = padding.calculateBottomPadding() + 20.dp
                ),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item(key = "appearance") {
                    SettingsSection(
                        title = stringResource(R.string.settings_section_appearance),
                        icon = Icons.Rounded.Palette,
                    ) {
                        HapticToggleRow(
                            title = stringResource(R.string.settings_dynamic_color_title),
                            subtitle = stringResource(R.string.settings_dynamic_color_subtitle),
                            checked = settings.useDynamicColors,
                            onCheckedChange = onUseDynamicColorsChange,
                        )

                        RowDivider()

                        HapticToggleRow(
                            title = stringResource(R.string.settings_amoled_title),
                            subtitle = if (appInDarkTheme) {
                                stringResource(R.string.settings_amoled_subtitle)
                            } else {
                                stringResource(R.string.settings_amoled_subtitle_light)
                            },
                            checked = settings.amoledBlack,
                            onCheckedChange = onAmoledBlackChange,
                        )

                        RowDivider()

                        HapticToggleRow(
                            title = stringResource(R.string.settings_liquid_glass_title),
                            subtitle = stringResource(R.string.settings_liquid_glass_subtitle),
                            checked = settings.liquidGlass,
                            onCheckedChange = onLiquidGlassChange,
                        )

                        RowDivider()

                        ThemeModeRow(
                            selected = settings.themeMode,
                            onThemeModeChange = onThemeModeChange,
                        )
                    }
                }

                item(key = "haptic") {
                    SettingsSection(
                        title = stringResource(R.string.settings_toggle_haptics_header),
                        icon = Icons.Rounded.Vibration,
                    ) {
                        HapticToggleRow(
                            title = stringResource(R.string.settings_toggle_haptics_title),
                            subtitle = stringResource(R.string.settings_toggle_haptics_subtitle),
                            checked = settings.hapticsEnabled,
                            onCheckedChange = onHapticsEnabledChange,
                        )
                    }
                }

                item(key = "about") {
                    SettingsSection(
                        title = stringResource(R.string.settings_section_about),
                        icon = Icons.Rounded.Settings,
                    ) {

                        SettingsRow(
                            title = stringResource(R.string.settings_version_title),
                            subtitle = stringResource(
                                R.string.settings_version_subtitle,
                                BuildConfig.VERSION_NAME,
                                BuildConfig.VERSION_CODE,
                            ),
                            position = RowPosition.Top,
                            onClick = {
                                isChangelogModalVisible = true
                                loadInstalledReleaseChangelog()
                            },
                            trailing = {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp),
                                )
                            },
                        )
                        RowDivider()

                        SettingsRow(
                            title = stringResource(R.string.settings_developer_title),
                            subtitle = stringResource(R.string.settings_developer_subtitle),
                            position = RowPosition.Middle,
                            onClick = {
                                val intent = Intent(
                                    Intent.ACTION_VIEW,
                                    "https://github.com/archieamas11".toUri(),
                                )
                                context.startActivity(intent)
                            },
                            trailing = {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp),
                                )
                            },
                        )

                        RowDivider()

                        SettingsRow(
                            title = stringResource(R.string.settings_report_bug_title),
                            subtitle = stringResource(R.string.settings_report_bug_subtitle),
                            position = RowPosition.Middle,
                            onClick = {
                                val intent = Intent(
                                    Intent.ACTION_VIEW,
                                    "https://t.me/ricosixnine".toUri(),
                                )
                                context.startActivity(intent)
                            },
                            trailing = {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp),
                                )
                            },
                        )

                        RowDivider()

                        SettingsRow(
                            title = stringResource(R.string.settings_github_title),
                            subtitle = stringResource(R.string.settings_github_subtitle),
                            position = RowPosition.Bottom,
                            onClick = {
                                val intent = Intent(
                                    Intent.ACTION_VIEW,
                                    "https://github.com/archieamas11/expand-haptics".toUri(),
                                )
                                context.startActivity(intent)
                            },
                            trailing = {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp),
                                )
                            },
                        )

                        RowDivider()

                        SettingsRow(
                            title = stringResource(R.string.settings_check_updates_title),
                            subtitle = stringResource(
                                R.string.settings_check_updates_subtitle,
                            ),
                            position = RowPosition.Bottom,
                            onClick = onOpenUpdateCheck,
                            trailing = {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp),
                                )
                            },
                        )
                    }
                }

                item(key = "accessibility") {
                    SettingsSection(
                        title = stringResource(R.string.settings_accessibility),
                        icon = Icons.Rounded.AccessibilityNew,
                    ) {
                        SettingsRow(
                            title = stringResource(R.string.settings_accessibility_title),
                            subtitle = stringResource(R.string.settings_accessibility_subtitle),
                            position = RowPosition.Single,
                            onClick = {
                                val compName =
                                    ComponentName(context, HapticsAccessibilityService::class.java)
                                val intent =
                                    Intent("android.settings.ACCESSIBILITY_DETAILS_SETTINGS").apply {
                                        data = "package:${context.packageName}".toUri()
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                        putExtra(
                                            ":settings:fragment_args_key",
                                            compName.flattenToShortString()
                                        )
                                    }
                                try {
                                    context.startActivity(intent)
                                } catch (_: Exception) {
                                    val fallbackIntent =
                                        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                        }
                                    context.startActivity(fallbackIntent)
                                }
                            },
                        )
                    }
                }

                item(key = "bottom_inset") {
                    Spacer(modifier = Modifier.height(96.dp))
                }
            }
        }
    }

    if (isChangelogModalVisible) {
        ChangelogModal(
            uiState = changelogUiState,
            onDismiss = { isChangelogModalVisible = false },
            onRetry = { loadInstalledReleaseChangelog() },
            onOpenRelease = { releaseUrl ->
                val intent = Intent(Intent.ACTION_VIEW, releaseUrl.toUri())
                context.startActivity(intent)
            },
        )
    }
}

private sealed interface ChangelogUiState {
    data object Idle : ChangelogUiState
    data object Loading : ChangelogUiState
    data class Success(val release: LatestRelease) : ChangelogUiState
    data object Error : ChangelogUiState
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ChangelogModal(
    uiState: ChangelogUiState,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    onOpenRelease: (String) -> Unit,
) {
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
                    text = stringResource(R.string.settings_changelog_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                when (uiState) {
                    ChangelogUiState.Idle,
                    ChangelogUiState.Loading -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            CircularWavyProgressIndicator(
                                modifier = Modifier.size(20.dp),
                            )
                            Text(
                                text = stringResource(R.string.settings_changelog_loading),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    ChangelogUiState.Error -> {
                        Text(
                            text = stringResource(R.string.settings_changelog_error),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TextButton(onClick = onRetry) {
                            Text(text = stringResource(R.string.settings_changelog_retry))
                        }
                    }

                    is ChangelogUiState.Success -> {
                        Text(
                            text = uiState.release.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        MarkdownText(
                            markdown = uiState.release.body,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 360.dp)
                                .verticalScroll(rememberScrollState()),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            linkColor = MaterialTheme.colorScheme.primary,
                        )
                        TextButton(onClick = { onOpenRelease(uiState.release.url) }) {
                            Text(text = stringResource(R.string.settings_changelog_open_release))
                        }
                    }
                }

                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                ) {
                    Text(text = stringResource(R.string.settings_changelog_close))
                }
            }
        }
    }
}

@Composable
private fun SettingsHeader() {
    val junicodeFontFamily = remember { FontFamily(Font(R.font.junicode_italic)) }

    Column {
        Text(
            text = stringResource(R.string.settings_header_caption),
            style = MaterialTheme.typography.labelLarge.copy(
                fontFamily = junicodeFontFamily,
                fontSize = 15.sp,
            ),
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = stringResource(R.string.settings_header_title),
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(bottom = 10.dp)
        )
    }
}

@Composable
private fun SettingsSection(
    title: String,
    icon: ImageVector,
    content: @Composable () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Column { content() }
            }
        }
    }
}

private enum class RowPosition { Top, Middle, Bottom, Single }

@Composable
private fun SettingsRow(
    title: String,
    subtitle: String? = null,
    position: RowPosition = RowPosition.Middle,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    val verticalPadding = when (position) {
        RowPosition.Top -> PaddingValues(top = 14.dp, bottom = 10.dp)
        RowPosition.Middle -> PaddingValues(vertical = 10.dp)
        RowPosition.Bottom -> PaddingValues(top = 10.dp, bottom = 14.dp)
        RowPosition.Single -> PaddingValues(vertical = 14.dp)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) Modifier.hapticClickable(onClick = onClick) else Modifier,
            )
            .defaultMinSize(minHeight = 52.dp)
            .padding(horizontal = 14.dp)
            .padding(verticalPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (trailing != null) {
            trailing()
        }
    }
}

@Composable
private fun RowDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThemeModeRow(
    selected: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            .padding(top = 12.dp, bottom = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = stringResource(R.string.settings_theme_mode_title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        val modes = listOf(
            ThemeModeOption(
                ThemeMode.SYSTEM,
                stringResource(R.string.settings_theme_mode_system),
                Icons.Rounded.Brightness6
            ),
            ThemeModeOption(
                ThemeMode.LIGHT,
                stringResource(R.string.settings_theme_mode_light),
                Icons.Rounded.LightMode
            ),
            ThemeModeOption(
                ThemeMode.DARK,
                stringResource(R.string.settings_theme_mode_dark),
                Icons.Rounded.DarkMode
            ),
        )

        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            modes.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = selected == option.mode,
                    onClick = {
                        context.performHapticDoubleClick()
                        onThemeModeChange(option.mode)
                    },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size),
                    icon = {
                        Icon(
                            imageVector = option.icon,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                ) {
                    Text(option.label)
                }
            }
        }
    }
}

private data class ThemeModeOption(
    val mode: ThemeMode,
    val label: String,
    val icon: ImageVector,
)


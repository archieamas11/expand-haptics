package com.hapticks.app.ui.screens

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import com.hapticks.app.service.HapticsAccessibilityService
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AccessibilityNew
import androidx.compose.material.icons.rounded.Brightness6
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.hapticks.app.BuildConfig
import com.hapticks.app.R
import com.hapticks.app.data.HapticsSettings
import com.hapticks.app.data.ThemeMode
import com.hapticks.app.ui.haptics.hapticClickable
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: HapticsSettings,
    onUseDynamicColorsChange: (Boolean) -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
    onAmoledBlackChange: (Boolean) -> Unit,
    onLiquidGlassChange: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var isChangelogModalVisible by rememberSaveable { mutableStateOf(false) }
    var changelogUiState by remember { mutableStateOf<ChangelogUiState>(ChangelogUiState.Idle) }
    val appInDarkTheme = when (settings.themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    fun loadLatestRelease() {
        scope.launch {
            changelogUiState = ChangelogUiState.Loading
            changelogUiState = fetchLatestRelease()
        }
    }

    fun checkForUpdates() {
        scope.launch {
            Toast.makeText(
                context,
                context.getString(R.string.settings_check_updates_loading),
                Toast.LENGTH_SHORT,
            ).show()

            when (val result = fetchUpdateStatus()) {
                UpdateCheckResult.UpToDate -> {
                    Toast.makeText(
                        context,
                        context.getString(R.string.settings_check_updates_no_update),
                        Toast.LENGTH_SHORT,
                    ).show()
                }

                is UpdateCheckResult.UpdateAvailable -> {
                    val downloadUrl = result.release.apkDownloadUrl
                    if (downloadUrl.isNullOrBlank()) {
                        Toast.makeText(
                            context,
                            context.getString(R.string.settings_check_updates_missing_apk),
                            Toast.LENGTH_LONG,
                        ).show()
                        return@launch
                    }

                    startApkDownload(
                        context = context,
                        release = result.release,
                        apkUrl = downloadUrl,
                    )
                    Toast.makeText(
                        context,
                        context.getString(
                            R.string.settings_check_updates_downloading,
                            result.release.tagName
                        ),
                        Toast.LENGTH_LONG,
                    ).show()
                }

                UpdateCheckResult.Error -> {
                    Toast.makeText(
                        context,
                        context.getString(R.string.settings_check_updates_error),
                        Toast.LENGTH_LONG,
                    ).show()
                }
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
                        SettingsRow(
                            title = stringResource(R.string.settings_dynamic_color_title),
                            subtitle = stringResource(R.string.settings_dynamic_color_subtitle),
                            position = RowPosition.Top,
                            trailing = {
                                Switch(
                                    checked = settings.useDynamicColors,
                                    onCheckedChange = onUseDynamicColorsChange,
                                )
                            },
                        )

                        SettingsRow(
                            title = stringResource(R.string.settings_amoled_title),
                            subtitle = if (appInDarkTheme) {
                                stringResource(R.string.settings_amoled_subtitle)
                            } else {
                                stringResource(R.string.settings_amoled_subtitle_light)
                            },
                            position = RowPosition.Middle,
                            trailing = {
                                Switch(
                                    checked = settings.amoledBlack,
                                    onCheckedChange = onAmoledBlackChange,
                                )
                            },
                        )

                        SettingsRow(
                            title = stringResource(R.string.settings_liquid_glass_title),
                            subtitle = stringResource(R.string.settings_liquid_glass_subtitle),
                            position = RowPosition.Bottom,
                            trailing = {
                                Switch(
                                    checked = settings.liquidGlass,
                                    onCheckedChange = onLiquidGlassChange,
                                )
                            },
                        )

                        RowDivider()

                        ThemeModeRow(
                            selected = settings.themeMode,
                            onThemeModeChange = onThemeModeChange,
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
                                loadLatestRelease()
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
                            onClick = { checkForUpdates() },
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
                                val compName = ComponentName(context, HapticsAccessibilityService::class.java)
                                val intent = Intent("android.settings.ACCESSIBILITY_DETAILS_SETTINGS").apply {
                                    data = "package:${context.packageName}".toUri()
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                    putExtra(":settings:fragment_args_key", compName.flattenToShortString())
                                }
                                try {
                                    context.startActivity(intent)
                                } catch (_: Exception) {
                                    val fallbackIntent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
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
            onRetry = { loadLatestRelease() },
            onOpenRelease = { releaseUrl ->
                val intent = Intent(Intent.ACTION_VIEW, releaseUrl.toUri())
                context.startActivity(intent)
            },
        )
    }
}

private data class LatestRelease(
    val title: String,
    val body: String,
    val tagName: String,
    val url: String,
    val apkDownloadUrl: String?,
)

private sealed interface ChangelogUiState {
    data object Idle : ChangelogUiState
    data object Loading : ChangelogUiState
    data class Success(val release: LatestRelease) : ChangelogUiState
    data object Error : ChangelogUiState
}

private sealed interface UpdateCheckResult {
    data object UpToDate : UpdateCheckResult
    data class UpdateAvailable(val release: LatestRelease) : UpdateCheckResult
    data object Error : UpdateCheckResult
}

private suspend fun fetchLatestRelease(): ChangelogUiState = withContext(Dispatchers.IO) {
    val endpoint = "https://api.github.com/repos/archieamas11/hapticks/releases/latest"
    val connection = (URL(endpoint).openConnection() as? HttpURLConnection)
        ?: return@withContext ChangelogUiState.Error

    try {
        connection.requestMethod = "GET"
        connection.connectTimeout = 10_000
        connection.readTimeout = 10_000
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        connection.setRequestProperty("User-Agent", "Hapticks-Android")

        val statusCode = connection.responseCode
        if (statusCode !in 200..299) {
            return@withContext ChangelogUiState.Error
        }

        val response = connection.inputStream.bufferedReader().use { it.readText() }
        val json = JSONObject(response)
        val title = json.optString("name").ifBlank { json.optString("tag_name", "Latest release") }
        val body = json.optString("body").ifBlank { "No changelog details were provided." }
        val tagName = json.optString("tag_name")
        val url = json.optString("html_url")
        val apkAssetUrl = json.optJSONArray("assets")
            ?.let { assets ->
                (0 until assets.length())
                    .asSequence()
                    .mapNotNull { index -> assets.optJSONObject(index) }
                    .firstOrNull { asset ->
                        asset.optString("name").endsWith(".apk", ignoreCase = true)
                    }
                    ?.optString("browser_download_url")
            }
        if (url.isBlank()) {
            return@withContext ChangelogUiState.Error
        }

        ChangelogUiState.Success(
            LatestRelease(
                title = title,
                body = body,
                tagName = tagName,
                url = url,
                apkDownloadUrl = apkAssetUrl,
            )
        )
    } catch (_: Exception) {
        ChangelogUiState.Error
    } finally {
        connection.disconnect()
    }
}

private suspend fun fetchUpdateStatus(): UpdateCheckResult = withContext(Dispatchers.IO) {
    when (val changelogState = fetchLatestRelease()) {
        is ChangelogUiState.Success -> {
            val remoteTag = changelogState.release.tagName
            val isRemoteNewer = isRemoteVersionNewer(
                remoteVersion = remoteTag,
                currentVersion = BuildConfig.VERSION_NAME,
            )
            if (isRemoteNewer) {
                UpdateCheckResult.UpdateAvailable(changelogState.release)
            } else {
                UpdateCheckResult.UpToDate
            }
        }

        else -> UpdateCheckResult.Error
    }
}

private fun isRemoteVersionNewer(remoteVersion: String, currentVersion: String): Boolean {
    val remoteParts = parseVersionParts(remoteVersion)
    val currentParts = parseVersionParts(currentVersion)
    if (remoteParts.isEmpty() || currentParts.isEmpty()) {
        return remoteVersion.trim() != currentVersion.trim()
    }

    val maxLength = maxOf(remoteParts.size, currentParts.size)
    for (index in 0 until maxLength) {
        val remote = remoteParts.getOrElse(index) { 0 }
        val current = currentParts.getOrElse(index) { 0 }
        if (remote > current) return true
        if (remote < current) return false
    }
    return false
}

private fun parseVersionParts(rawVersion: String): List<Int> {
    val normalized = rawVersion.trim().removePrefix("v").removePrefix("V")
    return normalized
        .split(".")
        .mapNotNull { part -> part.toIntOrNull() }
}

private fun startApkDownload(
    context: Context,
    release: LatestRelease,
    apkUrl: String,
) {
    val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager ?: return
    val fileName = "hapticks-${release.tagName}.apk"
    val request = DownloadManager.Request(apkUrl.toUri())
        .setTitle(context.getString(R.string.app_name))
        .setDescription(context.getString(R.string.settings_check_updates_download_description))
        .setMimeType("application/vnd.android.package-archive")
        .setAllowedOverMetered(true)
        .setAllowedOverRoaming(true)
        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)

    val downloadId = manager.enqueue(request)
    registerApkInstallReceiver(context, downloadId)
}

private fun registerApkInstallReceiver(context: Context, downloadId: Long) {
    val receiver = object : BroadcastReceiver() {
        override fun onReceive(receiverContext: Context, intent: Intent) {
            val completedId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
            if (completedId != downloadId) return

            runCatching { receiverContext.unregisterReceiver(this) }

            val manager =
                receiverContext.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager ?: return
            val apkUri = manager.getUriForDownloadedFile(downloadId) ?: return

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            runCatching { receiverContext.startActivity(installIntent) }
                .onFailure {
                    Toast.makeText(
                        receiverContext,
                        receiverContext.getString(R.string.settings_check_updates_install_failed),
                        Toast.LENGTH_LONG,
                    ).show()
                }
        }
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        context.registerReceiver(
            receiver,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            Context.RECEIVER_NOT_EXPORTED,
        )
    } else {
        @Suppress("DEPRECATION")
        context.registerReceiver(receiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChangelogModal(
    uiState: ChangelogUiState,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    onOpenRelease: (String) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
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
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
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
                    Text(
                        text = uiState.release.body,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = { onOpenRelease(uiState.release.url) }) {
                        Text(text = stringResource(R.string.settings_changelog_open_release))
                    }
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
                    onClick = { onThemeModeChange(option.mode) },
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
package com.hapticks.app.ui.screens

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Environment
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.hapticks.app.BuildConfig
import com.hapticks.app.R
import com.hapticks.app.ui.haptics.withDefaultHaptic
import com.hapticks.app.ui.components.BackPill
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URLEncoder
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

data class LatestRelease(
    val title: String,
    val body: String,
    val tagName: String,
    val url: String,
    val apkDownloadUrl: String?,
)

sealed interface UpdateCheckUiState {
    data object Idle : UpdateCheckUiState
    data object Loading : UpdateCheckUiState
    data object UpToDate : UpdateCheckUiState
    data class UpdateAvailable(val release: LatestRelease) : UpdateCheckUiState
    data object Error : UpdateCheckUiState
}

internal sealed interface UpdateCheckResult {
    data object UpToDate : UpdateCheckResult
    data class UpdateAvailable(val release: LatestRelease) : UpdateCheckResult
    data object Error : UpdateCheckResult
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateCheckScreen(
    uiState: UpdateCheckUiState,
    onBack: () -> Unit,
    onCheckForUpdates: () -> Unit,
    onOpenSourceCode: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val topAppBarState = rememberTopAppBarState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(topAppBarState)

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = colorScheme.background,
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.settings_check_updates_title),
                        style = MaterialTheme.typography.displaySmall.copy(
                            color = colorScheme.onBackground,
                            fontWeight = FontWeight.Normal
                        ),
                    )
                },
                navigationIcon = { BackPill(onBack = onBack) },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = colorScheme.background,
                    scrolledContainerColor = colorScheme.background,
                    titleContentColor = colorScheme.onBackground,
                    navigationIconContentColor = colorScheme.onBackground,
                ),
            )
        },
        bottomBar = {
            UpdateCheckBottomActions(
                uiState = uiState,
                onCheckForUpdates = onCheckForUpdates,
                onOpenSourceCode = onOpenSourceCode,
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = 24.dp,
                    top = padding.calculateTopPadding() + 4.dp,
                    end = 24.dp,
                    bottom = padding.calculateBottomPadding(),
                ),
        ) {
            when (uiState) {
                UpdateCheckUiState.Idle,
                UpdateCheckUiState.UpToDate -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        UpdateCheckEmptyState(
                            body = stringResource(R.string.settings_check_updates_no_update),
                        )
                    }
                }

                UpdateCheckUiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            CircularProgressIndicator(
                                color = colorScheme.primary,
                                strokeWidth = 3.dp,
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                text = stringResource(R.string.settings_check_updates_loading),
                                style = MaterialTheme.typography.bodyLarge,
                                color = colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                UpdateCheckUiState.Error -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        UpdateCheckEmptyState(
                            body = stringResource(R.string.settings_check_updates_error),
                        )
                    }
                }

                is UpdateCheckUiState.UpdateAvailable -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(120.dp)
                                .clip(CircleShape)
                                .background(colorScheme.primaryContainer.copy(alpha = 0.35f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Download,
                                contentDescription = null,
                                modifier = Modifier.size(56.dp),
                                tint = colorScheme.primary,
                            )
                        }

                        Text(
                            text = stringResource(
                                R.string.settings_check_updates_available_subtitle,
                                uiState.release.tagName
                            ),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontSize = 18.sp
                            ),
                            color = colorScheme.onBackground,
                            modifier = Modifier.padding(horizontal = 16.dp),
                            textAlign = TextAlign.Center,
                        )

                        Text(
                            text = uiState.release.title,
                            style = MaterialTheme.typography.titleSmall,
                            color = colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .clip(RoundedCornerShape(16.dp))
                                .background(colorScheme.surfaceContainerHigh)
                                .padding(16.dp)
                        ) {
                            Text(
                                text = markdownToAnnotatedString(uiState.release.body),
                                style = MaterialTheme.typography.bodyMedium,
                                color = colorScheme.onSurfaceVariant,
                                modifier = Modifier.verticalScroll(rememberScrollState()),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UpdateCheckBottomActions(
    uiState: UpdateCheckUiState,
    onCheckForUpdates: () -> Unit,
    onOpenSourceCode: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val openSourceCodeWithHaptic = withDefaultHaptic(onOpenSourceCode)
    val checkForUpdatesWithHaptic = withDefaultHaptic(onCheckForUpdates)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colorScheme.background)
            .imePadding()
            .padding(
                start = 24.dp,
                top = 8.dp,
                end = 24.dp,
                bottom = 24.dp,
            ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when (uiState) {
            is UpdateCheckUiState.UpdateAvailable -> {
                uiState.release.apkDownloadUrl?.let { apkUrl ->
                    Button(
                        onClick = withDefaultHaptic {
                            startApkDownload(context, uiState.release, apkUrl)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(28.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colorScheme.primary,
                            contentColor = colorScheme.onPrimary,
                        ),
                    ) {
                        Text(
                            text = stringResource(R.string.settings_check_updates_download_now),
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium
                            )
                        )
                    }
                }
                OutlinedButton(
                    onClick = openSourceCodeWithHaptic,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = colorScheme.primary,
                    ),
                    border = ButtonDefaults.outlinedButtonBorder.copy(
                        width = 1.5.dp
                    )
                ) {
                    Text(
                        text = stringResource(R.string.settings_check_updates_source_code),
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                    )
                }
            }

            UpdateCheckUiState.Loading -> {
                Spacer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(124.dp)
                )
            }

            else -> {
                OutlinedButton(
                    onClick = openSourceCodeWithHaptic,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = colorScheme.primary,
                    ),
                    border = ButtonDefaults.outlinedButtonBorder.copy(
                        width = 1.5.dp
                    )
                ) {
                    Text(
                        text = stringResource(R.string.settings_check_updates_source_code),
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                    )
                }

                Button(
                    onClick = checkForUpdatesWithHaptic,
                    enabled = uiState !is UpdateCheckUiState.Loading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colorScheme.primary,
                        contentColor = colorScheme.onPrimary,
                    ),
                ) {
                    if (uiState is UpdateCheckUiState.Loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = colorScheme.onPrimary,
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.settings_check_updates_action),
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UpdateCheckEmptyState(body: String) {
    val colorScheme = MaterialTheme.colorScheme
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp),
        modifier = Modifier.padding(vertical = 48.dp)
    ) {
        // Icon with accent background circle (matching screenshot style)
        Box(
            modifier = Modifier
                .size(140.dp)
                .clip(CircleShape)
                .background(colorScheme.primaryContainer.copy(alpha = 0.35f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Download,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = colorScheme.primary,
            )
        }

        Text(
            text = body,
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium
            ),
            color = colorScheme.onBackground,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}

internal suspend fun fetchLatestRelease(): LatestRelease? = withContext(Dispatchers.IO) {
    val endpoint = "https://api.github.com/repos/archieamas11/hapticks/releases/latest"
    fetchReleaseFromEndpoint(endpoint)
}

internal suspend fun fetchReleaseForVersion(versionName: String): LatestRelease? = withContext(Dispatchers.IO) {
    val normalizedVersion = versionName.trim().removePrefix("v").removePrefix("V")
    if (normalizedVersion.isBlank()) return@withContext null

    val candidateTags = listOf(
        normalizedVersion,
        "v$normalizedVersion",
    ).distinct()

    candidateTags.firstNotNullOfOrNull { candidateTag ->
        val encodedTag = URLEncoder.encode(candidateTag, StandardCharsets.UTF_8.toString())
        val endpoint = "https://api.github.com/repos/archieamas11/hapticks/releases/tags/$encodedTag"
        fetchReleaseFromEndpoint(endpoint)
    }
}

private fun fetchReleaseFromEndpoint(endpoint: String): LatestRelease? {
    val connection = (URL(endpoint).openConnection() as? HttpURLConnection)
        ?: return null

    return try {
        connection.requestMethod = "GET"
        connection.connectTimeout = 10_000
        connection.readTimeout = 10_000
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        connection.setRequestProperty("User-Agent", "Hapticks-Android")

        val statusCode = connection.responseCode
        if (statusCode !in 200..299) {
            return null
        }

        val response = connection.inputStream.bufferedReader().use { it.readText() }
        parseReleaseResponse(response)
    } catch (_: Exception) {
        null
    } finally {
        connection.disconnect()
    }
}

private fun parseReleaseResponse(response: String): LatestRelease? {
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
        return null
    }

    return LatestRelease(
        title = title,
        body = body,
        tagName = tagName,
        url = url,
        apkDownloadUrl = apkAssetUrl,
    )
}

internal suspend fun fetchUpdateStatus(): UpdateCheckResult = withContext(Dispatchers.IO) {
    val release = fetchLatestRelease()
    if (release != null) {
        val remoteTag = release.tagName
        val isRemoteNewer = isRemoteVersionNewer(
            remoteVersion = remoteTag,
            currentVersion = BuildConfig.VERSION_NAME,
        )
        if (isRemoteNewer) {
            UpdateCheckResult.UpdateAvailable(release)
        } else {
            UpdateCheckResult.UpToDate
        }
    } else {
        UpdateCheckResult.Error
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

fun startApkDownload(
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

internal fun markdownToAnnotatedString(markdown: String): AnnotatedString {
    val lines = markdown.replace("\r\n", "\n").split('\n')
    val builder = AnnotatedString.Builder()
    var inCodeFence = false

    lines.forEachIndexed { index, rawLine ->
        val line = rawLine.trimEnd()
        val isLastLine = index == lines.lastIndex

        when {
            line.trimStart().startsWith("```") -> {
                inCodeFence = !inCodeFence
            }

            inCodeFence -> {
                builder.withStyle(
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                    )
                ) {
                    append(line)
                }
            }

            line.startsWith("### ") -> {
                builder.withStyle(
                    SpanStyle(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                    )
                ) { append(line.removePrefix("### ").trim()) }
            }

            line.startsWith("## ") -> {
                builder.withStyle(
                    SpanStyle(
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                    )
                ) { append(line.removePrefix("## ").trim()) }
            }

            line.startsWith("# ") -> {
                builder.withStyle(
                    SpanStyle(
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                    )
                ) { append(line.removePrefix("# ").trim()) }
            }

            line.startsWith("- ") || line.startsWith("* ") -> {
                builder.append("• ")
                appendMarkdownInline(builder, line.drop(2))
            }

            line.matches(Regex("""^\d+\.\s+.*$""")) -> {
                appendMarkdownInline(builder, line)
            }

            else -> appendMarkdownInline(builder, line)
        }

        if (!isLastLine) builder.append("\n")
    }

    return builder.toAnnotatedString()
}

private fun appendMarkdownInline(builder: AnnotatedString.Builder, rawText: String) {
    val inlinePattern = Regex("""(\*\*[^*]+\*\*|`[^`]+`|\*[^\*]+\*)""")
    var cursor = 0

    inlinePattern.findAll(rawText).forEach { match ->
        if (match.range.first > cursor) {
            builder.append(rawText.substring(cursor, match.range.first))
        }

        val token = match.value
        when {
            token.startsWith("**") && token.endsWith("**") -> {
                builder.withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(token.removePrefix("**").removeSuffix("**"))
                }
            }

            token.startsWith("`") && token.endsWith("`") -> {
                builder.withStyle(
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                    )
                ) {
                    append(token.removePrefix("`").removeSuffix("`"))
                }
            }

            token.startsWith("*") && token.endsWith("*") -> {
                builder.withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                    append(token.removePrefix("*").removeSuffix("*"))
                }
            }

            else -> builder.append(token)
        }

        cursor = match.range.last + 1
    }

    if (cursor < rawText.length) {
        builder.append(rawText.substring(cursor))
    }
}
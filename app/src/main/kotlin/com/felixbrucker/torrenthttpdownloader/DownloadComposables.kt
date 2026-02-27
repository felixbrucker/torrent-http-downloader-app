package com.felixbrucker.torrenthttpdownloader

import android.app.DownloadManager
import android.content.Intent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.felixbrucker.torrenthttpdownloader.models.DownloadFile
import com.felixbrucker.torrenthttpdownloader.models.DownloadTask
import com.felixbrucker.torrenthttpdownloader.models.LocalDownloadState
import com.felixbrucker.torrenthttpdownloader.models.TorrentState
import java.util.Locale

@Composable
fun DownloadItem(task: DownloadTask, onRemove: () -> Unit) {
    var isExpanded by remember { mutableStateOf(true) }
    val isExpandable = task.state.ordinal >= TorrentState.STARTING_LOCAL_DOWNLOADS.ordinal

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .clickable {
                if (isExpandable) {
                    isExpanded = !isExpanded
                }
            }
            .animateContentSize()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StateIcon(state = task.state)
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = task.name, style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val animatedProgress by animateFloatAsState(
                            targetValue = task.overallProgress / 100f,
                            animationSpec = tween(durationMillis = 1000),
                            label = "overall progress"
                        )
                        LinearProgressIndicator(
                            progress = { animatedProgress },
                            modifier = Modifier.weight(1f),
                            drawStopIndicator = {}
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${task.overallProgress}%",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        StatItem(icon = Icons.Default.Info, text = task.state.name.replace("_", " ").lowercase())
                        if (task.state.ordinal < TorrentState.STARTING_LOCAL_DOWNLOADS.ordinal && task.rdState != null) {
                            StatItem(icon = Icons.Default.Info, text = "RD: ${task.rdState}")
                        }
                        if (task.overallSpeed > 0) {
                            StatItem(icon = Icons.Default.Speed, text = formatSpeed(task.overallSpeed))
                        }
                        StatItem(
                            icon = Icons.Default.DataUsage,
                            text = "${formatBytes(task.downloadedBytes)} / ${formatBytes(task.totalBytes)}"
                        )
                        if (task.overallSpeed > 0) {
                            val remainingBytes = task.totalBytes - task.downloadedBytes
                            val remainingTime =
                                if (task.overallSpeed > 0) remainingBytes / task.overallSpeed else 0
                            StatItem(icon = Icons.Default.Timer, text = formatTime(remainingTime))
                        }
                    }
                    if (task.errorMessage != null) {
                        Text(
                            text = "Error: ${task.errorMessage}",
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(16.dp))
                IconButton(onClick = onRemove) {
                    Icon(Icons.Default.Delete, contentDescription = "Remove")
                }
                if (isExpandable) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (isExpanded) "Collapse" else "Expand"
                    )
                }
            }

            if (isExpandable && isExpanded) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    task.files
                        .filter { it.state != LocalDownloadState.COMPLETED }
                        .forEach { file ->
                            SubDownloadItem(task = task, file = file)
                        }
                }
            }
        }
    }
}

@Composable
fun StateIcon(state: TorrentState) {
    val icon = when (state) {
        TorrentState.WAITING_FOR_FILE_SELECTION -> Icons.Default.HourglassEmpty
        TorrentState.WAITING_FOR_REAL_DEBRID_DOWNLOAD,
        TorrentState.STARTING_LOCAL_DOWNLOADS,
        TorrentState.WAITING_FOR_LOCAL_DOWNLOADS -> Icons.Default.Download
        TorrentState.EXTRACTING_ARCHIVES -> Icons.Default.Unarchive
        TorrentState.MOVING_TO_DESTINATION -> Icons.AutoMirrored.Default.DriveFileMove
        TorrentState.COMPLETED -> Icons.Default.CheckCircle
        TorrentState.ERROR -> Icons.Default.Error
        else -> Icons.Default.HourglassEmpty
    }
    Icon(icon, contentDescription = state.name)
}

@Composable
fun StatItem(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
        Text(text = text, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun SubDownloadItem(task: DownloadTask, file: DownloadFile) {
    val context = LocalContext.current

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, top = 8.dp, end = 8.dp, bottom = 8.dp)
    ) {
        Row(
            modifier = Modifier.padding(8.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            LocalDownloadStateIcon(file.state)
            Column(modifier = Modifier.weight(1f)) {
                val name = file.filePath?.substringAfterLast('/') ?: file.link
                Text(text = name, style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val animatedProgress by animateFloatAsState(
                        targetValue = file.progress / 100f,
                        animationSpec = tween(durationMillis = 1000),
                        label = "file progress"
                    )
                    LinearProgressIndicator(
                        trackColor = MaterialTheme.colorScheme.surfaceContainer,
                        progress = { animatedProgress },
                        modifier = Modifier.weight(1f),
                        drawStopIndicator = {}
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${file.progress}%",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))

                val speed = file.speed
                val downloadedBytes = file.downloadedBytes
                val totalBytes = file.totalBytes

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    StatItem(icon = Icons.Default.Info, text = file.state.name.replace("_", " ").lowercase())
                    if (speed > 0) {
                        StatItem(icon = Icons.Default.Speed, text = formatSpeed(speed))
                    }
                    if (totalBytes > 0) {
                        StatItem(
                            icon = Icons.Default.DataUsage,
                            text = "${formatBytes(downloadedBytes)} / ${formatBytes(totalBytes)}"
                        )
                    }
                    if (speed > 0) {
                        val remainingBytes = totalBytes - downloadedBytes
                        val remainingTime = remainingBytes / speed
                        StatItem(icon = Icons.Default.Timer, text = formatTime(remainingTime))
                    }
                }
                if (file.stateDescription != null) {
                    Text(
                        text = file.stateDescription,
                        style = MaterialTheme.typography.bodySmall,
                        fontStyle = FontStyle.Italic
                    )
                }
            }

            if (file.state == LocalDownloadState.ERROR) {
                TextButton(onClick = {
                    val intent = Intent(context, DownloadService::class.java).apply {
                        action = DownloadService.ACTION_RETRY_FILE
                        putExtra(DownloadService.EXTRA_TASK_ID, task.id)
                        putExtra(DownloadService.EXTRA_FILE_LINK, file.link)
                    }
                    context.startService(intent)
                }) {
                    Text(text = "Retry")
                }
            } else if (file.state == LocalDownloadState.PAUSED) {
                TextButton(onClick = { context.startActivity(Intent(DownloadManager.ACTION_VIEW_DOWNLOADS)) }) {
                    Text(text = "Manage")
                }
            }
        }
    }
}

@Composable
fun LocalDownloadStateIcon(state: LocalDownloadState) {
    val icon = when (state) {
        LocalDownloadState.DOWNLOADING -> Icons.Default.Download
        LocalDownloadState.PAUSED -> Icons.Default.Pause
        LocalDownloadState.ERROR -> Icons.Default.Error
        LocalDownloadState.COMPLETED -> Icons.Default.CheckCircle
        LocalDownloadState.PENDING -> Icons.Default.HourglassEmpty
        else -> Icons.Default.Error
    }
    Icon(icon, contentDescription = state.name)
}

fun formatSpeed(speed: Long): String {
    if (speed < 1024) return "$speed B/s"
    val kb = speed / 1024
    if (kb < 1024) return "$kb KB/s"
    val mb = kb / 1024.0
    return String.format(Locale.US, "%.2f MB/s", mb)
}

fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024
    if (kb < 1024) return "$kb KB"
    val mb = kb / 1024.0
    if (mb < 1024) return String.format(Locale.US, "%.2f MB", mb)
    val gb = mb / 1024.0
    return String.format(Locale.US, "%.2f GB", gb)
}

fun formatTime(seconds: Long): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60
    return when {
        hours > 0 -> String.format(Locale.US, "%dh %dm %ds", hours, minutes, secs)
        minutes > 0 -> String.format(Locale.US, "%dm %ds", minutes, secs)
        else -> String.format(Locale.US, "%ds", secs)
    }
}

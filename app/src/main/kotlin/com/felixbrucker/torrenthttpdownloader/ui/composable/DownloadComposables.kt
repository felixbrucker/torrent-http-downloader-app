package com.felixbrucker.torrenthttpdownloader.ui.composable

import android.content.Intent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Block
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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.felixbrucker.torrenthttpdownloader.download.DownloadService
import com.felixbrucker.torrenthttpdownloader.download.DownloadService.Companion.ACTION_RESTART_TASK
import com.felixbrucker.torrenthttpdownloader.util.Formatter
import com.felixbrucker.torrenthttpdownloader.models.DownloadFile
import com.felixbrucker.torrenthttpdownloader.models.DownloadTask
import com.felixbrucker.torrenthttpdownloader.models.FileSelectionMode
import com.felixbrucker.torrenthttpdownloader.models.LocalDownloadState
import com.felixbrucker.torrenthttpdownloader.models.TaskLocation
import com.felixbrucker.torrenthttpdownloader.models.TorrentState
import com.felixbrucker.torrenthttpdownloader.providers.FilePriority
import com.felixbrucker.torrenthttpdownloader.providers.ProviderTorrentFile
import com.felixbrucker.torrenthttpdownloader.providers.ProviderTorrentFileState
import com.felixbrucker.torrenthttpdownloader.providers.ProviderTorrentState
import com.felixbrucker.torrenthttpdownloader.providers.TorrentProvider
import com.felixbrucker.torrenthttpdownloader.util.asStateText
import com.felixbrucker.torrenthttpdownloader.util.capitalized
import com.felixbrucker.torrenthttpdownloader.providers.ProviderFeature
import com.felixbrucker.torrenthttpdownloader.ui.icons.arrow_upload_progress
import com.felixbrucker.torrenthttpdownloader.ui.icons.downloading
import com.felixbrucker.torrenthttpdownloader.ui.icons.graph_3
import kotlin.math.roundToInt

@Composable
fun DownloadItem(
    task: DownloadTask,
    onRemove: () -> Unit,
    supportsPauseResume: Boolean = false,
    supportsPriorities: Boolean = false
) {
    val context = LocalContext.current
    var isExpanded by remember { mutableStateOf(false) }
    val isLocal = task.location == TaskLocation.LOCAL
    val hasUnfinishedLocalDownloads = isLocal && task.files.any { it.state != LocalDownloadState.COMPLETED }
    val isExpandable = hasUnfinishedLocalDownloads || (!isLocal && task.providerTorrentInfo?.files?.isNotEmpty() == true)

    val isDownloading = task.files.any { it.state == LocalDownloadState.DOWNLOADING || it.state == LocalDownloadState.PENDING }
    val isPaused = task.files.any { it.state == LocalDownloadState.PAUSED }
    val isManualSelectionMode = task.state == TorrentState.SELECTING_FILES && task.fileSelectionMode == FileSelectionMode.MANUAL

    val taskIcon: @Composable () -> Unit = {
        if (isManualSelectionMode && isExpanded) {
            val allSelected = task.providerTorrentInfo?.files?.all { it.isSelected } == true
            Icon(
                imageVector = if (allSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                contentDescription = if (allSelected) "Deselect All" else "Select All",
                tint = if (allSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                modifier = Modifier.clickable {
                    val intent = Intent(context, DownloadService::class.java).apply {
                        action = DownloadService.ACTION_TOGGLE_ALL_PROVIDER_FILE_SELECTION
                        putExtra(DownloadService.EXTRA_TASK_ID, task.id)
                        putExtra(DownloadService.EXTRA_SELECT_ALL, !allSelected)
                    }
                    context.startService(intent)
                }
            )
        } else {
            StateIcon(state = task.state)
        }
    }

    val actions: @Composable () -> Unit = {
        if (task.state == TorrentState.SELECTING_FILES && task.fileSelectionMode == FileSelectionMode.MANUAL) {
            IconButton(onClick = {
                val intent = Intent(context, DownloadService::class.java).apply {
                    action = DownloadService.ACTION_CONFIRM_FILE_SELECTION
                    putExtra(DownloadService.EXTRA_TASK_ID, task.id)
                }
                context.startService(intent)
            }) {
                Icon(Icons.Default.CheckCircle, contentDescription = "Confirm selection", tint = MaterialTheme.colorScheme.primary)
            }
        }

        if (task.location == TaskLocation.PROVIDER && supportsPauseResume) {
            if (task.providerTorrentInfo?.state == ProviderTorrentState.DOWNLOADING) {
                IconButton(onClick = {
                    val intent = Intent(context, DownloadService::class.java).apply {
                        action = DownloadService.ACTION_PAUSE_TASK_ON_PROVIDER
                        putExtra(DownloadService.EXTRA_TASK_ID, task.id)
                    }
                    context.startService(intent)
                }) {
                    Icon(Icons.Default.Pause, contentDescription = "Pause")
                }
            } else if (task.providerTorrentInfo?.state == ProviderTorrentState.PAUSED) {
                IconButton(onClick = {
                    val intent = Intent(context, DownloadService::class.java).apply {
                        action = DownloadService.ACTION_RESUME_TASK_ON_PROVIDER
                        putExtra(DownloadService.EXTRA_TASK_ID, task.id)
                    }
                    context.startService(intent)
                }) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Resume")
                }
            }
        }

        if (task.state == TorrentState.DOWNLOADING_LOCALLY) {
            if (isDownloading) {
                IconButton(onClick = {
                    val intent = Intent(context, DownloadService::class.java).apply {
                        action = DownloadService.ACTION_PAUSE_TASK_LOCAL_DOWNLOADS
                        putExtra(DownloadService.EXTRA_TASK_ID, task.id)
                    }
                    context.startService(intent)
                }) {
                    Icon(Icons.Default.Pause, contentDescription = "Pause")
                }
            } else if (isPaused) {
                IconButton(onClick = {
                    val intent = Intent(context, DownloadService::class.java).apply {
                        action = DownloadService.ACTION_RESUME_TASK_LOCAL_DOWNLOADS
                        putExtra(DownloadService.EXTRA_TASK_ID, task.id)
                    }
                    context.startService(intent)
                }) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Resume")
                }
            }
        }

        if (task.state == TorrentState.ERROR) {
            IconButton(onClick = {
                val intent = Intent(context, DownloadService::class.java).apply {
                    action = ACTION_RESTART_TASK
                    putExtra(DownloadService.EXTRA_TASK_ID, task.id)
                }
                context.startService(intent)
            }) {
                Icon(Icons.Default.Replay, contentDescription = "Restart")
            }
        }

        IconButton(onClick = onRemove) {
            Icon(Icons.Default.Delete, contentDescription = "Remove")
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .clickable {
                if (isExpandable) {
                    isExpanded = !isExpanded
                }
            }
            .animateContentSize(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        BoxWithConstraints {
            val isNarrow = maxWidth < 600.dp
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!isNarrow) {
                        taskIcon()
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isNarrow) {
                                taskIcon()
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text(
                                text = task.name,
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f),
                            )
                        }
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
                            StatItem(icon = Icons.Default.Info, text = task.state.name.asStateText())
                            if (task.location == TaskLocation.PROVIDER && task.providerTorrentInfo?.status != null) {
                                StatItem(icon = Icons.Default.Info, text = "Provider: ${task.providerTorrentInfo.status.asStateText()}")
                            }
                            if (task.overallDownloadSpeed > 0) {
                                StatItem(
                                    icon = downloading,
                                    text = Formatter.formatSpeed(task.overallDownloadSpeed)
                                )
                            }
                            if (task.providerUploadSpeed > 0) {
                                StatItem(
                                    icon = arrow_upload_progress,
                                    text = Formatter.formatSpeed(task.providerUploadSpeed)
                                )
                            }
                            StatItem(
                                icon = Icons.Default.DataUsage,
                                text = "${Formatter.formatBytes(task.downloadedBytes)} / ${Formatter.formatBytes(task.totalBytes)}"
                            )
                            if (task.overallDownloadSpeed > 0) {
                                val remainingBytes = task.totalBytes - task.downloadedBytes
                                val remainingTime =
                                    if (task.overallDownloadSpeed > 0) remainingBytes / task.overallDownloadSpeed else 0
                                StatItem(icon = Icons.Default.Timer, text = Formatter.formatTime(remainingTime))
                            }
                            if (task.providerTorrentInfo?.peers != null && task.providerTorrentInfo.totalPeers != null) {
                                StatItem(icon = graph_3, text = "${task.providerTorrentInfo.peers}/${task.providerTorrentInfo.totalPeers}")
                            }
                        }
                        if (task.errorMessage != null) {
                            Text(
                                text = "Error: ${task.errorMessage}",
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                        if (isNarrow) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                actions()
                                if (isExpandable) {
                                    Icon(
                                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        contentDescription = if (isExpanded) "Collapse" else "Expand"
                                    )
                                }
                            }
                        }
                    }
                    if (!isNarrow) {
                        Spacer(modifier = Modifier.width(16.dp))
                        actions()
                        if (isExpandable) {
                            Icon(
                                imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = if (isExpanded) "Collapse" else "Expand"
                            )
                        }
                    }
                }

                if (isExpandable && isExpanded) {
                    Column(modifier = Modifier.padding(top = 8.dp)) {
                        if (isLocal) {
                            task.files.forEach { file ->
                                if (file.state != LocalDownloadState.COMPLETED) {
                                    SubDownloadItem(task = task, file = file)
                                }
                            }
                        } else {
                            task.providerTorrentInfo?.files?.forEach { file ->
                                ProviderTorrentFileItem(
                                    taskId = task.id,
                                    file = file,
                                    supportsPriorities = supportsPriorities,
                                    isTorrentCompletedOnProvider = task.providerTorrentInfo.state == ProviderTorrentState.COMPLETED,
                                    isManualSelectionMode = isManualSelectionMode
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StateIcon(state: TorrentState) {
    val icon = when (state) {
        TorrentState.WAITING_FOR_PROVIDER_DOWNLOAD,
        TorrentState.DOWNLOADING_LOCALLY -> Icons.Default.Download
        TorrentState.EXTRACTING_ARCHIVES -> Icons.Default.Unarchive
        TorrentState.MOVING_TO_DESTINATION -> Icons.AutoMirrored.Default.DriveFileMove
        TorrentState.COMPLETED -> Icons.Default.CheckCircle
        TorrentState.ERROR -> Icons.Default.Error
        else -> Icons.Default.HourglassEmpty
    }
    Icon(icon, contentDescription = state.name)
}

@Composable
fun StatItem(
    icon: ImageVector,
    text: String,
    modifier: Modifier = Modifier,
    iconSize: Dp = 16.dp,
    textStyle: TextStyle = MaterialTheme.typography.bodySmall,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(iconSize))
        Text(text = text, style = textStyle)
    }
}

@Composable
fun DownloadStatsBar(tasks: List<DownloadTask>) {
    // Single pass calculation to avoid collection allocations and multiple iterations
    var pendingTasksCount = 0
    var runningTasksCount = 0
    var totalDownloadSpeed = 0L
    var totalUploadSpeed = 0L
    var totalSize = 0L
    var totalDownloaded = 0L

    for (task in tasks) {
        if (task.state != TorrentState.COMPLETED) {
            pendingTasksCount++
            if (task.state != TorrentState.ERROR && (task.isDownloadingOnProvider || task.isDownloadingLocally)) {
                runningTasksCount++
            }
        }
        totalDownloadSpeed += task.overallDownloadSpeed
        totalUploadSpeed += task.providerUploadSpeed
        totalSize += task.totalBytes
        totalDownloaded += task.downloadedBytes
    }

    val remainingBytes = totalSize - totalDownloaded
    val etaSeconds = if (totalDownloadSpeed > 0) remainingBytes / totalDownloadSpeed else 0L

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .height(IntrinsicSize.Min)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Speeds
            Column(verticalArrangement = Arrangement.Center) {
                StatItem(
                    arrow_upload_progress,
                    Formatter.formatSpeed(totalUploadSpeed),
                    iconSize = 14.dp,
                    textStyle = MaterialTheme.typography.labelSmall
                )
                StatItem(
                    downloading,
                    Formatter.formatSpeed(totalDownloadSpeed),
                    iconSize = 14.dp,
                    textStyle = MaterialTheme.typography.labelSmall
                )
            }

            VerticalDivider(
                modifier = Modifier.padding(vertical = 4.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )

            // Progress/Tasks
            Column(verticalArrangement = Arrangement.Center) {
                Text(
                    text = "$runningTasksCount / $pendingTasksCount tasks",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${Formatter.formatBytes(totalDownloaded)} / ${Formatter.formatBytes(totalSize)}",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (etaSeconds > 0) {
                VerticalDivider(
                    modifier = Modifier.padding(vertical = 4.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )

                // ETA
                Column(verticalArrangement = Arrangement.Center) {
                    Text(
                        text = "ETA",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                    Text(
                        text = Formatter.formatTime(etaSeconds),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun SubDownloadItem(task: DownloadTask, file: DownloadFile) {
    val context = LocalContext.current

    Card(
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
                    StatItem(icon = Icons.Default.Info, text = file.state.name.asStateText())
                    if (speed > 0) {
                        StatItem(icon = downloading, text = Formatter.formatSpeed(speed))
                    }
                    if (totalBytes > 0) {
                        StatItem(
                            icon = Icons.Default.DataUsage,
                            text = "${Formatter.formatBytes(downloadedBytes)} / ${Formatter.formatBytes(totalBytes)}"
                        )
                    }
                    if (speed > 0) {
                        val remainingBytes = totalBytes - downloadedBytes
                        val remainingTime = remainingBytes / speed
                        StatItem(icon = Icons.Default.Timer, text = Formatter.formatTime(remainingTime))
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

            if (file.state == LocalDownloadState.PAUSED || file.state == LocalDownloadState.ERROR) {
                IconButton(onClick = {
                    val intent = Intent(context, DownloadService::class.java).apply {
                        action = DownloadService.ACTION_RESUME_LOCAL_FILE_DOWNLOAD
                        putExtra(DownloadService.EXTRA_TASK_ID, task.id)
                        putExtra(DownloadService.EXTRA_FILE_LINK, file.link)
                    }
                    context.startService(intent)
                }) {
                    if (file.state == LocalDownloadState.PAUSED) Icon(Icons.Default.PlayArrow, contentDescription = "Resume")
                    if (file.state == LocalDownloadState.ERROR) Icon(Icons.Default.Replay, contentDescription = "Retry")
                }
            } else if (file.state == LocalDownloadState.DOWNLOADING || file.state == LocalDownloadState.PENDING) {
                IconButton(onClick = {
                    val intent = Intent(context, DownloadService::class.java).apply {
                        action = DownloadService.ACTION_PAUSE_LOCAL_FILE_DOWNLOAD
                        putExtra(DownloadService.EXTRA_TASK_ID, task.id)
                        putExtra(DownloadService.EXTRA_FILE_LINK, file.link)
                    }
                    context.startService(intent)
                }) {
                    Icon(Icons.Default.Pause, contentDescription = "Pause")
                }
            }
        }
    }
}

@Composable
fun ProviderTorrentFileItem(
    taskId: String,
    file: ProviderTorrentFile,
    supportsPriorities: Boolean,
    isTorrentCompletedOnProvider: Boolean,
    isManualSelectionMode: Boolean = false
) {
    val context = LocalContext.current
    var showPriorityMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, top = 8.dp, end = 8.dp, bottom = 8.dp)
            .clickable(enabled = isManualSelectionMode) {
                val intent = Intent(context, DownloadService::class.java).apply {
                    action = DownloadService.ACTION_TOGGLE_PROVIDER_FILE_SELECTION
                    putExtra(DownloadService.EXTRA_TASK_ID, taskId)
                    putExtra(DownloadService.EXTRA_FILE_ID, file.id)
                }
                context.startService(intent)
            },
        colors = CardDefaults.cardColors(
            containerColor = if (file.isSelected) {
                MaterialTheme.colorScheme.surfaceContainerHighest
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            }
        )
    ) {
        Row(
            modifier = Modifier
                .padding(8.dp)
                .fillMaxWidth()
                .alpha(if (file.isSelected || isManualSelectionMode) 1f else 0.6f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (isManualSelectionMode) {
                Icon(
                    imageVector = if (file.isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                    contentDescription = if (file.isSelected) "Selected" else "Not selected",
                    tint = if (file.isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                )
            } else if (file.isSelected) {
                ProviderTorrentFileStateIcon(file.state)
            } else {
                Icon(Icons.Default.Block, contentDescription = "Not selected")
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(text = file.name, style = MaterialTheme.typography.titleSmall)
                if (file.progress != null) {
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
                            text = "${file.progress.roundToInt()}%",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                val downloadedBytes = file.downloadedBytes
                val totalBytes = file.size

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val text = if (downloadedBytes == null) Formatter.formatBytes(totalBytes) else "${Formatter.formatBytes(downloadedBytes)} / ${Formatter.formatBytes(totalBytes)}"
                    StatItem(
                        icon = Icons.Default.DataUsage,
                        text = text
                    )
                }
            }

            if (supportsPriorities && !isManualSelectionMode) {
                Box {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.alpha(if (isTorrentCompletedOnProvider) 0.5f else 1f)
                    ) {
                        Row(
                            modifier = Modifier
                                .clickable(enabled = !isTorrentCompletedOnProvider) {
                                    showPriorityMenu = true
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = file.priority.name.lowercase().capitalized(),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium
                            )
                            Icon(
                                Icons.Default.ArrowDropDown,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    DropdownMenu(
                        expanded = showPriorityMenu,
                        onDismissRequest = { showPriorityMenu = false }
                    ) {
                        FilePriority.entries.forEach { priority ->
                            DropdownMenuItem(
                                text = { Text(priority.name.lowercase().capitalized()) },
                                onClick = {
                                    showPriorityMenu = false
                                    val intent = Intent(context, DownloadService::class.java).apply {
                                        action = DownloadService.ACTION_SET_PROVIDER_FILE_PRIORITY
                                        putExtra(DownloadService.EXTRA_TASK_ID, taskId)
                                        putExtra(DownloadService.EXTRA_FILE_ID, file.id)
                                        putExtra(DownloadService.EXTRA_PRIORITY, priority.name)
                                    }
                                    context.startService(intent)
                                }
                            )
                        }
                    }
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
    }
    Icon(icon, contentDescription = state.name)
}

@Composable
fun ProviderTorrentFileStateIcon(state: ProviderTorrentFileState) {
    val icon = when (state) {
        ProviderTorrentFileState.DOWNLOADING -> Icons.Default.Download
        ProviderTorrentFileState.COMPLETED -> Icons.Default.CheckCircle
        ProviderTorrentFileState.PENDING -> Icons.Default.HourglassEmpty
    }
    Icon(icon, contentDescription = state.name)
}

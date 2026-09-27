package com.felixbrucker.torrenthttpdownloader.feature.downloads

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import com.felixbrucker.torrenthttpdownloader.core.data.providers.FilePriority
import com.felixbrucker.torrenthttpdownloader.core.data.providers.ProviderFeature
import com.felixbrucker.torrenthttpdownloader.core.data.providers.ProviderTorrentFile
import com.felixbrucker.torrenthttpdownloader.core.data.providers.ProviderTorrentFileState
import com.felixbrucker.torrenthttpdownloader.core.data.providers.ProviderTorrentState
import com.felixbrucker.torrenthttpdownloader.core.data.providers.TorrentProvider
import com.felixbrucker.torrenthttpdownloader.core.designsystem.icons.arrow_upload_progress
import com.felixbrucker.torrenthttpdownloader.core.designsystem.icons.downloading
import com.felixbrucker.torrenthttpdownloader.core.designsystem.icons.graph_3
import com.felixbrucker.torrenthttpdownloader.core.model.DownloadFile
import com.felixbrucker.torrenthttpdownloader.core.model.DownloadTask
import com.felixbrucker.torrenthttpdownloader.core.model.FileSelectionMode
import com.felixbrucker.torrenthttpdownloader.core.model.LocalDownloadState
import com.felixbrucker.torrenthttpdownloader.core.model.TaskLocation
import com.felixbrucker.torrenthttpdownloader.core.model.TorrentState
import com.felixbrucker.torrenthttpdownloader.core.util.Formatter
import com.felixbrucker.torrenthttpdownloader.extensions.asStateText
import com.felixbrucker.torrenthttpdownloader.extensions.capitalized

@Composable
fun DownloadItem(
    task: DownloadTask,
    onRemove: () -> Unit,
    provider: TorrentProvider? = null,
    onToggleAllSelection: (taskId: String, selectAll: Boolean) -> Unit = { _, _ -> },
    onConfirmFileSelection: (taskId: String) -> Unit = {},
    onPauseTaskOnProvider: (taskId: String) -> Unit = {},
    onResumeTaskOnProvider: (taskId: String) -> Unit = {},
    onPauseTaskLocalDownloads: (taskId: String) -> Unit = {},
    onResumeTaskLocalDownloads: (taskId: String) -> Unit = {},
    onRestartTask: (taskId: String) -> Unit = {},
    onResumeLocalFileDownload: (taskId: String, fileLink: String) -> Unit = { _, _ -> },
    onPauseLocalFileDownload: (taskId: String, fileLink: String) -> Unit = { _, _ -> },
    onToggleProviderFileSelection: (taskId: String, fileId: Int) -> Unit = { _, _ -> },
    onSetProviderFilePriority: (taskId: String, fileId: Int, priority: FilePriority) -> Unit = { _, _, _ -> }
) {
    var isExpanded by remember { mutableStateOf(false) }
    val isLocal = task.location == TaskLocation.LOCAL
    val hasUnfinishedLocalDownloads = isLocal && task.files.any { it.state != LocalDownloadState.COMPLETED }
    val isExpandable = hasUnfinishedLocalDownloads || (!isLocal && task.providerTorrentInfo?.files?.isNotEmpty() == true)
    val isManualSelectionMode = task.state == TorrentState.SELECTING_FILES && task.fileSelectionMode == FileSelectionMode.MANUAL

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
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!isNarrow) {
                        TaskSelectionOrStateIcon(
                            task = task,
                            isExpanded = isExpanded,
                            onToggleAllSelection = onToggleAllSelection
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        TaskHeaderRow(
                            task = task,
                            isNarrow = isNarrow,
                            isExpanded = isExpanded,
                            onToggleAllSelection = onToggleAllSelection
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        TaskProgressBar(progress = task.overallProgress)
                        Spacer(modifier = Modifier.height(8.dp))
                        TaskStatsFlow(task = task)
                        if (task.errorMessage != null) {
                            Text(
                                text = "Error: ${task.errorMessage}",
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                        if (isNarrow) {
                            NarrowTaskActionRow(
                                task = task,
                                provider = provider,
                                onRemove = onRemove,
                                isExpandable = isExpandable,
                                isExpanded = isExpanded,
                                onConfirmFileSelection = onConfirmFileSelection,
                                onPauseTaskOnProvider = onPauseTaskOnProvider,
                                onResumeTaskOnProvider = onResumeTaskOnProvider,
                                onPauseTaskLocalDownloads = onPauseTaskLocalDownloads,
                                onResumeTaskLocalDownloads = onResumeTaskLocalDownloads,
                                onRestartTask = onRestartTask
                            )
                        }
                    }
                    if (!isNarrow) {
                        Spacer(modifier = Modifier.width(16.dp))
                        TaskActions(
                            task = task,
                            provider = provider,
                            onRemove = onRemove,
                            onConfirmFileSelection = onConfirmFileSelection,
                            onPauseTaskOnProvider = onPauseTaskOnProvider,
                            onResumeTaskOnProvider = onResumeTaskOnProvider,
                            onPauseTaskLocalDownloads = onPauseTaskLocalDownloads,
                            onResumeTaskLocalDownloads = onResumeTaskLocalDownloads,
                            onRestartTask = onRestartTask
                        )
                        if (isExpandable) {
                            Icon(
                                imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = if (isExpanded) "Collapse" else "Expand"
                            )
                        }
                    }
                }

                if (isExpandable && isExpanded) {
                    TaskExpandedFilesList(
                        task = task,
                        provider = provider,
                        isManualSelectionMode = isManualSelectionMode,
                        onResumeLocalFileDownload = onResumeLocalFileDownload,
                        onPauseLocalFileDownload = onPauseLocalFileDownload,
                        onToggleProviderFileSelection = onToggleProviderFileSelection,
                        onSetProviderFilePriority = onSetProviderFilePriority
                    )
                }
            }
        }
    }
}

@Composable
fun TaskSelectionOrStateIcon(
    task: DownloadTask,
    isExpanded: Boolean,
    onToggleAllSelection: (String, Boolean) -> Unit
) {
    val isManualSelectionMode = task.state == TorrentState.SELECTING_FILES && task.fileSelectionMode == FileSelectionMode.MANUAL

    if (isManualSelectionMode && isExpanded) {
        val allSelected = task.providerTorrentInfo?.files?.all { it.isSelected } == true
        Icon(
            imageVector = if (allSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
            contentDescription = if (allSelected) "Deselect All" else "Select All",
            tint = if (allSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
            modifier = Modifier.clickable {
                onToggleAllSelection(task.id, !allSelected)
            }
        )
    } else {
        StateIcon(state = task.state)
    }
}

@Composable
fun TaskHeaderRow(
    task: DownloadTask,
    isNarrow: Boolean,
    isExpanded: Boolean,
    onToggleAllSelection: (String, Boolean) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (isNarrow) {
            TaskSelectionOrStateIcon(
                task = task,
                isExpanded = isExpanded,
                onToggleAllSelection = onToggleAllSelection
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = task.name,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
fun TaskProgressBar(progress: Int) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress / 100f,
        animationSpec = tween(durationMillis = 1000),
        label = "overall progress"
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        LinearProgressIndicator(
            progress = { animatedProgress },
            modifier = Modifier.weight(1f),
            drawStopIndicator = {}
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "$progress%",
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
fun TaskStatsFlow(task: DownloadTask) {
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
            val remainingTime = if (task.overallDownloadSpeed > 0) remainingBytes / task.overallDownloadSpeed else 0
            StatItem(icon = Icons.Default.Timer, text = Formatter.formatTime(remainingTime))
        }
        if (task.providerTorrentInfo?.peers != null && task.providerTorrentInfo.totalPeers != null) {
            StatItem(icon = graph_3, text = "${task.providerTorrentInfo.peers}/${task.providerTorrentInfo.totalPeers}")
        }
    }
}

@Composable
fun NarrowTaskActionRow(
    task: DownloadTask,
    provider: TorrentProvider?,
    onRemove: () -> Unit,
    isExpandable: Boolean,
    isExpanded: Boolean,
    onConfirmFileSelection: (taskId: String) -> Unit,
    onPauseTaskOnProvider: (taskId: String) -> Unit,
    onResumeTaskOnProvider: (taskId: String) -> Unit,
    onPauseTaskLocalDownloads: (taskId: String) -> Unit,
    onResumeTaskLocalDownloads: (taskId: String) -> Unit,
    onRestartTask: (taskId: String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        TaskActions(
            task = task,
            provider = provider,
            onRemove = onRemove,
            onConfirmFileSelection = onConfirmFileSelection,
            onPauseTaskOnProvider = onPauseTaskOnProvider,
            onResumeTaskOnProvider = onResumeTaskOnProvider,
            onPauseTaskLocalDownloads = onPauseTaskLocalDownloads,
            onResumeTaskLocalDownloads = onResumeTaskLocalDownloads,
            onRestartTask = onRestartTask
        )
        if (isExpandable) {
            Icon(
                imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (isExpanded) "Collapse" else "Expand"
            )
        }
    }
}

@Composable
fun TaskActions(
    task: DownloadTask,
    provider: TorrentProvider?,
    onRemove: () -> Unit,
    onConfirmFileSelection: (taskId: String) -> Unit,
    onPauseTaskOnProvider: (taskId: String) -> Unit,
    onResumeTaskOnProvider: (taskId: String) -> Unit,
    onPauseTaskLocalDownloads: (taskId: String) -> Unit,
    onResumeTaskLocalDownloads: (taskId: String) -> Unit,
    onRestartTask: (taskId: String) -> Unit
) {
    val isDownloading = task.files.any { it.state == LocalDownloadState.DOWNLOADING || it.state == LocalDownloadState.PENDING }
    val isPaused = task.files.any { it.state == LocalDownloadState.PAUSED }

    if (task.state == TorrentState.SELECTING_FILES && task.fileSelectionMode == FileSelectionMode.MANUAL) {
        IconButton(onClick = { onConfirmFileSelection(task.id) }) {
            Icon(Icons.Default.CheckCircle, contentDescription = "Confirm selection", tint = MaterialTheme.colorScheme.primary)
        }
    }

    if (task.location == TaskLocation.PROVIDER && provider?.supports(ProviderFeature.PauseResume) == true) {
        if (task.providerTorrentInfo?.state == ProviderTorrentState.DOWNLOADING) {
            IconButton(onClick = { onPauseTaskOnProvider(task.id) }) {
                Icon(Icons.Default.Pause, contentDescription = "Pause")
            }
        } else if (task.providerTorrentInfo?.state == ProviderTorrentState.PAUSED) {
            IconButton(onClick = { onResumeTaskOnProvider(task.id) }) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Resume")
            }
        }
    }

    if (task.state == TorrentState.DOWNLOADING_LOCALLY) {
        if (isDownloading) {
            IconButton(onClick = { onPauseTaskLocalDownloads(task.id) }) {
                Icon(Icons.Default.Pause, contentDescription = "Pause")
            }
        } else if (isPaused) {
            IconButton(onClick = { onResumeTaskLocalDownloads(task.id) }) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Resume")
            }
        }
    }

    if (task.state == TorrentState.ERROR) {
        IconButton(onClick = { onRestartTask(task.id) }) {
            Icon(Icons.Default.Replay, contentDescription = "Restart")
        }
    }

    IconButton(onClick = onRemove) {
        Icon(Icons.Default.Delete, contentDescription = "Remove")
    }
}

@Composable
fun TaskExpandedFilesList(
    task: DownloadTask,
    provider: TorrentProvider?,
    isManualSelectionMode: Boolean,
    onResumeLocalFileDownload: (taskId: String, fileLink: String) -> Unit,
    onPauseLocalFileDownload: (taskId: String, fileLink: String) -> Unit,
    onToggleProviderFileSelection: (taskId: String, fileId: Int) -> Unit,
    onSetProviderFilePriority: (taskId: String, fileId: Int, priority: FilePriority) -> Unit
) {
    Column(modifier = Modifier.padding(top = 8.dp)) {
        if (task.location == TaskLocation.LOCAL) {
            task.files.forEach { file ->
                if (file.state != LocalDownloadState.COMPLETED) {
                    SubDownloadItem(
                        task = task,
                        file = file,
                        onResumeFile = onResumeLocalFileDownload,
                        onPauseFile = onPauseLocalFileDownload
                    )
                }
            }
        } else {
            task.providerTorrentInfo?.files?.forEach { file ->
                ProviderTorrentFileItem(
                    taskId = task.id,
                    file = file,
                    supportsPriorities = provider?.supports(ProviderFeature.FilePriorities) == true,
                    isTorrentCompletedOnProvider = task.providerTorrentInfo.state == ProviderTorrentState.COMPLETED,
                    isManualSelectionMode = isManualSelectionMode,
                    onToggleSelection = onToggleProviderFileSelection,
                    onSetPriority = onSetProviderFilePriority
                )
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
            SpeedStatsColumn(uploadSpeed = totalUploadSpeed, downloadSpeed = totalDownloadSpeed)

            VerticalDivider(
                modifier = Modifier.padding(vertical = 4.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )

            TaskProgressStatsColumn(
                runningCount = runningTasksCount,
                pendingCount = pendingTasksCount,
                downloadedBytes = totalDownloaded,
                totalBytes = totalSize
            )

            if (etaSeconds > 0) {
                VerticalDivider(
                    modifier = Modifier.padding(vertical = 4.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )

                EtaStatsColumn(etaSeconds = etaSeconds)
            }
        }
    }
}

@Composable
private fun SpeedStatsColumn(uploadSpeed: Long, downloadSpeed: Long) {
    Column(verticalArrangement = Arrangement.Center) {
        StatItem(
            arrow_upload_progress,
            Formatter.formatSpeed(uploadSpeed),
            iconSize = 14.dp,
            textStyle = MaterialTheme.typography.labelSmall
        )
        StatItem(
            downloading,
            Formatter.formatSpeed(downloadSpeed),
            iconSize = 14.dp,
            textStyle = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
private fun TaskProgressStatsColumn(
    runningCount: Int,
    pendingCount: Int,
    downloadedBytes: Long,
    totalBytes: Long
) {
    Column(verticalArrangement = Arrangement.Center) {
        Text(
            text = "$runningCount / $pendingCount tasks",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "${Formatter.formatBytes(downloadedBytes)} / ${Formatter.formatBytes(totalBytes)}",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun EtaStatsColumn(etaSeconds: Long) {
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

@Composable
fun SubDownloadItem(
    task: DownloadTask,
    file: DownloadFile,
    onResumeFile: (taskId: String, fileLink: String) -> Unit,
    onPauseFile: (taskId: String, fileLink: String) -> Unit
) {
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
            SubDownloadDetails(file = file, modifier = Modifier.weight(1f))
            SubDownloadActions(
                taskId = task.id,
                file = file,
                onResumeFile = onResumeFile,
                onPauseFile = onPauseFile
            )
        }
    }
}

@Composable
private fun SubDownloadDetails(file: DownloadFile, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
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

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            StatItem(icon = Icons.Default.Info, text = file.state.name.asStateText())
            if (file.speed > 0) {
                StatItem(icon = downloading, text = Formatter.formatSpeed(file.speed))
            }
            if (file.totalBytes > 0) {
                StatItem(
                    icon = Icons.Default.DataUsage,
                    text = "${Formatter.formatBytes(file.downloadedBytes)} / ${Formatter.formatBytes(file.totalBytes)}"
                )
            }
            if (file.speed > 0) {
                val remainingBytes = file.totalBytes - file.downloadedBytes
                val remainingTime = remainingBytes / file.speed
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
}

@Composable
private fun SubDownloadActions(
    taskId: String,
    file: DownloadFile,
    onResumeFile: (taskId: String, fileLink: String) -> Unit,
    onPauseFile: (taskId: String, fileLink: String) -> Unit
) {
    if (file.state == LocalDownloadState.PAUSED || file.state == LocalDownloadState.ERROR) {
        IconButton(onClick = { onResumeFile(taskId, file.link) }) {
            if (file.state == LocalDownloadState.PAUSED) Icon(Icons.Default.PlayArrow, contentDescription = "Resume")
            if (file.state == LocalDownloadState.ERROR) Icon(Icons.Default.Replay, contentDescription = "Retry")
        }
    } else if (file.state == LocalDownloadState.DOWNLOADING || file.state == LocalDownloadState.PENDING) {
        IconButton(onClick = { onPauseFile(taskId, file.link) }) {
            Icon(Icons.Default.Pause, contentDescription = "Pause")
        }
    }
}

@Composable
fun ProviderTorrentFileItem(
    taskId: String,
    file: ProviderTorrentFile,
    supportsPriorities: Boolean,
    isTorrentCompletedOnProvider: Boolean,
    isManualSelectionMode: Boolean = false,
    onToggleSelection: (taskId: String, fileId: Int) -> Unit,
    onSetPriority: (taskId: String, fileId: Int, priority: FilePriority) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, top = 8.dp, end = 8.dp, bottom = 8.dp)
            .clickable(enabled = isManualSelectionMode) {
                onToggleSelection(taskId, file.id)
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

            ProviderFileDetails(file = file, modifier = Modifier.weight(1f))

            if (supportsPriorities && !isManualSelectionMode) {
                ProviderFilePrioritySelector(
                    taskId = taskId,
                    file = file,
                    isTorrentCompletedOnProvider = isTorrentCompletedOnProvider,
                    onSetPriority = onSetPriority
                )
            }
        }
    }
}

@Composable
private fun ProviderFileDetails(file: ProviderTorrentFile, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
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
}

@Composable
private fun ProviderFilePrioritySelector(
    taskId: String,
    file: ProviderTorrentFile,
    isTorrentCompletedOnProvider: Boolean,
    onSetPriority: (taskId: String, fileId: Int, priority: FilePriority) -> Unit
) {
    var showPriorityMenu by remember { mutableStateOf(false) }

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
                        onSetPriority(taskId, file.id, priority)
                    }
                )
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

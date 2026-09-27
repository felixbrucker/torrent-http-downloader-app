package com.felixbrucker.torrenthttpdownloader.feature.downloads

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import com.felixbrucker.torrenthttpdownloader.NavRoute
import com.felixbrucker.torrenthttpdownloader.Navigator
import com.felixbrucker.torrenthttpdownloader.R
import com.felixbrucker.torrenthttpdownloader.core.data.providers.FilePriority
import com.felixbrucker.torrenthttpdownloader.core.data.providers.ProviderTorrentState
import com.felixbrucker.torrenthttpdownloader.core.data.providers.TorrentProvider
import com.felixbrucker.torrenthttpdownloader.core.model.DownloadTask
import com.felixbrucker.torrenthttpdownloader.core.model.LocalDownloadState
import com.felixbrucker.torrenthttpdownloader.core.model.TorrentType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    navigator: Navigator,
    viewModel: DownloadsViewModel = viewModel()
) {
    val windowInfo = LocalWindowInfo.current
    val isNarrowScreen = windowInfo.containerSize.width.dp < 1400.dp
    val tasks by viewModel.tasks.collectAsState()
    val unreadRssCount by viewModel.totalUnreadRssCount.collectAsState(initial = 0)
    val provider = remember { viewModel.getProvider() }

    val lazyListState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    var draggedItemIndex by remember { mutableStateOf<Int?>(null) }
    var draggingOffset by remember { mutableFloatStateOf(0f) }

    var taskToRemove by remember { mutableStateOf<DownloadTask?>(null) }
    var deleteFiles by remember { mutableStateOf(true) }
    var deleteTorrentFile by remember { mutableStateOf(true) }

    fun onDrag(dragAmount: Offset) {
        draggingOffset += dragAmount.y
        val currentDraggedIndex = draggedItemIndex ?: return

        val layoutInfo = lazyListState.layoutInfo
        val visibleItems = layoutInfo.visibleItemsInfo
        val draggedItem = visibleItems.firstOrNull { it.index == currentDraggedIndex } ?: return

        val draggedItemCenter = draggedItem.offset + draggedItem.size / 2 + draggingOffset

        val targetItem = visibleItems.firstOrNull { item ->
            item.index != currentDraggedIndex &&
                    draggedItemCenter.toInt() in item.offset..(item.offset + item.size)
        }

        if (targetItem != null) {
            viewModel.moveTask(currentDraggedIndex, targetItem.index)
            draggedItemIndex = targetItem.index
            draggingOffset += (draggedItem.offset - targetItem.offset).toFloat()
        }

        // Auto-scroll logic
        val topBound = layoutInfo.viewportStartOffset + 50
        val bottomBound = layoutInfo.viewportEndOffset - 50
        if (draggedItem.offset + draggingOffset < topBound) {
            coroutineScope.launch { lazyListState.scrollBy(-10f) }
        } else if (draggedItem.offset + draggingOffset + draggedItem.size > bottomBound) {
            coroutineScope.launch { lazyListState.scrollBy(10f) }
        }
    }

    val taskStateFlags = remember(tasks) { computeTaskStateFlags(tasks) }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        DownloadsTopBarTitle(isNarrowScreen = isNarrowScreen, tasks = tasks)
                    },
                    actions = {
                        DownloadsTopBarActions(
                            taskStateFlags = taskStateFlags,
                            unreadRssCount = unreadRssCount,
                            onResumeAll = { viewModel.resumeAll() },
                            onPauseAll = { viewModel.pauseAll() },
                            onNavigateRss = { navigator.navigate(NavRoute.RssFeeds) },
                            onNavigateSettings = { navigator.navigate(NavRoute.Settings) }
                        )
                    }
                )
                if (isNarrowScreen) {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        DownloadStatsBar(tasks = tasks)
                    }
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { offset ->
                                lazyListState.layoutInfo.visibleItemsInfo
                                    .firstOrNull { item ->
                                        offset.y.toInt() in item.offset..(item.offset + item.size)
                                    }?.also {
                                        draggedItemIndex = it.index
                                    }
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                onDrag(dragAmount)
                            },
                            onDragEnd = {
                                draggedItemIndex = null
                                draggingOffset = 0f
                            },
                            onDragCancel = {
                                draggedItemIndex = null
                                draggingOffset = 0f
                            }
                        )
                    },
                state = lazyListState
            ) {
                itemsIndexed(tasks, key = { _, task -> task.id }) { index, task ->
                    val isDragging = index == draggedItemIndex
                    DownloadTaskListItem(
                        task = task,
                        isDragging = isDragging,
                        draggingOffset = draggingOffset,
                        onRemove = {
                            taskToRemove = task
                            deleteFiles = true
                            deleteTorrentFile = true
                        },
                        provider = provider,
                        onToggleAllSelection = { taskId, selectAll -> viewModel.toggleAllProviderFileSelection(taskId, selectAll) },
                        onConfirmFileSelection = { taskId -> viewModel.confirmFileSelection(taskId) },
                        onPauseTaskOnProvider = { taskId -> viewModel.pauseTaskOnProvider(taskId) },
                        onResumeTaskOnProvider = { taskId -> viewModel.resumeTaskOnProvider(taskId) },
                        onPauseTaskLocalDownloads = { taskId -> viewModel.pauseTaskLocalDownloads(taskId) },
                        onResumeTaskLocalDownloads = { taskId -> viewModel.resumeTaskLocalDownloads(taskId) },
                        onRestartTask = { taskId -> viewModel.restartTask(taskId) },
                        onResumeLocalFileDownload = { taskId, fileLink -> viewModel.resumeLocalFileDownload(taskId, fileLink) },
                        onPauseLocalFileDownload = { taskId, fileLink -> viewModel.pauseLocalFileDownload(taskId, fileLink) },
                        onToggleProviderFileSelection = { taskId, fileId -> viewModel.toggleProviderFileSelection(taskId, fileId) },
                        onSetProviderFilePriority = { taskId, fileId, priority -> viewModel.setProviderFilePriority(taskId, fileId, priority) }
                    )
                }
            }
        }

        taskToRemove?.let { task ->
            RemoveTaskDialog(
                task = task,
                deleteFiles = deleteFiles,
                deleteTorrentFile = deleteTorrentFile,
                onDeleteFilesChange = { deleteFiles = it },
                onDeleteTorrentFileChange = { deleteTorrentFile = it },
                onDismiss = { taskToRemove = null },
                onConfirm = {
                    viewModel.removeTask(
                        taskId = task.id,
                        deleteFiles = deleteFiles,
                        deleteTorrentFile = deleteTorrentFile
                    )
                    taskToRemove = null
                }
            )
        }
    }
}

private fun computeTaskStateFlags(tasks: List<DownloadTask>): TaskStateFlags {
    var downloading = false
    var paused = false
    var downloadingOnProvider = false
    var pausedOnProvider = false

    for (task in tasks) {
        if (!downloading || !paused) {
            for (file in task.files) {
                if (file.state == LocalDownloadState.DOWNLOADING || file.state == LocalDownloadState.PENDING) {
                    downloading = true
                } else if (file.state == LocalDownloadState.PAUSED) {
                    paused = true
                }
            }
        }
        if (task.providerTorrentInfo?.state == ProviderTorrentState.DOWNLOADING) {
            downloadingOnProvider = true
        } else if (task.providerTorrentInfo?.state == ProviderTorrentState.PAUSED) {
            pausedOnProvider = true
        }
    }
    return TaskStateFlags(downloading, paused, downloadingOnProvider, pausedOnProvider)
}

@Composable
private fun DownloadsTopBarTitle(isNarrowScreen: Boolean, tasks: List<DownloadTask>) {
    if (isNarrowScreen) {
        Text(
            text = stringResource(id = R.string.app_name),
            maxLines = 1
        )
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(id = R.string.app_name),
                maxLines = 1
            )
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center
            ) {
                DownloadStatsBar(tasks = tasks)
            }
        }
    }
}

@Composable
private fun DownloadsTopBarActions(
    taskStateFlags: TaskStateFlags,
    unreadRssCount: Int,
    onResumeAll: () -> Unit,
    onPauseAll: () -> Unit,
    onNavigateRss: () -> Unit,
    onNavigateSettings: () -> Unit
) {
    if (taskStateFlags.anyPaused || taskStateFlags.anyPausedOnProvider) {
        IconButton(onClick = onResumeAll) {
            Icon(Icons.Default.PlayArrow, contentDescription = "Resume All")
        }
    }
    if (taskStateFlags.anyDownloading || taskStateFlags.anyDownloadingOnProvider) {
        IconButton(onClick = onPauseAll) {
            Icon(Icons.Default.Pause, contentDescription = "Pause All")
        }
    }

    RssNavigationAction(
        unreadRssCount = unreadRssCount,
        onNavigateRss = onNavigateRss
    )

    IconButton(onClick = onNavigateSettings) {
        Icon(
            Icons.Default.Settings,
            contentDescription = stringResource(id = R.string.action_settings)
        )
    }
}

@Composable
private fun RssNavigationAction(
    unreadRssCount: Int,
    onNavigateRss: () -> Unit
) {
    Box {
        IconButton(onClick = onNavigateRss) {
            Icon(Icons.Default.RssFeed, contentDescription = "RSS Feeds")
        }
        if (unreadRssCount > 0) {
            Badge(
                modifier = Modifier.align(Alignment.TopEnd),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Text(unreadRssCount.toString())
            }
        }
    }
}

@Composable
private fun LazyItemScope.DownloadTaskListItem(
    task: DownloadTask,
    isDragging: Boolean,
    draggingOffset: Float,
    onRemove: () -> Unit,
    provider: TorrentProvider?,
    onToggleAllSelection: (taskId: String, selectAll: Boolean) -> Unit,
    onConfirmFileSelection: (taskId: String) -> Unit,
    onPauseTaskOnProvider: (taskId: String) -> Unit,
    onResumeTaskOnProvider: (taskId: String) -> Unit,
    onPauseTaskLocalDownloads: (taskId: String) -> Unit,
    onResumeTaskLocalDownloads: (taskId: String) -> Unit,
    onRestartTask: (taskId: String) -> Unit,
    onResumeLocalFileDownload: (taskId: String, fileLink: String) -> Unit,
    onPauseLocalFileDownload: (taskId: String, fileLink: String) -> Unit,
    onToggleProviderFileSelection: (taskId: String, fileId: Int) -> Unit,
    onSetProviderFilePriority: (taskId: String, fileId: Int, priority: FilePriority) -> Unit
) {
    Box(
        modifier = Modifier
            .animateItem()
            .zIndex(if (isDragging) 1f else 0f)
            .graphicsLayer {
                translationY = if (isDragging) draggingOffset else 0f
                scaleX = if (isDragging) 1.05f else 1f
                scaleY = if (isDragging) 1.05f else 1f
                shadowElevation = if (isDragging) 8f else 0f
            }
    ) {
        DownloadItem(
            task = task,
            onRemove = onRemove,
            provider = provider,
            onToggleAllSelection = onToggleAllSelection,
            onConfirmFileSelection = onConfirmFileSelection,
            onPauseTaskOnProvider = onPauseTaskOnProvider,
            onResumeTaskOnProvider = onResumeTaskOnProvider,
            onPauseTaskLocalDownloads = onPauseTaskLocalDownloads,
            onResumeTaskLocalDownloads = onResumeTaskLocalDownloads,
            onRestartTask = onRestartTask,
            onResumeLocalFileDownload = onResumeLocalFileDownload,
            onPauseLocalFileDownload = onPauseLocalFileDownload,
            onToggleProviderFileSelection = onToggleProviderFileSelection,
            onSetProviderFilePriority = onSetProviderFilePriority
        )
    }
}

@Composable
private fun RemoveTaskDialog(
    task: DownloadTask,
    deleteFiles: Boolean,
    deleteTorrentFile: Boolean,
    onDeleteFilesChange: (Boolean) -> Unit,
    onDeleteTorrentFileChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Remove task") },
        text = {
            RemoveTaskDialogContent(
                taskName = task.name,
                isTorrentFile = task.torrent.type == TorrentType.TORRENT_FILE,
                deleteFiles = deleteFiles,
                deleteTorrentFile = deleteTorrentFile,
                onDeleteFilesChange = onDeleteFilesChange,
                onDeleteTorrentFileChange = onDeleteTorrentFileChange
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Remove")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun RemoveTaskDialogContent(
    taskName: String,
    isTorrentFile: Boolean,
    deleteFiles: Boolean,
    deleteTorrentFile: Boolean,
    onDeleteFilesChange: (Boolean) -> Unit,
    onDeleteTorrentFileChange: (Boolean) -> Unit
) {
    Column {
        Text("Are you sure you want to remove $taskName?")
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onDeleteFilesChange(!deleteFiles) }
                .padding(vertical = 4.dp)
        ) {
            Checkbox(checked = deleteFiles, onCheckedChange = onDeleteFilesChange)
            Text("Delete temporary files")
        }
        if (isTorrentFile) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onDeleteTorrentFileChange(!deleteTorrentFile) }
                    .padding(vertical = 4.dp)
            ) {
                Checkbox(
                    checked = deleteTorrentFile,
                    onCheckedChange = onDeleteTorrentFileChange
                )
                Text("Delete torrent file")
            }
        }
    }
}

private data class TaskStateFlags(
    val anyDownloading: Boolean,
    val anyPaused: Boolean,
    val anyDownloadingOnProvider: Boolean,
    val anyPausedOnProvider: Boolean
)

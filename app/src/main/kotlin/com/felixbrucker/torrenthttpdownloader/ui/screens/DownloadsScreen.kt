package com.felixbrucker.torrenthttpdownloader.ui.screens

import android.content.Intent
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
import androidx.compose.foundation.lazy.LazyListState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.felixbrucker.torrenthttpdownloader.download.DownloadService
import com.felixbrucker.torrenthttpdownloader.download.DownloadTracker
import com.felixbrucker.torrenthttpdownloader.ui.navigation.NavRoute
import com.felixbrucker.torrenthttpdownloader.ui.navigation.Navigator
import com.felixbrucker.torrenthttpdownloader.R
import com.felixbrucker.torrenthttpdownloader.models.DownloadTask
import com.felixbrucker.torrenthttpdownloader.models.LocalDownloadState
import com.felixbrucker.torrenthttpdownloader.models.TorrentType
import com.felixbrucker.torrenthttpdownloader.providers.ProviderTorrentState
import com.felixbrucker.torrenthttpdownloader.ui.composable.DownloadItem
import com.felixbrucker.torrenthttpdownloader.ui.composable.DownloadStatsBar
import com.felixbrucker.torrenthttpdownloader.ui.viewmodels.DownloadsViewModel

@Composable
fun DownloadsScreen(
    navigator: Navigator,
    viewModel: DownloadsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val windowInfo = LocalWindowInfo.current
    val isNarrowScreen = windowInfo.containerSize.width.dp < 1400.dp
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()
    val unreadRssCount by DownloadTracker.totalUnreadRssCount.collectAsState(initial = 0)

    val lazyListState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    var draggedItemIndex by remember { mutableStateOf<Int?>(null) }
    var draggingOffset by remember { mutableFloatStateOf(0f) }

    var taskToRemove by remember { mutableStateOf<DownloadTask?>(null) }
    var deleteFiles by remember { mutableStateOf(true) }
    var deleteTorrentFile by remember { mutableStateOf(true) }

    Scaffold(
        topBar = {
            DownloadsTopBar(
                isNarrowScreen = isNarrowScreen,
                tasks = tasks,
                unreadRssCount = unreadRssCount,
                onNavigateToRss = { navigator.navigate(NavRoute.RssFeeds) },
                onNavigateToSettings = { navigator.navigate(NavRoute.Settings) },
                onPauseAll = {
                    context.startService(Intent(context, DownloadService::class.java).apply { action = DownloadService.ACTION_PAUSE_ALL_LOCAL_DOWNLOADS })
                    context.startService(Intent(context, DownloadService::class.java).apply { action = DownloadService.ACTION_PAUSE_ALL_ON_PROVIDER })
                },
                onResumeAll = {
                    context.startService(Intent(context, DownloadService::class.java).apply { action = DownloadService.ACTION_RESUME_ALL_LOCAL_DOWNLOADS })
                    context.startService(Intent(context, DownloadService::class.java).apply { action = DownloadService.ACTION_RESUME_ALL_ON_PROVIDER })
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            DownloadTaskList(
                tasks = tasks,
                lazyListState = lazyListState,
                draggedItemIndex = draggedItemIndex,
                draggingOffset = draggingOffset,
                onDragStart = { draggedItemIndex = it },
                onDragAmount = { dragAmount ->
                    draggingOffset += dragAmount.y
                    val currentDraggedIndex = draggedItemIndex ?: return@DownloadTaskList
                    val layoutInfo = lazyListState.layoutInfo
                    val visibleItems = layoutInfo.visibleItemsInfo
                    val draggedItem = visibleItems.firstOrNull { it.index == currentDraggedIndex } ?: return@DownloadTaskList
                    val draggedItemCenter = draggedItem.offset + draggedItem.size / 2 + draggingOffset
                    val targetItem = visibleItems.firstOrNull { item ->
                        item.index != currentDraggedIndex && draggedItemCenter.toInt() in item.offset..(item.offset + item.size)
                    }
                    if (targetItem != null) {
                        DownloadTracker.moveTask(currentDraggedIndex, targetItem.index)
                        draggedItemIndex = targetItem.index
                        draggingOffset += (draggedItem.offset - targetItem.offset).toFloat()
                    }
                },
                onDragEnd = {
                    draggedItemIndex = null
                    draggingOffset = 0f
                },
                onRemoveTask = { task ->
                    taskToRemove = task
                    deleteFiles = true
                    deleteTorrentFile = true
                }
            )
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
                    val intent = Intent(context, DownloadService::class.java).apply {
                        action = DownloadService.ACTION_REMOVE_TASK
                        putExtra(DownloadService.EXTRA_TASK_ID, task.id)
                        putExtra(DownloadService.EXTRA_DELETE_FILES, deleteFiles)
                        putExtra(DownloadService.EXTRA_DELETE_TORRENT_FILE, deleteTorrentFile)
                    }
                    context.startService(intent)
                    taskToRemove = null
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DownloadsTopBar(
    isNarrowScreen: Boolean,
    tasks: List<DownloadTask>,
    unreadRssCount: Int,
    onNavigateToRss: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onPauseAll: () -> Unit,
    onResumeAll: () -> Unit
) {
    val anyDownloading = tasks.any { t -> t.files.any { it.state == LocalDownloadState.DOWNLOADING || it.state == LocalDownloadState.PENDING } || t.providerTorrentInfo?.state == ProviderTorrentState.DOWNLOADING }
    val anyPaused = tasks.any { t -> t.files.any { it.state == LocalDownloadState.PAUSED } || t.providerTorrentInfo?.state == ProviderTorrentState.PAUSED }

    Column {
        TopAppBar(
            title = {
                if (isNarrowScreen) {
                    Text(stringResource(id = R.string.app_name), maxLines = 1)
                } else {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(id = R.string.app_name), maxLines = 1)
                        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            DownloadStatsBar(tasks = tasks)
                        }
                    }
                }
            },
            actions = {
                if (anyPaused) {
                    IconButton(onClick = onResumeAll) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Resume All")
                    }
                }
                if (anyDownloading) {
                    IconButton(onClick = onPauseAll) {
                        Icon(Icons.Default.Pause, contentDescription = "Pause All")
                    }
                }
                Box {
                    IconButton(onClick = onNavigateToRss) {
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
                IconButton(onClick = onNavigateToSettings) {
                    Icon(Icons.Default.Settings, contentDescription = stringResource(id = R.string.action_settings))
                }
            }
        )
        if (isNarrowScreen) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                DownloadStatsBar(tasks = tasks)
            }
        }
    }
}

@Composable
private fun DownloadTaskList(
    tasks: List<DownloadTask>,
    lazyListState: LazyListState,
    draggedItemIndex: Int?,
    draggingOffset: Float,
    onDragStart: (Int) -> Unit,
    onDragAmount: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onRemoveTask: (DownloadTask) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { offset ->
                        lazyListState.layoutInfo.visibleItemsInfo
                            .firstOrNull { item -> offset.y.toInt() in item.offset..(item.offset + item.size) }
                            ?.also { onDragStart(it.index) }
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        onDragAmount(dragAmount)
                    },
                    onDragEnd = onDragEnd,
                    onDragCancel = onDragEnd
                )
            },
        state = lazyListState
    ) {
        itemsIndexed(tasks, key = { _, task -> task.id }) { index, task ->
            val isDragging = index == draggedItemIndex
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
                DownloadItem(task = task, onRemove = { onRemoveTask(task) })
            }
        }
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
            Column {
                Text("Are you sure you want to remove ${task.name}?")
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
                if (task.torrent.type == TorrentType.TORRENT_FILE) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onDeleteTorrentFileChange(!deleteTorrentFile) }
                            .padding(vertical = 4.dp)
                    ) {
                        Checkbox(checked = deleteTorrentFile, onCheckedChange = onDeleteTorrentFileChange)
                        Text("Delete torrent file")
                    }
                }
            }
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

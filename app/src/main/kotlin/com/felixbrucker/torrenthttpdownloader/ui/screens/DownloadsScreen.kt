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
import com.felixbrucker.torrenthttpdownloader.ui.composable.DownloadItem
import kotlinx.coroutines.launch
import com.felixbrucker.torrenthttpdownloader.ui.composable.DownloadStatsBar
import com.felixbrucker.torrenthttpdownloader.DownloadService
import com.felixbrucker.torrenthttpdownloader.DownloadTracker
import com.felixbrucker.torrenthttpdownloader.NavRoute
import com.felixbrucker.torrenthttpdownloader.Navigator
import com.felixbrucker.torrenthttpdownloader.R
import com.felixbrucker.torrenthttpdownloader.models.DownloadTask
import com.felixbrucker.torrenthttpdownloader.models.LocalDownloadState
import com.felixbrucker.torrenthttpdownloader.models.TorrentType
import com.felixbrucker.torrenthttpdownloader.providers.ProviderFactory
import com.felixbrucker.torrenthttpdownloader.providers.ProviderTorrentState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    navigator: Navigator,
    downloadTracker: DownloadTracker,
    providerFactory: ProviderFactory,
) {
    val context = LocalContext.current
    val windowInfo = LocalWindowInfo.current
    val isNarrowScreen = windowInfo.containerSize.width.dp < 1400.dp
    val tasks by downloadTracker.tasks.collectAsState()
    val unreadRssCount by downloadTracker.totalUnreadRssCount.collectAsState(initial = 0)
    val provider = remember { providerFactory.getProvider() }

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
            downloadTracker.moveTask(currentDraggedIndex, targetItem.index)
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

    val (anyDownloading, anyPaused, anyDownloadingOnProvider, anyPausedOnProvider) = remember(tasks) {
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
        TaskStateFlags(downloading, paused, downloadingOnProvider, pausedOnProvider)
    }

    fun removeTask(
        taskId: String,
        deleteFiles: Boolean,
        deleteTorrentFile: Boolean
    ) {
        val intent = Intent(context, DownloadService::class.java).apply {
            action = DownloadService.ACTION_REMOVE_TASK
            putExtra(DownloadService.EXTRA_TASK_ID, taskId)
            putExtra(DownloadService.EXTRA_DELETE_FILES, deleteFiles)
            putExtra(DownloadService.EXTRA_DELETE_TORRENT_FILE, deleteTorrentFile)
        }
        context.startService(intent)
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
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
                    },
                    actions = {
                        if (anyPaused || anyPausedOnProvider) {
                            IconButton(onClick = {
                                context.startService(Intent(context, DownloadService::class.java).apply {
                                    action = DownloadService.ACTION_RESUME_ALL_LOCAL_DOWNLOADS
                                })
                                context.startService(Intent(context, DownloadService::class.java).apply {
                                    action = DownloadService.ACTION_RESUME_ALL_ON_PROVIDER
                                })
                            }) {
                                Icon(Icons.Default.PlayArrow, contentDescription = "Resume All")
                            }
                        }
                        if (anyDownloading || anyDownloadingOnProvider) {
                            IconButton(onClick = {
                                context.startService(Intent(context, DownloadService::class.java).apply {
                                    action = DownloadService.ACTION_PAUSE_ALL_LOCAL_DOWNLOADS
                                })
                                context.startService(Intent(context, DownloadService::class.java).apply {
                                    action = DownloadService.ACTION_PAUSE_ALL_ON_PROVIDER
                                })
                            }) {
                                Icon(Icons.Default.Pause, contentDescription = "Pause All")
                            }
                        }

                        Box {
                            IconButton(onClick = { navigator.navigate(NavRoute.RssFeeds) }) {
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

                        IconButton(onClick = {
                            navigator.navigate(NavRoute.Settings)
                        }) {
                            Icon(
                                Icons.Default.Settings,
                                contentDescription = stringResource(id = R.string.action_settings)
                            )
                        }
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
                            onRemove = {
                                taskToRemove = task
                                deleteFiles = true
                                deleteTorrentFile = true
                            },
                            provider = provider
                        )
                    }
                }
            }
        }

        taskToRemove?.let { task ->
            AlertDialog(
                onDismissRequest = { taskToRemove = null },
                title = { Text("Remove task") },
                text = {
                    Column {
                        Text("Are you sure you want to remove ${task.name}?")
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { deleteFiles = !deleteFiles }
                                .padding(vertical = 4.dp)
                        ) {
                            Checkbox(checked = deleteFiles, onCheckedChange = { deleteFiles = it })
                            Text("Delete temporary files")
                        }
                        if (task.torrent.type == TorrentType.TORRENT_FILE) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { deleteTorrentFile = !deleteTorrentFile }
                                    .padding(vertical = 4.dp)
                            ) {
                                Checkbox(
                                    checked = deleteTorrentFile,
                                    onCheckedChange = { deleteTorrentFile = it })
                                Text("Delete torrent file")
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            removeTask(
                                task.id,
                                deleteFiles = deleteFiles,
                                deleteTorrentFile = deleteTorrentFile
                            )
                            taskToRemove = null
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Remove")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { taskToRemove = null }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

private data class TaskStateFlags(
    val anyDownloading: Boolean,
    val anyPaused: Boolean,
    val anyDownloadingOnProvider: Boolean,
    val anyPausedOnProvider: Boolean
)

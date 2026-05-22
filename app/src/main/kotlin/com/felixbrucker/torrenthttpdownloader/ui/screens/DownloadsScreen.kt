package com.felixbrucker.torrenthttpdownloader.ui.screens

import android.content.Intent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.felixbrucker.torrenthttpdownloader.ui.DownloadItem
import com.felixbrucker.torrenthttpdownloader.DownloadService
import com.felixbrucker.torrenthttpdownloader.DownloadTracker
import com.felixbrucker.torrenthttpdownloader.NavRoute
import com.felixbrucker.torrenthttpdownloader.Navigator
import com.felixbrucker.torrenthttpdownloader.R
import com.felixbrucker.torrenthttpdownloader.models.LocalDownloadState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    navigator: Navigator,
) {
    val context = LocalContext.current
    val tasks by DownloadTracker.tasks.collectAsState()
    val unreadRssCount by DownloadTracker.totalUnreadRssCount.collectAsState(initial = 0)

    val anyDownloading = tasks.any { task -> task.files.any { it.state == LocalDownloadState.DOWNLOADING || it.state == LocalDownloadState.PENDING } }
    val anyPaused = tasks.any { task -> task.files.any { it.state == LocalDownloadState.PAUSED } }

    fun removeTask(taskId: String) {
        val intent = Intent(context, DownloadService::class.java).apply {
            action = DownloadService.ACTION_REMOVE_TASK
            putExtra(DownloadService.EXTRA_TASK_ID, taskId)
        }
        context.startService(intent)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(id = R.string.app_name)) },
                actions = {
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


                    if (anyDownloading) {
                        IconButton(onClick = {
                            val intent = Intent(context, DownloadService::class.java).apply {
                                action = DownloadService.ACTION_PAUSE_ALL
                            }
                            context.startService(intent)
                        }) {
                            Icon(Icons.Default.Pause, contentDescription = "Pause All")
                        }
                    }
                    if (anyPaused) {
                        IconButton(onClick = {
                            val intent = Intent(context, DownloadService::class.java).apply {
                                action = DownloadService.ACTION_RESUME_ALL
                            }
                            context.startService(intent)
                        }) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Resume All")
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
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(tasks, key = { task -> task.id }) { task ->
                    DownloadItem(task = task, onRemove = { removeTask(task.id) })
                }
            }
        }
    }
}
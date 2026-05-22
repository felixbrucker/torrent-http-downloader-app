package com.felixbrucker.torrenthttpdownloader

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.felixbrucker.torrenthttpdownloader.models.LocalDownloadState
import com.felixbrucker.torrenthttpdownloader.models.RssFeed
import com.felixbrucker.torrenthttpdownloader.models.RssItem
import com.felixbrucker.torrenthttpdownloader.network.TorrentUriResolver
import com.felixbrucker.torrenthttpdownloader.ui.AddTorrentBottomSheet
import com.felixbrucker.torrenthttpdownloader.ui.RssFeedsScreen
import com.felixbrucker.torrenthttpdownloader.ui.AddTorrentConfig
import com.felixbrucker.torrenthttpdownloader.ui.theme.TorrentHttpDownloaderTheme
import com.felixbrucker.torrenthttpdownloader.worker.RssSyncWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {
    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)
    private var pendingConfig by mutableStateOf<AddTorrentConfig?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (savedInstanceState == null) {
            handleIntent(intent)
        }

        setContent {
            TorrentHttpDownloaderTheme {
                val context = LocalContext.current
                var hasNotificationPermission by remember {
                    mutableStateOf(
                        ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.POST_NOTIFICATIONS
                        ) == PackageManager.PERMISSION_GRANTED
                    )
                }

                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission(),
                    onResult = { isGranted ->
                        hasNotificationPermission = isGranted
                    }
                )

                LaunchedEffect(Unit) {
                    if (!hasNotificationPermission) {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }

                MainScreen(
                    pendingConfig = pendingConfig,
                    onConfigDismiss = { pendingConfig = null },
                    onConfigConfirm = { config ->
                        val intent = Intent(this, DownloadService::class.java).apply {
                            action = DownloadService.ACTION_ADD_TASK
                            putExtra(DownloadService.EXTRA_TORRENT_PATH, config.path)
                            putExtra(DownloadService.EXTRA_TORRENT_TYPE, config.type.name)
                            putExtra(DownloadService.EXTRA_DESTINATION_SUBDIRECTORY, config.destinationSubdirectory)
                            putExtra(DownloadService.EXTRA_CREATE_SUBFOLDER_BY_NAME, config.createSubfolderByName)
                            putExtra(DownloadService.EXTRA_TORRENT_NAME, config.name)
                        }
                        startService(intent)

                        if (config.feedId != null && config.feedItemId != null) {
                            DownloadTracker.updateRssFeed(config.feedId) { f ->
                                f.copy(items = f.items.map { if (it.id == config.feedItemId) it.copy(isDownloaded = true) else it })
                            }
                        }

                        pendingConfig = null
                    },
                    syncFeed = { runRssSyncOnce(it.id) },
                    syncFeeds = { runRssSyncOnce() },
                    addFeedItem = { feed, item ->
                        serviceScope.launch {
                            val resolvedTorrent = TorrentUriResolver(context.contentResolver).resolve(item.link.toUri())
                            pendingConfig = AddTorrentConfig(
                                path = resolvedTorrent.uri.toString(),
                                type = resolvedTorrent.type,
                                name = resolvedTorrent.name,
                                createSubfolderByName = feed.createSubfolderByName,
                                destinationSubdirectory = feed.destinationSubdirectory,
                                feedId = feed.id,
                                feedItemId = item.id,
                            )
                        }
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onStart() {
        super.onStart()

        if (DownloadTracker.hasTasksWhichNeedProcessing()) {
            startService(Intent(this, DownloadService::class.java))
        }
        ensureRssSyncIsScheduled()
    }

    private fun ensureRssSyncIsScheduled() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val rssSyncRequest = PeriodicWorkRequestBuilder<RssSyncWorker>(3, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "RssSyncRequest",
            ExistingPeriodicWorkPolicy.KEEP,
            rssSyncRequest,
        )
    }

    private fun runRssSyncOnce(feedId: String? = null) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val rssSyncRequest = OneTimeWorkRequestBuilder<RssSyncWorker>()
            .setConstraints(constraints)
            .setInputData(workDataOf("feedId" to feedId))
            .build()
        WorkManager.getInstance(this).enqueue(rssSyncRequest)
    }

    private fun handleIntent(intent: Intent) {
        val action = intent.action
        val data: Uri? = intent.data

        if (action == Intent.ACTION_VIEW && data != null) {
            serviceScope.launch {
                val resolvedTorrent = TorrentUriResolver(contentResolver).resolve(data)
                pendingConfig = AddTorrentConfig(
                    path = resolvedTorrent.uri.toString(),
                    type = resolvedTorrent.type,
                    name = resolvedTorrent.name,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    pendingConfig: AddTorrentConfig?,
    onConfigDismiss: () -> Unit,
    onConfigConfirm: (AddTorrentConfig) -> Unit,
    syncFeed: (RssFeed) -> Unit,
    syncFeeds: () -> Unit,
    addFeedItem: (RssFeed, RssItem) -> Unit,
) {
    val context = LocalContext.current
    val tasks by DownloadTracker.tasks.collectAsState()
    val unreadRssCount by DownloadTracker.totalUnreadRssCount.collectAsState(initial = 0)
    var showRssScreen by remember { mutableStateOf(false) }

    if (showRssScreen) {
        RssFeedsScreen(
            onBack = { showRssScreen = false },
            onAddItem = addFeedItem,
            syncFeed = syncFeed,
            syncFeeds = syncFeeds,
        )

        pendingConfig?.let { config ->
            AddTorrentBottomSheet(
                config = config,
                onDismiss = onConfigDismiss,
                onConfirm = { updatedConfig ->
                    onConfigConfirm(updatedConfig)
                }
            )
        }

        return
    }

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
                        IconButton(onClick = { showRssScreen = true }) {
                            Icon(Icons.Default.RssFeed, contentDescription = "RSS Feeds")
                        }
                        if (unreadRssCount > 0) {
                            Badge(
                                modifier = Modifier.align(Alignment.TopEnd)
                            ) { Text(unreadRssCount.toString()) }
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
                        context.startActivity(Intent(context, SettingsActivity::class.java))
                    }) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(id = R.string.action_settings))
                    }
                }
            )
        }
    ) {
        LazyColumn(modifier = Modifier.padding(it)) {
            items(tasks, key = { task -> task.id }) { task ->
                DownloadItem(task = task, onRemove = { removeTask(task.id) })
            }
        }

        pendingConfig?.let { config ->
            AddTorrentBottomSheet(
                config = config,
                onDismiss = onConfigDismiss,
                onConfirm = { updatedConfig ->
                    onConfigConfirm(updatedConfig)
                }
            )
        }
    }
}

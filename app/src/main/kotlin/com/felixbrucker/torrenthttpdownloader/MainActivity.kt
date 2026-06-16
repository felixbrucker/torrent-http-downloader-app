package com.felixbrucker.torrenthttpdownloader

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.felixbrucker.torrenthttpdownloader.network.TorrentUriResolver
import com.felixbrucker.torrenthttpdownloader.ui.AddTorrentBottomSheet
import com.felixbrucker.torrenthttpdownloader.ui.AddTorrentConfig
import com.felixbrucker.torrenthttpdownloader.ui.screens.RssFeedsScreen
import com.felixbrucker.torrenthttpdownloader.ui.screens.DownloadsScreen
import com.felixbrucker.torrenthttpdownloader.ui.screens.RssFeedDetailScreen
import com.felixbrucker.torrenthttpdownloader.ui.screens.SettingsScreen
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
    private var isResolvingTorrent by mutableStateOf(false)

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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

                val navigationState = rememberNavigationState(
                    startRoute = NavRoute.Downloads,
                    topLevelRoutes = setOf(NavRoute.Downloads, NavRoute.RssFeeds, NavRoute.Settings)
                )
                val navigator = remember { Navigator(navigationState) }

                val entryProvider = entryProvider {
                    entry<NavRoute.Downloads> {
                        DownloadsScreen(navigator = navigator)
                    }
                    entry<NavRoute.RssFeeds> {
                        RssFeedsScreen(
                            onBack = { navigator.goBack() },
                            syncFeed = { runRssSyncOnce(it.id) },
                            syncFeeds = { runRssSyncOnce() },
                            onNavigateToDetail = { feedId ->
                                navigator.navigate(NavRoute.RssFeedDetail(feedId))
                            }
                        )
                    }
                    entry<NavRoute.RssFeedDetail> { key ->
                        val feeds by DownloadTracker.rssFeeds.collectAsState()
                        val feed = feeds.find { it.id == key.feedId }
                        feed?.let { feed ->
                            RssFeedDetailScreen(
                                feed = feed,
                                onBack = { navigator.goBack() },
                                syncFeed = { runRssSyncOnce(it.id) },
                                addTorrentFromFeed = { feed, item ->
                                    serviceScope.launch {
                                        isResolvingTorrent = true
                                        try {
                                            val resolvedTorrent = TorrentUriResolver(context.contentResolver).resolve(item.link.toUri())
                                            pendingConfig = AddTorrentConfig(
                                                uri = resolvedTorrent.uri.toString(),
                                                type = resolvedTorrent.type,
                                                name = resolvedTorrent.name,
                                                createSubfolderByName = feed.createSubfolderByName,
                                                destinationSubdirectory = feed.destinationSubdirectory,
                                                feedId = feed.id,
                                                feedItemId = item.id,
                                            )
                                        } finally {
                                            isResolvingTorrent = false
                                        }
                                    }
                                }
                            )
                        }
                    }
                    entry<NavRoute.Settings> {
                        SettingsScreen(
                            onBack = { navigator.goBack() },
                            onSave = {
                                val intent = Intent(context, DownloadService::class.java).apply {
                                    action = DownloadService.ACTION_RELOAD_SETTINGS
                                }
                                startService(intent)
                            }
                        )
                    }
                }

                NavDisplay(
                    entries = navigationState.toEntries { entryProvider(it as NavRoute) as NavEntry<NavKey> },
                    onBack = { navigator.goBack() }
                )

                pendingConfig?.let { config ->
                    AddTorrentBottomSheet(
                        config = config,
                        onDismiss = { pendingConfig = null },
                        onConfirm = { updatedConfig ->
                            val intent = Intent(this, DownloadService::class.java).apply {
                                action = DownloadService.ACTION_ADD_TASK
                                putExtra(DownloadService.EXTRA_TORRENT_URI, updatedConfig.uri)
                                putExtra(DownloadService.EXTRA_TORRENT_TYPE, updatedConfig.type.name)
                                putExtra(DownloadService.EXTRA_DESTINATION_SUBDIRECTORY, updatedConfig.destinationSubdirectory)
                                putExtra(DownloadService.EXTRA_CREATE_SUBFOLDER_BY_NAME, updatedConfig.createSubfolderByName)
                                putExtra(DownloadService.EXTRA_NOTIFY_ON_COMPLETION, updatedConfig.notifyOnCompletion)
                                putExtra(DownloadService.EXTRA_ONLY_DOWNLOAD_BIGGEST_FILE, updatedConfig.onlyDownloadBiggestFile)
                                putExtra(DownloadService.EXTRA_TORRENT_NAME, updatedConfig.name)
                            }
                            startService(intent)

                            if (updatedConfig.feedId != null && updatedConfig.feedItemId != null) {
                                DownloadTracker.updateRssFeed(updatedConfig.feedId) { f ->
                                    f.copy(items = f.items.map { if (it.id == updatedConfig.feedItemId) it.copy(isDownloaded = true) else it })
                                }
                            }

                            pendingConfig = null
                        }
                    )
                }

                if (isResolvingTorrent) {
                    Dialog(onDismissRequest = { }) {
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh
                        ) {
                            Box(
                                modifier = Modifier.padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                    }
                }
            }
        }
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
            ExistingPeriodicWorkPolicy.UPDATE,
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

}


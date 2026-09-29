package com.felixbrucker.torrenthttpdownloader

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
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
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import dagger.hilt.android.AndroidEntryPoint
import com.felixbrucker.torrenthttpdownloader.core.designsystem.theme.TorrentHttpDownloaderTheme
import com.felixbrucker.torrenthttpdownloader.feature.addtorrent.AddTorrentContent
import com.felixbrucker.torrenthttpdownloader.feature.addtorrent.AddTorrentViewModel
import com.felixbrucker.torrenthttpdownloader.feature.downloads.DownloadsScreen
import com.felixbrucker.torrenthttpdownloader.feature.rss.RssFeedDetailScreen
import com.felixbrucker.torrenthttpdownloader.feature.rss.RssFeedsScreen
import com.felixbrucker.torrenthttpdownloader.feature.settings.SettingsScreen

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val mainViewModel: MainViewModel by viewModels()
    private val addTorrentViewModel: AddTorrentViewModel by viewModels()

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

                var hasAllFilesPermission by remember {
                    mutableStateOf(Environment.isExternalStorageManager())
                }

                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission(),
                    onResult = { isGranted ->
                        hasNotificationPermission = isGranted
                    }
                )

                val allFilesPermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.StartActivityForResult(),
                    onResult = {
                        hasAllFilesPermission = Environment.isExternalStorageManager()
                    }
                )

                LaunchedEffect(hasNotificationPermission, hasAllFilesPermission) {
                    if (!hasNotificationPermission) {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else if (!hasAllFilesPermission) {
                        val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                            data = "package:${context.packageName}".toUri()
                        }
                        allFilesPermissionLauncher.launch(intent)
                    }
                }

                val navigationState = rememberNavigationState(
                    startRoute = NavRoute.Downloads,
                    topLevelRoutes = setOf(NavRoute.Downloads, NavRoute.RssFeeds, NavRoute.Settings)
                )
                val navigator = remember { Navigator(navigationState) }

                val isResolvingTorrent by addTorrentViewModel.isResolvingTorrent.collectAsState()

                val entryProvider = entryProvider {
                    entry<NavRoute.Downloads> {
                        DownloadsScreen(
                            navigator = navigator
                        )
                    }
                    entry<NavRoute.RssFeeds> {
                        RssFeedsScreen(
                            onBack = { navigator.goBack() },
                            onNavigateToDetail = { feedId ->
                                navigator.navigate(NavRoute.RssFeedDetail(feedId))
                            }
                        )
                    }
                    entry<NavRoute.RssFeedDetail> { key ->
                        RssFeedDetailScreen(
                            feedId = key.feedId,
                            onBack = { navigator.goBack() },
                            addTorrentFromFeed = { feedItem, item ->
                                addTorrentViewModel.resolveTorrentUri(
                                    uri = item.link.toUri(),
                                    createSubfolderByName = feedItem.createSubfolderByName,
                                    destinationSubdirectory = feedItem.destinationSubdirectory,
                                    feedId = feedItem.id,
                                    feedItemId = item.id
                                )
                            }
                        )
                    }
                    entry<NavRoute.Settings> {
                        SettingsScreen(
                            onBack = { navigator.goBack() }
                        )
                    }
                }

                NavDisplay(
                    entries = navigationState.toEntries { entryProvider(it as NavRoute) as NavEntry<NavKey> },
                    onBack = { navigator.goBack() }
                )

                AddTorrentContent(
                    onFinish = { },
                    viewModel = addTorrentViewModel
                )

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

        mainViewModel.checkAndStartDownloadService(this)
        mainViewModel.ensureRssSyncIsScheduled()
    }
}

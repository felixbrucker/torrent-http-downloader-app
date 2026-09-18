package com.felixbrucker.torrenthttpdownloader.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.felixbrucker.torrenthttpdownloader.DownloadTracker
import com.felixbrucker.torrenthttpdownloader.R
import com.felixbrucker.torrenthttpdownloader.models.RssFeed
import com.felixbrucker.torrenthttpdownloader.ui.composable.EditRssFeedDialog
import com.felixbrucker.torrenthttpdownloader.ui.composable.RssFeedItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RssFeedsScreen(
    onBack: () -> Unit,
    syncFeed: (RssFeed) -> Unit,
    syncFeeds: () -> Unit,
    onNavigateToDetail: (String) -> Unit,
) {
    val feeds by DownloadTracker.rssFeeds.collectAsState()
    val isSyncingAll by DownloadTracker.isSyncingAll.collectAsState()
    val syncingFeedIds by DownloadTracker.syncingFeedIds.collectAsState()

    val infiniteTransition = rememberInfiniteTransition(label = "syncRotation")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    var showAddFeedDialog by remember { mutableStateOf(false) }
    var editFeedConfig by remember { mutableStateOf<RssFeed?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.rss_feeds)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = syncFeeds) {
                        Icon(
                            Icons.Default.Sync,
                            contentDescription = "Sync Feeds",
                            modifier = if (isSyncingAll) Modifier.rotate(rotation) else Modifier
                        )
                    }
                    IconButton(onClick = { showAddFeedDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Add Feed")
                    }
                }
            )
        }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = isSyncingAll,
            onRefresh = syncFeeds,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (feeds.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    contentAlignment = Alignment.Center
                ) {
                    Text(stringResource(R.string.no_rss_feeds))
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(feeds, key = { it.id }) { feed ->
                        RssFeedItem(
                            feed = feed,
                            onClick = { onNavigateToDetail(feed.id) },
                            onDelete = { DownloadTracker.removeRssFeed(feed.id) },
                            syncFeed = { syncFeed(feed) },
                            editFeed = { editFeedConfig = feed },
                            isSyncing = syncingFeedIds.contains(feed.id)
                        )
                    }
                }
            }
        }

        if (showAddFeedDialog) {
            EditRssFeedDialog(
                onDismiss = { showAddFeedDialog = false },
                onConfirm = { newFeed ->
                    DownloadTracker.addRssFeed(newFeed)
                    showAddFeedDialog = false
                    syncFeed(newFeed)
                }
            )
        }

        editFeedConfig?.let { editingFeed ->
            EditRssFeedDialog(
                feed = editingFeed,
                onDismiss = { editFeedConfig = null },
                onConfirm = { newFeed ->
                    DownloadTracker.updateRssFeed(newFeed.id) { feed ->
                        val isResetState = feed.url != newFeed.url

                        newFeed.copy(
                            lastCheck = if (isResetState) 0 else newFeed.lastCheck,
                            items = if (isResetState) listOf() else newFeed.items,
                        )
                    }
                    syncFeed(newFeed)
                    editFeedConfig = null
                }
            )
        }
    }
}

package com.felixbrucker.torrenthttpdownloader.feature.rss

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DoneAll
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import com.felixbrucker.torrenthttpdownloader.R
import com.felixbrucker.torrenthttpdownloader.core.data.DownloadTracker
import com.felixbrucker.torrenthttpdownloader.core.data.RssRepository
import com.felixbrucker.torrenthttpdownloader.core.model.RssFeed
import com.felixbrucker.torrenthttpdownloader.core.model.RssItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RssFeedDetailScreen(
    feed: RssFeed,
    onBack: () -> Unit,
    syncFeed: (RssFeed) -> Unit,
    addTorrentFromFeed: (RssFeed, RssItem) -> Unit,
    downloadTracker: DownloadTracker,
    rssRepository: RssRepository,
) {
    val syncingFeedIds by downloadTracker.syncingFeedIds.collectAsState()
    val isSyncing = syncingFeedIds.contains(feed.id)

    Scaffold(
        topBar = {
            RssFeedDetailTopBar(
                feedName = feed.name,
                isSyncing = isSyncing,
                onBack = onBack,
                onSyncFeed = { syncFeed(feed) },
                onMarkAllRead = { rssRepository.markAllItemsAsRead(feed.id) }
            )
        }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = isSyncing,
            onRefresh = { syncFeed(feed) },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (feed.items.isEmpty()) {
                EmptyRssItemsView()
            } else {
                RssItemsList(
                    feed = feed,
                    onItemClick = { item ->
                        rssRepository.markItemAsRead(feed.id, item.id)
                        addTorrentFromFeed(feed, item)
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RssFeedDetailTopBar(
    feedName: String,
    isSyncing: Boolean,
    onBack: () -> Unit,
    onSyncFeed: () -> Unit,
    onMarkAllRead: () -> Unit
) {
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

    TopAppBar(
        title = { Text(feedName) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        },
        actions = {
            IconButton(onClick = onSyncFeed) {
                Icon(
                    Icons.Default.Sync,
                    contentDescription = "Sync Feed",
                    modifier = if (isSyncing) Modifier.rotate(rotation) else Modifier
                )
            }
            IconButton(onClick = onMarkAllRead) {
                Icon(Icons.Default.DoneAll, contentDescription = stringResource(R.string.mark_all_read))
            }
        }
    )
}

@Composable
private fun EmptyRssItemsView() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        contentAlignment = Alignment.Center
    ) {
        Text(stringResource(R.string.no_rss_items))
    }
}

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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.felixbrucker.torrenthttpdownloader.R
import com.felixbrucker.torrenthttpdownloader.core.model.RssFeed
import com.felixbrucker.torrenthttpdownloader.core.model.RssItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RssFeedDetailScreen(
    feedId: String,
    onBack: () -> Unit,
    syncFeed: (RssFeed) -> Unit,
    addTorrentFromFeed: (RssFeed, RssItem) -> Unit,
    viewModel: RssFeedDetailViewModel = viewModel(),
) {
    val feed by viewModel.getFeedFlow(feedId).collectAsState(initial = null)
    val syncingFeedIds by viewModel.syncingFeedIds.collectAsState()
    val currentFeed = feed ?: return
    val isSyncing = syncingFeedIds.contains(currentFeed.id)

    Scaffold(
        topBar = {
            RssFeedDetailTopBar(
                feedName = currentFeed.name,
                isSyncing = isSyncing,
                onBack = onBack,
                onSyncFeed = { syncFeed(currentFeed) },
                onMarkAllRead = { viewModel.markAllItemsAsRead(currentFeed.id) }
            )
        }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = isSyncing,
            onRefresh = { syncFeed(currentFeed) },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (currentFeed.items.isEmpty()) {
                EmptyRssItemsView()
            } else {
                RssItemsList(
                    feed = currentFeed,
                    onItemClick = { item ->
                        viewModel.markItemAsRead(currentFeed.id, item.id)
                        addTorrentFromFeed(currentFeed, item)
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

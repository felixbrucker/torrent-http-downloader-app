package com.felixbrucker.torrenthttpdownloader.feature.rss

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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.felixbrucker.torrenthttpdownloader.R
import com.felixbrucker.torrenthttpdownloader.core.model.RssFeed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RssFeedsScreen(
    onBack: () -> Unit,
    syncFeed: (RssFeed) -> Unit,
    syncFeeds: () -> Unit,
    onNavigateToDetail: (String) -> Unit,
    viewModel: RssFeedsViewModel = viewModel(),
) {
    val feeds by viewModel.feeds.collectAsState()
    val isSyncingAll by viewModel.isSyncingAll.collectAsState()
    val syncingFeedIds by viewModel.syncingFeedIds.collectAsState()

    var showAddFeedDialog by remember { mutableStateOf(false) }
    var editFeedConfig by remember { mutableStateOf<RssFeed?>(null) }

    Scaffold(
        topBar = {
            RssFeedsTopBar(
                isSyncingAll = isSyncingAll,
                onBack = onBack,
                onSyncFeeds = syncFeeds,
                onAddFeedClick = { showAddFeedDialog = true }
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
                EmptyRssFeedsView()
            } else {
                RssFeedsList(
                    feeds = feeds,
                    syncingFeedIds = syncingFeedIds,
                    onNavigateToDetail = onNavigateToDetail,
                    onDeleteFeed = { viewModel.removeRssFeed(it) },
                    syncFeed = syncFeed,
                    onEditFeed = { editFeedConfig = it }
                )
            }
        }

        if (showAddFeedDialog) {
            EditRssFeedDialog(
                onDismiss = { showAddFeedDialog = false },
                onConfirm = { newFeed ->
                    viewModel.addRssFeed(newFeed)
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
                    viewModel.updateFeedConfig(newFeed)
                    syncFeed(newFeed)
                    editFeedConfig = null
                }
            )
        }
    }
}

@Composable
private fun RssFeedsList(
    feeds: List<RssFeed>,
    syncingFeedIds: Set<String>,
    onNavigateToDetail: (String) -> Unit,
    onDeleteFeed: (String) -> Unit,
    syncFeed: (RssFeed) -> Unit,
    onEditFeed: (RssFeed) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(feeds, key = { it.id }) { feed ->
            RssFeedItem(
                feed = feed,
                onClick = { onNavigateToDetail(feed.id) },
                onDelete = { onDeleteFeed(feed.id) },
                syncFeed = { syncFeed(feed) },
                editFeed = { onEditFeed(feed) },
                isSyncing = syncingFeedIds.contains(feed.id)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RssFeedsTopBar(
    isSyncingAll: Boolean,
    onBack: () -> Unit,
    onSyncFeeds: () -> Unit,
    onAddFeedClick: () -> Unit
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
        title = { Text(stringResource(R.string.rss_feeds)) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        },
        actions = {
            IconButton(onClick = onSyncFeeds) {
                Icon(
                    Icons.Default.Sync,
                    contentDescription = "Sync Feeds",
                    modifier = if (isSyncingAll) Modifier.rotate(rotation) else Modifier
                )
            }
            IconButton(onClick = onAddFeedClick) {
                Icon(Icons.Default.Add, contentDescription = "Add Feed")
            }
        }
    )
}

@Composable
private fun EmptyRssFeedsView() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        contentAlignment = Alignment.Center
    ) {
        Text(stringResource(R.string.no_rss_feeds))
    }
}

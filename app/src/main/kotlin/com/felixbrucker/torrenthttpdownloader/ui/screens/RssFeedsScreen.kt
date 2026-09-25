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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.felixbrucker.torrenthttpdownloader.R
import com.felixbrucker.torrenthttpdownloader.models.RssFeed
import com.felixbrucker.torrenthttpdownloader.ui.composable.EditRssFeedDialog
import com.felixbrucker.torrenthttpdownloader.ui.composable.RssFeedItem
import com.felixbrucker.torrenthttpdownloader.ui.viewmodel.RssFeedsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RssFeedsScreen(
    onBack: () -> Unit,
    syncFeed: (RssFeed) -> Unit,
    syncFeeds: () -> Unit,
    onNavigateToDetail: (String) -> Unit,
    viewModel: RssFeedsViewModel = hiltViewModel()
) {
    val feeds by viewModel.feeds.collectAsState()
    var showAddFeedDialog by remember { mutableStateOf(false) }
    var editFeedConfig by remember { mutableStateOf<RssFeed?>(null) }

    Scaffold(
        topBar = {
            RssFeedsTopBar(
                onBack = onBack,
                onSyncFeeds = syncFeeds,
                onAddFeedClick = { showAddFeedDialog = true }
            )
        }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = false,
            onRefresh = syncFeeds,
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            if (feeds.isEmpty()) {
                EmptyRssFeedsView()
            } else {
                RssFeedList(
                    feeds = feeds,
                    onNavigateToDetail = onNavigateToDetail,
                    onDelete = { viewModel.removeFeed(it) },
                    onSync = syncFeed,
                    onEdit = { editFeedConfig = it }
                )
            }
        }

        if (showAddFeedDialog) {
            AddRssFeedDialog(
                onDismiss = { showAddFeedDialog = false },
                onConfirm = { feed ->
                    viewModel.addFeed(feed)
                    showAddFeedDialog = false
                    syncFeed(feed)
                }
            )
        }

        editFeedConfig?.let { feed ->
            EditRssFeedDialogWrapper(
                feed = feed,
                onDismiss = { editFeedConfig = null },
                onConfirm = { updatedFeed ->
                    viewModel.addFeed(updatedFeed)
                    syncFeed(updatedFeed)
                    editFeedConfig = null
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RssFeedsTopBar(
    onBack: () -> Unit,
    onSyncFeeds: () -> Unit,
    onAddFeedClick: () -> Unit
) {
    TopAppBar(
        title = { Text(stringResource(R.string.rss_feeds)) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        },
        actions = {
            IconButton(onClick = onSyncFeeds) {
                Icon(Icons.Default.Sync, contentDescription = "Sync Feeds")
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
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        contentAlignment = Alignment.Center
    ) {
        Text(stringResource(R.string.no_rss_feeds))
    }
}

@Composable
private fun RssFeedList(
    feeds: List<RssFeed>,
    onNavigateToDetail: (String) -> Unit,
    onDelete: (String) -> Unit,
    onSync: (RssFeed) -> Unit,
    onEdit: (RssFeed) -> Unit
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
                onDelete = { onDelete(feed.id) },
                syncFeed = { onSync(feed) },
                editFeed = { onEdit(feed) },
                isSyncing = false
            )
        }
    }
}

@Composable
private fun AddRssFeedDialog(
    onDismiss: () -> Unit,
    onConfirm: (RssFeed) -> Unit
) {
    EditRssFeedDialog(
        onDismiss = onDismiss,
        onConfirm = onConfirm
    )
}

@Composable
private fun EditRssFeedDialogWrapper(
    feed: RssFeed,
    onDismiss: () -> Unit,
    onConfirm: (RssFeed) -> Unit
) {
    EditRssFeedDialog(
        feed = feed,
        onDismiss = onDismiss,
        onConfirm = onConfirm
    )
}

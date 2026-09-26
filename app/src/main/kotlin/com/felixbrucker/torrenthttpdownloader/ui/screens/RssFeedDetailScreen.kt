package com.felixbrucker.torrenthttpdownloader.ui.screens

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
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import com.felixbrucker.torrenthttpdownloader.download.DownloadTracker
import com.felixbrucker.torrenthttpdownloader.R
import com.felixbrucker.torrenthttpdownloader.models.RssFeed
import com.felixbrucker.torrenthttpdownloader.models.RssItem
import com.felixbrucker.torrenthttpdownloader.ui.composable.RssItemsList
import com.felixbrucker.torrenthttpdownloader.ui.viewmodels.RssFeedDetailViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RssFeedDetailScreen(
    feed: RssFeed,
    onBack: () -> Unit,
    syncFeed: (RssFeed) -> Unit,
    addTorrentFromFeed: (RssFeed, RssItem) -> Unit,
    viewModel: RssFeedDetailViewModel = hiltViewModel()
) {
    val syncingFeedIds by DownloadTracker.syncingFeedIds.collectAsState()
    val isSyncing = syncingFeedIds.contains(feed.id)

    Scaffold(
        topBar = {
            RssDetailTopBar(
                feed = feed,
                onBack = onBack,
                onSync = { syncFeed(feed) },
                onMarkAllRead = {
                    val updatedFeed = feed.copy(items = feed.items.map { it.copy(isRead = true) })
                    viewModel.updateFeed(updatedFeed)
                }
            )
        }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = isSyncing,
            onRefresh = { syncFeed(feed) },
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            if (feed.items.isEmpty()) {
                EmptyRssItemsView()
            } else {
                RssItemsList(
                    feed = feed,
                    onItemClick = { item ->
                        val updatedFeed = feed.copy(items = feed.items.map {
                            if (it.id == item.id) it.copy(isRead = true) else it
                        })
                        viewModel.updateFeed(updatedFeed)
                        addTorrentFromFeed(feed, item)
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RssDetailTopBar(
    feed: RssFeed,
    onBack: () -> Unit,
    onSync: () -> Unit,
    onMarkAllRead: () -> Unit
) {
    TopAppBar(
        title = { Text(feed.name) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        },
        actions = {
            IconButton(onClick = onSync) {
                Icon(Icons.Default.Sync, contentDescription = "Sync Feed")
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
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        contentAlignment = Alignment.Center
    ) {
        Text(stringResource(R.string.no_rss_items))
    }
}

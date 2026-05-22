package com.felixbrucker.torrenthttpdownloader.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import com.felixbrucker.torrenthttpdownloader.DownloadTracker
import com.felixbrucker.torrenthttpdownloader.R
import com.felixbrucker.torrenthttpdownloader.models.RssFeed
import com.felixbrucker.torrenthttpdownloader.models.RssItem
import com.felixbrucker.torrenthttpdownloader.ui.RssItemsList

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RssFeedDetailScreen(
    feed: RssFeed,
    onBack: () -> Unit,
    syncFeed: (RssFeed) -> Unit,
    addTorrentFromFeed: (RssFeed, RssItem) -> Unit,
) {
    val syncingFeedIds by DownloadTracker.syncingFeedIds.collectAsState()
    val isSyncing = syncingFeedIds.contains(feed.id)

    val infiniteTransition = rememberInfiniteTransition(label = "syncRotation")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(feed.name) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { syncFeed(feed) }) {
                        Icon(
                            Icons.Default.Sync,
                            contentDescription = "Sync Feed",
                            modifier = if (isSyncing) Modifier.rotate(rotation) else Modifier
                        )
                    }
                    IconButton(onClick = {
                        DownloadTracker.updateRssFeed(feed.id) { feed ->
                            feed.copy(items = feed.items.map { it.copy(isRead = true) })
                        }
                    }) {
                        Icon(Icons.Default.DoneAll, contentDescription = stringResource(R.string.mark_all_read))
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            RssItemsList(
                feed = feed,
                onItemClick = { item ->
                    DownloadTracker.updateRssFeed(feed.id) { f ->
                        f.copy(items = f.items.map {
                            if (it.id == item.id) it.copy(isRead = true) else it
                        })
                    }
                    addTorrentFromFeed(feed, item)
                }
            )
        }
    }
}
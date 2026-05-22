package com.felixbrucker.torrenthttpdownloader.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.felixbrucker.torrenthttpdownloader.DownloadTracker
import com.felixbrucker.torrenthttpdownloader.R
import com.felixbrucker.torrenthttpdownloader.models.RssFeed
import com.felixbrucker.torrenthttpdownloader.models.RssItem
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RssFeedsScreen(
    onBack: () -> Unit,
    onAddItem: (RssFeed, RssItem) -> Unit,
    syncFeed: (RssFeed) -> Unit,
    syncFeeds: () -> Unit,
) {
    val feeds by DownloadTracker.rssFeeds.collectAsState()
    var selectedFeedId by remember { mutableStateOf<String?>(null) }
    val selectedFeed = remember(feeds, selectedFeedId) { feeds.find { it.id == selectedFeedId } }
    var showAddFeedDialog by remember { mutableStateOf(false) }
    var editFeedConfig by remember { mutableStateOf<RssFeed?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(selectedFeed?.name ?: stringResource(R.string.rss_feeds)) },
                navigationIcon = {
                    IconButton(onClick = {
                        if (selectedFeedId != null) selectedFeedId = null else onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (selectedFeedId == null) {
                        IconButton(onClick = syncFeeds) {
                            Icon(Icons.Default.Sync, contentDescription = "Sync Feeds")
                        }
                        IconButton(onClick = { showAddFeedDialog = true }) {
                            Icon(Icons.Default.Add, contentDescription = "Add Feed")
                        }
                    } else {
                        IconButton(onClick = { syncFeed(selectedFeed!!) }) {
                            Icon(Icons.Default.Sync, contentDescription = "Sync Feed")
                        }
                        IconButton(onClick = {
                            DownloadTracker.updateRssFeed(selectedFeedId!!) { feed ->
                                feed.copy(items = feed.items.map { it.copy(isRead = true) })
                            }
                        }) {
                            Icon(Icons.Default.DoneAll, contentDescription = stringResource(R.string.mark_all_read))
                        }
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            if (selectedFeedId == null) {
                if (feeds.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.no_rss_feeds))
                    }
                } else {
                    LazyColumn {
                        items(feeds, key = { it.id }) { feed ->
                            RssFeedItem(
                                feed = feed,
                                onClick = { selectedFeedId = feed.id },
                                onDelete = { DownloadTracker.removeRssFeed(feed.id) },
                                syncFeed = { syncFeed(feed) },
                                editFeed = { editFeedConfig = feed },
                            )
                        }
                    }
                }
            } else {
                selectedFeed?.let { feed ->
                    RssItemsList(
                        feed = feed,
                        onItemClick = { item ->
                            DownloadTracker.updateRssFeed(feed.id) { f ->
                                f.copy(items = f.items.map {
                                    if (it.id == item.id) it.copy(isRead = true) else it
                                })
                            }
                            onAddItem(feed, item)
                        }
                    )
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

                        feed.copy(
                            name = newFeed.name,
                            url = newFeed.url,
                            destinationSubdirectory = newFeed.destinationSubdirectory,
                            createSubfolderByName = newFeed.createSubfolderByName,
                            autoDownload = newFeed.autoDownload,
                            lastCheck = if (isResetState) 0 else feed.lastCheck,
                            items = if (isResetState) listOf() else feed.items,
                        )
                    }
                    syncFeed(newFeed)
                    editFeedConfig = null
                }
            )
        }
    }
}

@Composable
fun RssFeedItem(
    feed: RssFeed,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    syncFeed: () -> Unit,
    editFeed: () -> Unit,
) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = { Text(feed.name) },
        supportingContent = { Text(feed.url, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (feed.unreadCount > 0) {
                    Badge { Text(feed.unreadCount.toString()) }
                    Spacer(modifier = Modifier.width(8.dp))
                }
                IconButton(onClick = syncFeed) {
                    Icon(Icons.Default.Sync, contentDescription = "Sync")
                }
                IconButton(onClick = editFeed) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit")
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete")
                }
            }
        }
    )
}

@Composable
fun RssItemsList(feed: RssFeed, onItemClick: (RssItem) -> Unit) {
    LazyColumn {
        items(feed.items, key = { it.id }) { item ->
            RssItemRow(item = item, onClick = { onItemClick(item) })
        }
    }
}

@Composable
fun RssItemRow(item: RssItem, onClick: () -> Unit) {
    val dateFormat = remember { SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()) }
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = {
            Text(
                text = item.title,
                fontWeight = if (item.isRead) FontWeight.Normal else FontWeight.Bold
            )
        },
        supportingContent = {
            Column {
                if (!item.description.isNullOrBlank()) {
                    Text(item.description, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                item.pubDate?.let {
                    Text(dateFormat.format(Date(it)), style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        trailingContent = {
            if (item.isDownloaded) {
                Icon(Icons.Default.DownloadDone, contentDescription = "Downloaded", tint = MaterialTheme.colorScheme.primary)
            } else {
                Icon(Icons.Default.Download, contentDescription = "Download")
            }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditRssFeedDialog(
    feed: RssFeed? = null,
    onDismiss: () -> Unit,
    onConfirm: (RssFeed) -> Unit
) {
    val isNewFeed = feed == null
    val feed = feed ?: RssFeed.make()
    var name by remember { mutableStateOf(feed.name) }
    var url by remember { mutableStateOf(feed.url) }
    var autoDownload by remember { mutableStateOf(feed.autoDownload) }
    var selectedSubDir by remember { mutableStateOf(feed.destinationSubdirectory) }
    var createSubfolderByName by remember { mutableStateOf(feed.createSubfolderByName) }
    val titleTextResource by remember { mutableIntStateOf(if (isNewFeed) R.string.add_rss_feed else R.string.edit_rss_feed) }
    val confirmTextResource by remember { mutableIntStateOf(if (isNewFeed) R.string.add else R.string.save) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(titleTextResource)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                TextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.rss_feed_name)) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                TextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text(stringResource(R.string.rss_feed_url)) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))

                AddTorrentConfigFields(
                    selectedSubDir = selectedSubDir,
                    createSubfolderByName = createSubfolderByName,
                    onSubdirectorySelected = { selectedSubDir = it },
                    onCreateSubfolderByNameChanged = { createSubfolderByName = it }
                )

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { autoDownload = !autoDownload }) {
                    Checkbox(checked = autoDownload, onCheckedChange = { autoDownload = it })
                    Text(stringResource(R.string.auto_download))
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val newFeed = feed.copy(
                        name = name,
                        url = url,
                        destinationSubdirectory = selectedSubDir,
                        createSubfolderByName = createSubfolderByName,
                        autoDownload = autoDownload
                    )
                    onConfirm(newFeed)
                },
                enabled = name.isNotBlank() && url.isNotBlank()
            ) {
                Text(stringResource(confirmTextResource))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

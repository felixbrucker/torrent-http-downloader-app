package com.felixbrucker.torrenthttpdownloader.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.RssFeed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
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
    var showMenu by remember { mutableStateOf(false) }

    ElevatedCard(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        ListItem(
            headlineContent = { Text(feed.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) },
            supportingContent = {
                Text(
                    feed.url,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            leadingContent = {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Outlined.RssFeed,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            },
            trailingContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (feed.unreadCount > 0) {
                        Badge(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ) {
                            Text(feed.unreadCount.toString())
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    IconButton(onClick = syncFeed) {
                        Icon(Icons.Default.Sync, contentDescription = "Sync", modifier = Modifier.size(20.dp))
                    }
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More", modifier = Modifier.size(20.dp))
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.edit_rss_feed)) },
                                onClick = {
                                    showMenu = false
                                    editFeed()
                                },
                                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    showMenu = false
                                    onDelete()
                                },
                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) }
                            )
                        }
                    }
                }
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        )
    }
}

@Composable
fun RssItemsList(feed: RssFeed, onItemClick: (RssItem) -> Unit) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(feed.items, key = { it.id }) { item ->
            RssItemRow(item = item, onClick = { onItemClick(item) })
        }
    }
}

@Composable
fun RssItemRow(item: RssItem, onClick: () -> Unit) {
    val dateFormat = remember { SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()) }
    val contentAlpha = if (item.isRead) 0.6f else 1f

    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = if (!item.isRead) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.1f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = BorderStroke(
            width = 1.dp,
            color = if (!item.isRead) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            }
        ),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = if (!item.isRead) 2.dp else 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(if (!item.isRead) MaterialTheme.colorScheme.primary else Color.Transparent)
            )

            ListItem(
                headlineContent = {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (item.isRead) FontWeight.Normal else FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                supportingContent = {
                    Column {
                        if (!item.description.isNullOrBlank()) {
                            Text(
                                text = item.description,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                        item.pubDate?.let {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Schedule,
                                    contentDescription = null,
                                    modifier = Modifier.size(12.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha * 0.6f)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = dateFormat.format(Date(it)),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha * 0.7f)
                                )
                            }
                        }
                    }
                },
                trailingContent = {
                    if (item.isDownloaded) {
                        Surface(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                            shape = CircleShape,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Downloaded",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    } else {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = "Download",
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = contentAlpha),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
            )
        }
    }
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

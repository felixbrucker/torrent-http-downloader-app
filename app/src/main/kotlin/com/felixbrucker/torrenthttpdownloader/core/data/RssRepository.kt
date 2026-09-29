package com.felixbrucker.torrenthttpdownloader.core.data

import com.felixbrucker.torrenthttpdownloader.core.model.RssFeed
import com.felixbrucker.torrenthttpdownloader.core.model.RssItem
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

@Singleton
class RssRepository @Inject constructor(
    private val downloadTracker: DownloadTracker,
) {
    val rssFeeds: StateFlow<List<RssFeed>>
        get() = downloadTracker.rssFeeds

    val syncingFeedIds: StateFlow<Set<String>>
        get() = downloadTracker.syncingFeedIds

    val isSyncingAll: StateFlow<Boolean>
        get() = downloadTracker.isSyncingAll

    val totalUnreadRssCount: Flow<Int>
        get() = downloadTracker.totalUnreadRssCount

    fun addRssFeed(feed: RssFeed) {
        downloadTracker.addRssFeed(feed)
    }

    fun updateRssFeed(id: String, update: (RssFeed) -> RssFeed) {
        downloadTracker.updateRssFeed(id, update)
    }

    fun removeRssFeed(id: String) {
        downloadTracker.removeRssFeed(id)
    }

    fun setFeedSyncing(feedId: String, syncing: Boolean) {
        downloadTracker.setFeedSyncing(feedId, syncing)
    }

    fun setAllFeedsSyncing(syncing: Boolean) {
        downloadTracker.setAllFeedsSyncing(syncing)
    }

    fun markAllItemsAsRead(feedId: String) {
        downloadTracker.updateRssFeed(feedId) { feed ->
            feed.copy(items = feed.items.map { it.copy(isRead = true) })
        }
    }

    fun markItemAsRead(feedId: String, itemId: String) {
        downloadTracker.updateRssFeed(feedId) { feed ->
            feed.copy(items = feed.items.map {
                if (it.id == itemId) it.copy(isRead = true) else it
            })
        }
    }

    fun markItemAsDownloaded(feedId: String, itemId: String) {
        downloadTracker.updateRssFeed(feedId) { feed ->
            feed.copy(items = feed.items.map {
                if (it.id == itemId) it.copy(isDownloaded = true) else it
            })
        }
    }

    fun markItemAsDownloadedAndRead(feedId: String, itemId: String) {
        downloadTracker.updateRssFeed(feedId) { feed ->
            feed.copy(items = feed.items.map {
                if (it.id == itemId) it.copy(isDownloaded = true, isRead = true) else it
            })
        }
    }

    fun updateFeedConfig(newFeed: RssFeed) {
        downloadTracker.updateRssFeed(newFeed.id) { feed ->
            val isResetState = feed.url != newFeed.url

            newFeed.copy(
                lastCheck = if (isResetState) 0 else newFeed.lastCheck,
                items = if (isResetState) emptyList() else newFeed.items,
            )
        }
    }

    fun updateDiscoveredItems(
        feedId: String,
        newlyDiscoveredItems: List<RssItem>,
        totalNewItemsCount: Int,
        lastCheck: Long = System.currentTimeMillis(),
    ) {
        downloadTracker.updateRssFeed(feedId) { currentFeed ->
            val updatedItems = (newlyDiscoveredItems + currentFeed.items)
                .distinctBy { it.id }
                .take(max(totalNewItemsCount, 25))
            currentFeed.copy(
                items = updatedItems,
                lastCheck = lastCheck,
            )
        }
    }

    fun updateLastCheck(feedId: String, lastCheck: Long = System.currentTimeMillis()) {
        downloadTracker.updateRssFeed(feedId) { feed ->
            feed.copy(lastCheck = lastCheck)
        }
    }
}

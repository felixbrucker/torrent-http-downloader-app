package com.felixbrucker.torrenthttpdownloader.core.data

import com.felixbrucker.torrenthttpdownloader.core.model.RssFeed
import com.felixbrucker.torrenthttpdownloader.core.model.RssItem
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

@Singleton
class RssRepository @Inject constructor(
    private val downloadTracker: DownloadTracker,
) {
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

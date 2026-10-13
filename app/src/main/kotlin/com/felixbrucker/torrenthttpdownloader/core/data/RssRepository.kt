package com.felixbrucker.torrenthttpdownloader.core.data

import com.felixbrucker.torrenthttpdownloader.core.database.dao.RssDao
import com.felixbrucker.torrenthttpdownloader.core.database.entity.RssFeedEntity
import com.felixbrucker.torrenthttpdownloader.core.database.entity.RssFeedWithItems
import com.felixbrucker.torrenthttpdownloader.core.database.entity.RssItemEntity
import com.felixbrucker.torrenthttpdownloader.core.model.RssFeed
import com.felixbrucker.torrenthttpdownloader.core.model.RssItem
import com.felixbrucker.torrenthttpdownloader.di.ApplicationScope
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import timber.log.Timber

@Singleton
class RssRepository @Inject constructor(
    private val rssDao: RssDao,
    @param:ApplicationScope private val scope: CoroutineScope,
) {
    val rssFeeds: StateFlow<List<RssFeed>> = rssDao.getFeedsWithItemsFlow()
        .map { map ->
            map.map { (feed, items) -> RssFeedWithItems(feed, items).toRssFeed() }
        }
        .stateIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList(),
        )

    private val _syncingFeedIds = MutableStateFlow<Set<String>>(emptySet())
    val syncingFeedIds: StateFlow<Set<String>> = _syncingFeedIds.asStateFlow()

    private val _isSyncingAll = MutableStateFlow(false)
    val isSyncingAll: StateFlow<Boolean> = _isSyncingAll.asStateFlow()

    val totalUnreadRssCount: Flow<Int> = rssDao.getTotalUnreadCountFlow()

    suspend fun addRssFeed(feed: RssFeed) {
        Timber.i("Adding RSS feed name=%s, url=%s", feed.name, feed.url)
        val entity = RssFeedEntity.fromDomain(feed)
        rssDao.insertFeed(entity)
    }

    suspend fun findRssFeed(id: String): RssFeed? {
        val map = rssDao.getFeedWithItems(id)
        return map.entries.firstOrNull()?.let { (feed, items) -> RssFeedWithItems(feed, items).toRssFeed() }
    }

    fun getFeedFlow(id: String): Flow<RssFeed?> {
        return rssDao.getFeedWithItemsFlow(id).map { map ->
            map.entries.firstOrNull()?.let { (feed, items) -> RssFeedWithItems(feed, items).toRssFeed() }
        }
    }

    suspend fun removeRssFeed(id: String) {
        Timber.i("Removing RSS feed id=%s", id)
        rssDao.deleteFeed(id)
    }

    fun setFeedSyncing(feedId: String, syncing: Boolean) {
        _syncingFeedIds.update { if (syncing) it + feedId else it - feedId }
    }

    fun setAllFeedsSyncing(syncing: Boolean) {
        _isSyncingAll.value = syncing
    }

    suspend fun markAllItemsAsRead(feedId: String) {
        rssDao.markAllItemsAsRead(feedId)
    }

    suspend fun markItemAsRead(feedId: String, itemId: String) {
        rssDao.markItemAsRead(feedId, itemId)
    }

    suspend fun markItemAsDownloaded(feedId: String, itemId: String) {
        rssDao.markItemAsDownloaded(feedId, itemId)
    }

    suspend fun markItemAsDownloadedAndRead(feedId: String, itemId: String) {
        rssDao.markItemAsDownloadedAndRead(feedId, itemId)
    }

    suspend fun updateFeedConfig(newFeed: RssFeed) {
        val currentFeed = rssDao.getFeed(newFeed.id) ?: return
        val isResetState = currentFeed.url != newFeed.url
        if (isResetState) {
            rssDao.deleteItemsForFeed(newFeed.id)
        }
        val updatedFeed = currentFeed.updatedWith(RssFeedEntity.fromDomain(newFeed))
        rssDao.updateFeed(updatedFeed)
    }

    suspend fun updateDiscoveredItems(
        feedId: String,
        newlyDiscoveredItems: List<RssItem>,
        totalNewItemsCount: Int,
        lastCheck: Long = System.currentTimeMillis(),
    ) {
        Timber.i("Updated RSS feed id=%s with %d newly discovered items", feedId, newlyDiscoveredItems.size)
        val itemEntities = newlyDiscoveredItems.map { RssItemEntity.fromDomain(feedId, it) }
        rssDao.insertItems(itemEntities)
        rssDao.updateLastCheck(feedId, lastCheck)

        val limit = max(totalNewItemsCount, 25)
        rssDao.deleteOldItems(feedId, limit)
    }

    suspend fun updateLastCheck(feedId: String, lastCheck: Long = System.currentTimeMillis()) {
        rssDao.updateLastCheck(feedId, lastCheck)
    }
}

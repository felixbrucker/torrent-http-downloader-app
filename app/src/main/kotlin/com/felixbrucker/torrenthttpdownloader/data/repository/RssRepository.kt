package com.felixbrucker.torrenthttpdownloader.data.repository

import com.felixbrucker.torrenthttpdownloader.data.database.RssFeedDao
import com.felixbrucker.torrenthttpdownloader.data.database.RssFeedEntity
import com.felixbrucker.torrenthttpdownloader.data.database.RssItemEntity
import com.felixbrucker.torrenthttpdownloader.models.RssFeed
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RssRepository @Inject constructor(
    private val rssFeedDao: RssFeedDao
) {
    val feedsFlow: Flow<List<RssFeed>>
        get() = rssFeedDao.getFeedsFlow().map { list ->
            list.map { details -> details.toDomainModel() }
        }

    suspend fun getFeedById(id: String): RssFeed? {
        val details = rssFeedDao.getFeedById(id) ?: return null
        return details.toDomainModel()
    }

    suspend fun insertFeed(feed: RssFeed) {
        val feedEntity = RssFeedEntity(
            id = feed.id,
            name = feed.name,
            url = feed.url,
            destinationSubdirectory = feed.destinationSubdirectory,
            createSubfolderByName = feed.createSubfolderByName,
            notifyOnCompletion = feed.notifyOnCompletion,
            fileSelectionMode = feed.fileSelectionMode,
            autoDownload = feed.autoDownload,
            lastCheck = feed.lastCheck
        )
        rssFeedDao.insertFeed(feedEntity)

        val itemEntities = feed.items.map { item ->
            RssItemEntity(
                id = item.id,
                feedId = feed.id,
                title = item.title,
                link = item.link,
                description = item.description,
                pubDate = item.pubDate,
                isRead = item.isRead,
                isDownloaded = item.isDownloaded
            )
        }
        if (itemEntities.isNotEmpty()) {
            rssFeedDao.insertItems(itemEntities)
        }
    }

    suspend fun insertItems(feedId: String, items: List<com.felixbrucker.torrenthttpdownloader.models.RssItem>) {
        val itemEntities = items.map { item ->
            RssItemEntity(
                id = item.id,
                feedId = feedId,
                title = item.title,
                link = item.link,
                description = item.description,
                pubDate = item.pubDate,
                isRead = item.isRead,
                isDownloaded = item.isDownloaded
            )
        }
        if (itemEntities.isNotEmpty()) {
            rssFeedDao.insertItems(itemEntities)
        }
    }

    suspend fun updateItemState(id: String, isRead: Boolean, isDownloaded: Boolean) {
        rssFeedDao.updateItemState(id, isRead, isDownloaded)
    }

    suspend fun updateFeedLastCheck(id: String, lastCheck: Long) {
        rssFeedDao.updateFeedLastCheck(id, lastCheck)
    }

    suspend fun updateFeedDetails(feed: RssFeed) {
        rssFeedDao.updateFeedDetails(
            id = feed.id,
            name = feed.name,
            url = feed.url,
            destinationSubdirectory = feed.destinationSubdirectory,
            createSubfolderByName = feed.createSubfolderByName,
            notifyOnCompletion = feed.notifyOnCompletion,
            fileSelectionMode = feed.fileSelectionMode,
            autoDownload = feed.autoDownload
        )
    }

    suspend fun deleteFeed(id: String) {
        rssFeedDao.deleteFeedById(id)
    }

    suspend fun deleteAllFeeds() {
        rssFeedDao.deleteAllFeeds()
    }
}

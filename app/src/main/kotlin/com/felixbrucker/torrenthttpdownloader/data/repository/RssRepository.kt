package com.felixbrucker.torrenthttpdownloader.data.repository

import com.felixbrucker.torrenthttpdownloader.data.database.RssFeedDao
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

    suspend fun saveFeed(feed: RssFeed) {
        rssFeedDao.upsertFeed(feed)
    }

    suspend fun saveFeeds(feeds: List<RssFeed>) {
        for (feed in feeds) {
            rssFeedDao.upsertFeed(feed)
        }
    }

    suspend fun deleteFeed(id: String) {
        rssFeedDao.deleteFeedById(id)
    }

    suspend fun deleteAllFeeds() {
        rssFeedDao.deleteAllFeeds()
    }
}

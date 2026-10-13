package com.felixbrucker.torrenthttpdownloader.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.felixbrucker.torrenthttpdownloader.core.database.entity.RssFeedEntity
import com.felixbrucker.torrenthttpdownloader.core.database.entity.RssItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RssDao {

    @Transaction
    @Query(
        """
        SELECT * FROM rss_feeds
        LEFT JOIN rss_items ON rss_feeds.id = rss_items.feedId
        ORDER BY rss_feeds.name ASC, COALESCE(rss_items.pubDate, 0) DESC
        """
    )
    fun getFeedsWithItemsFlow(): Flow<Map<RssFeedEntity, List<RssItemEntity>>>

    @Transaction
    @Query(
        """
        SELECT * FROM rss_feeds
        LEFT JOIN rss_items ON rss_feeds.id = rss_items.feedId
        ORDER BY rss_feeds.name ASC, COALESCE(rss_items.pubDate, 0) DESC
        """
    )
    suspend fun getFeedsWithItems(): Map<RssFeedEntity, List<RssItemEntity>>

    @Transaction
    @Query(
        """
        SELECT * FROM rss_feeds
        LEFT JOIN rss_items ON rss_feeds.id = rss_items.feedId
        WHERE rss_feeds.id = :id
        ORDER BY COALESCE(rss_items.pubDate, 0) DESC
        """
    )
    suspend fun getFeedWithItems(id: String): Map<RssFeedEntity, List<RssItemEntity>>

    @Query("SELECT * FROM rss_feeds WHERE id = :id")
    suspend fun getFeed(id: String): RssFeedEntity?

    @Transaction
    @Query(
        """
        SELECT * FROM rss_feeds
        LEFT JOIN rss_items ON rss_feeds.id = rss_items.feedId
        WHERE rss_feeds.id = :id
        ORDER BY COALESCE(rss_items.pubDate, 0) DESC
        """
    )
    fun getFeedWithItemsFlow(id: String): Flow<Map<RssFeedEntity, List<RssItemEntity>>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFeed(feed: RssFeedEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertItems(items: List<RssItemEntity>)

    @Query("DELETE FROM rss_feeds WHERE id = :id")
    suspend fun deleteFeed(id: String)

    @Query("UPDATE rss_items SET isRead = 1 WHERE feedId = :feedId")
    suspend fun markAllItemsAsRead(feedId: String)

    @Query("UPDATE rss_items SET isRead = 1 WHERE feedId = :feedId AND id = :itemId")
    suspend fun markItemAsRead(feedId: String, itemId: String)

    @Query("UPDATE rss_items SET isDownloaded = 1 WHERE feedId = :feedId AND id = :itemId")
    suspend fun markItemAsDownloaded(feedId: String, itemId: String)

    @Query("UPDATE rss_items SET isDownloaded = 1, isRead = 1 WHERE feedId = :feedId AND id = :itemId")
    suspend fun markItemAsDownloadedAndRead(feedId: String, itemId: String)

    @Query("SELECT COUNT(*) FROM rss_items WHERE isRead = 0")
    fun getTotalUnreadCountFlow(): Flow<Int>

    @Update
    suspend fun updateFeed(feed: RssFeedEntity)

    @Query("DELETE FROM rss_items WHERE feedId = :feedId")
    suspend fun deleteItemsForFeed(feedId: String)

    @Query("DELETE FROM rss_items WHERE feedId = :feedId AND id IN (:itemIds)")
    suspend fun deleteItems(feedId: String, itemIds: List<String>)

    @Query("DELETE FROM rss_items WHERE feedId = :feedId AND id NOT IN (SELECT id FROM rss_items WHERE feedId = :feedId ORDER BY COALESCE(pubDate, 0) DESC LIMIT :limit)")
    suspend fun deleteOldItems(feedId: String, limit: Int)

    @Query("UPDATE rss_feeds SET lastCheck = :lastCheck WHERE id = :feedId")
    suspend fun updateLastCheck(feedId: String, lastCheck: Long)
}

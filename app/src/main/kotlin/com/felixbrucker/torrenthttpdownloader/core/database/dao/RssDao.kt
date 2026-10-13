package com.felixbrucker.torrenthttpdownloader.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.felixbrucker.torrenthttpdownloader.core.database.entity.RssFeedEntity
import com.felixbrucker.torrenthttpdownloader.core.database.entity.RssFeedWithItems
import com.felixbrucker.torrenthttpdownloader.core.database.entity.RssItemEntity
import com.felixbrucker.torrenthttpdownloader.core.model.FileSelectionMode
import kotlinx.coroutines.flow.Flow

@Dao
interface RssDao {

    @Transaction
    @Query("SELECT * FROM rss_feeds ORDER BY name ASC")
    fun getFeedsWithItemsFlow(): Flow<List<RssFeedWithItems>>

    @Transaction
    @Query("SELECT * FROM rss_feeds ORDER BY name ASC")
    suspend fun getFeedsWithItems(): List<RssFeedWithItems>

    @Transaction
    @Query("SELECT * FROM rss_feeds WHERE id = :id")
    suspend fun getFeedWithItems(id: String): RssFeedWithItems?

    @Query("SELECT * FROM rss_feeds WHERE id = :id")
    suspend fun getFeed(id: String): RssFeedEntity?

    @Transaction
    @Query("SELECT * FROM rss_feeds WHERE id = :id")
    fun getFeedWithItemsFlow(id: String): Flow<RssFeedWithItems?>

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

    @Query("UPDATE rss_feeds SET lastCheck = :lastCheck WHERE id = :feedId")
    suspend fun updateLastCheck(feedId: String, lastCheck: Long)
}

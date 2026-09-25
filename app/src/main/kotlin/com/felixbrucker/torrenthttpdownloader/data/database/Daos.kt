package com.felixbrucker.torrenthttpdownloader.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.felixbrucker.torrenthttpdownloader.models.LocalDownloadState
import com.felixbrucker.torrenthttpdownloader.models.TorrentState
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {
    @Transaction
    @Query("SELECT * FROM download_tasks")
    fun getTasksFlow(): Flow<List<DownloadTaskWithDetails>>

    @Transaction
    @Query("SELECT * FROM download_tasks WHERE id = :id")
    suspend fun getTaskById(id: String): DownloadTaskWithDetails?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: DownloadTaskEntity)

    @Update
    suspend fun updateTask(task: DownloadTaskEntity)

    @Query("UPDATE download_tasks SET state = :state, errorMessage = :errorMessage WHERE id = :id")
    suspend fun updateTaskState(id: String, state: TorrentState, errorMessage: String?)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProviderInfo(info: ProviderTorrentInfoEntity)

    @Update
    suspend fun updateProviderInfo(info: ProviderTorrentInfoEntity)

    @Query("DELETE FROM provider_torrent_info WHERE taskId = :taskId")
    suspend fun deleteProviderInfoForTask(taskId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProviderFiles(files: List<ProviderTorrentFileEntity>)

    @Query("DELETE FROM provider_torrent_files WHERE providerInfoId = :providerInfoId")
    suspend fun deleteProviderFiles(providerInfoId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProviderLinks(links: List<ProviderTorrentLinkEntity>)

    @Query("DELETE FROM provider_torrent_links WHERE providerInfoId = :providerInfoId")
    suspend fun deleteProviderLinks(providerInfoId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFiles(files: List<DownloadFileEntity>)

    @Query("UPDATE download_files SET state = :state, progress = :progress, speed = :speed, downloadedBytes = :downloadedBytes WHERE taskId = :taskId AND link = :link")
    suspend fun updateFileProgress(
        taskId: String,
        link: String,
        state: LocalDownloadState,
        progress: Int,
        speed: Long,
        downloadedBytes: Long
    )

    @Query("DELETE FROM download_files WHERE taskId = :taskId")
    suspend fun deleteFilesForTask(taskId: String)

    @Query("DELETE FROM download_tasks WHERE id = :id")
    suspend fun deleteTaskById(id: String)

    @Query("DELETE FROM download_tasks")
    suspend fun deleteAllTasks()
}

@Dao
interface RssFeedDao {
    @Transaction
    @Query("SELECT * FROM rss_feeds")
    fun getFeedsFlow(): Flow<List<RssFeedWithItems>>

    @Transaction
    @Query("SELECT * FROM rss_feeds WHERE id = :id")
    suspend fun getFeedById(id: String): RssFeedWithItems?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFeed(feed: RssFeedEntity)

    @Update
    suspend fun updateFeed(feed: RssFeedEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<RssItemEntity>)

    @Query("UPDATE rss_items SET isRead = :isRead, isDownloaded = :isDownloaded WHERE id = :id")
    suspend fun updateItemState(id: String, isRead: Boolean, isDownloaded: Boolean)

    @Query("DELETE FROM rss_items WHERE feedId = :feedId")
    suspend fun deleteItemsForFeed(feedId: String)

    @Query("DELETE FROM rss_feeds WHERE id = :id")
    suspend fun deleteFeedById(id: String)

    @Query("DELETE FROM rss_feeds")
    suspend fun deleteAllFeeds()
}

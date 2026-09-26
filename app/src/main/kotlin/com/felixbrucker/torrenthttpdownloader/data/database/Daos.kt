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

    @Query("SELECT * FROM download_files WHERE taskId = :taskId AND link = :link LIMIT 1")
    suspend fun getFileByLink(taskId: String, link: String): DownloadFileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: DownloadTaskEntity)

    @Update
    suspend fun updateTask(task: DownloadTaskEntity)

    @Query("UPDATE download_tasks SET state = :state, errorMessage = :errorMessage WHERE id = :id")
    suspend fun updateTaskState(id: String, state: TorrentState, errorMessage: String?)

    @Query("UPDATE download_tasks SET state = :state, providerId = :providerId WHERE id = :id")
    suspend fun updateTaskStateAndProviderId(id: String, state: TorrentState, providerId: String?)

    @Query("SELECT dt.id FROM download_tasks dt INNER JOIN provider_torrent_info pti ON dt.id = pti.taskId WHERE pti.state = :state")
    suspend fun getTaskIdsByProviderState(state: com.felixbrucker.torrenthttpdownloader.providers.ProviderTorrentState): List<String>

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

    @Query("UPDATE download_files SET state = :state, speed = :speed, downloadedBytes = :downloadedBytes WHERE taskId = :taskId AND link = :link")
    suspend fun updateFileProgress(
        taskId: String,
        link: String,
        state: LocalDownloadState,
        speed: Long,
        downloadedBytes: Long
    )

    @Query("UPDATE download_files SET state = :state, stateDescription = :stateDescription WHERE taskId = :taskId AND link = :link")
    suspend fun updateFileState(
        taskId: String,
        link: String,
        state: LocalDownloadState,
        stateDescription: String?
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

    @Query("UPDATE rss_feeds SET lastCheck = :lastCheck WHERE id = :id")
    suspend fun updateFeedLastCheck(id: String, lastCheck: Long)

    @Query("UPDATE rss_feeds SET name = :name, url = :url, destinationSubdirectory = :destinationSubdirectory, createSubfolderByName = :createSubfolderByName, notifyOnCompletion = :notifyOnCompletion, fileSelectionMode = :fileSelectionMode, autoDownload = :autoDownload WHERE id = :id")
    suspend fun updateFeedDetails(
        id: String,
        name: String,
        url: String,
        destinationSubdirectory: String?,
        createSubfolderByName: Boolean,
        notifyOnCompletion: Boolean,
        fileSelectionMode: com.felixbrucker.torrenthttpdownloader.models.FileSelectionMode,
        autoDownload: Boolean
    )

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

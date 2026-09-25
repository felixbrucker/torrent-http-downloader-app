package com.felixbrucker.torrenthttpdownloader.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.felixbrucker.torrenthttpdownloader.models.DownloadTask
import com.felixbrucker.torrenthttpdownloader.models.RssFeed
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {
    @Transaction
    @Query("SELECT * FROM download_tasks")
    fun getTasksFlow(): Flow<List<DownloadTaskWithDetails>>

    @Transaction
    @Query("SELECT * FROM download_tasks WHERE id = :id")
    suspend fun getTaskById(id: String): DownloadTaskWithDetails?

    @Query("SELECT * FROM provider_torrent_files WHERE providerInfoId = :providerInfoId")
    suspend fun getProviderFiles(providerInfoId: String): List<ProviderTorrentFileEntity>

    @Query("SELECT * FROM provider_torrent_links WHERE providerInfoId = :providerInfoId")
    suspend fun getProviderLinks(providerInfoId: String): List<ProviderTorrentLinkEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTaskEntity(task: DownloadTaskEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFiles(files: List<DownloadFileEntity>)

    @Query("DELETE FROM download_files WHERE taskId = :taskId")
    suspend fun deleteFilesForTask(taskId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProviderInfo(info: ProviderTorrentInfoEntity)

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

    @Transaction
    suspend fun upsertTask(task: DownloadTask) {
        val taskEntity = DownloadTaskEntity(
            id = task.id,
            providerId = task.providerId,
            name = task.name,
            torrent = TorrentDescriptorEntity(
                type = task.torrent.type.name,
                uri = task.torrent.uri
            ),
            state = task.state.name,
            errorMessage = task.errorMessage,
            destinationSubdirectory = task.destinationSubdirectory,
            createSubfolderByName = task.createSubfolderByName,
            notifyOnCompletion = task.notifyOnCompletion,
            fileSelectionMode = task.fileSelectionMode.name,
            onCompletionIntentUri = task.onCompletionIntentUri
        )
        insertTaskEntity(taskEntity)

        deleteFilesForTask(task.id)
        val fileEntities = task.files.map { file ->
            DownloadFileEntity(
                taskId = task.id,
                link = file.link,
                unrestrictedLink = file.unrestrictedLink,
                state = file.state.name,
                stateDescription = file.stateDescription,
                progress = file.progress,
                filePath = file.filePath,
                speed = file.speed,
                totalBytes = file.totalBytes,
                downloadedBytes = file.downloadedBytes
            )
        }
        if (fileEntities.isNotEmpty()) {
            insertFiles(fileEntities)
        }

        deleteProviderInfoForTask(task.id)
        val info = task.providerTorrentInfo
        if (info != null) {
            val infoEntity = ProviderTorrentInfoEntity(
                id = info.id,
                taskId = task.id,
                name = info.name,
                state = info.state.name,
                status = info.status,
                progress = info.progress,
                totalSizeInBytes = info.totalSizeInBytes,
                downloadedBytes = info.downloadedBytes,
                downloadSpeed = info.downloadSpeed,
                uploadSpeed = info.uploadSpeed,
                seeders = info.seeders,
                leechers = info.leechers,
                peers = info.peers,
                totalPeers = info.totalPeers
            )
            insertProviderInfo(infoEntity)

            deleteProviderFiles(info.id)
            val pFileEntities = info.files.map { file ->
                ProviderTorrentFileEntity(
                    providerInfoId = info.id,
                    fileId = file.id,
                    path = file.path,
                    size = file.size,
                    isSelected = file.isSelected,
                    progress = file.progress,
                    downloadedBytes = file.downloadedBytes,
                    priority = file.priority.name
                )
            }
            if (pFileEntities.isNotEmpty()) {
                insertProviderFiles(pFileEntities)
            }

            deleteProviderLinks(info.id)
            val pLinkEntities = info.links.map { link ->
                ProviderTorrentLinkEntity(
                    providerInfoId = info.id,
                    link = link
                )
            }
            if (pLinkEntities.isNotEmpty()) {
                insertProviderLinks(pLinkEntities)
            }
        }
    }

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
    suspend fun insertFeedEntity(feed: RssFeedEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<RssItemEntity>)

    @Query("DELETE FROM rss_items WHERE feedId = :feedId")
    suspend fun deleteItemsForFeed(feedId: String)

    @Transaction
    suspend fun upsertFeed(feed: RssFeed) {
        val feedEntity = RssFeedEntity(
            id = feed.id,
            name = feed.name,
            url = feed.url,
            destinationSubdirectory = feed.destinationSubdirectory,
            createSubfolderByName = feed.createSubfolderByName,
            notifyOnCompletion = feed.notifyOnCompletion,
            fileSelectionMode = feed.fileSelectionMode.name,
            autoDownload = feed.autoDownload,
            lastCheck = feed.lastCheck
        )
        insertFeedEntity(feedEntity)

        deleteItemsForFeed(feed.id)
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
            insertItems(itemEntities)
        }
    }

    @Query("DELETE FROM rss_feeds WHERE id = :id")
    suspend fun deleteFeedById(id: String)

    @Query("DELETE FROM rss_feeds")
    suspend fun deleteAllFeeds()
}

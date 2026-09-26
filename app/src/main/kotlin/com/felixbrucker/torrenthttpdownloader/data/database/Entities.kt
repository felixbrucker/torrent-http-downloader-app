package com.felixbrucker.torrenthttpdownloader.data.database

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.felixbrucker.torrenthttpdownloader.models.DownloadFile
import com.felixbrucker.torrenthttpdownloader.models.DownloadTask
import com.felixbrucker.torrenthttpdownloader.models.FileSelectionMode
import com.felixbrucker.torrenthttpdownloader.models.LocalDownloadState
import com.felixbrucker.torrenthttpdownloader.models.RssFeed
import com.felixbrucker.torrenthttpdownloader.models.RssItem
import com.felixbrucker.torrenthttpdownloader.models.TorrentDescriptor
import com.felixbrucker.torrenthttpdownloader.models.TorrentState
import com.felixbrucker.torrenthttpdownloader.models.TorrentType
import com.felixbrucker.torrenthttpdownloader.providers.FilePriority
import com.felixbrucker.torrenthttpdownloader.providers.ProviderTorrentFile
import com.felixbrucker.torrenthttpdownloader.providers.ProviderTorrentInfo
import com.felixbrucker.torrenthttpdownloader.providers.ProviderTorrentState

data class TorrentDescriptorEntity(
    val type: TorrentType,
    val uri: String
)

@Entity(tableName = "download_tasks")
data class DownloadTaskEntity(
    @PrimaryKey val id: String,
    val providerId: String?,
    val name: String,
    @Embedded(prefix = "torrent_") val torrent: TorrentDescriptorEntity,
    val state: TorrentState,
    val errorMessage: String?,
    val destinationSubdirectory: String?,
    val createSubfolderByName: Boolean,
    val notifyOnCompletion: Boolean,
    val fileSelectionMode: FileSelectionMode,
    val onCompletionIntentUri: String?
)

@Entity(
    tableName = "provider_torrent_info",
    foreignKeys = [
        ForeignKey(
            entity = DownloadTaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["taskId"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE
        )
    ],
    indices = [Index("taskId")]
)
data class ProviderTorrentInfoEntity(
    @PrimaryKey val id: String,
    val taskId: String,
    val name: String,
    val state: ProviderTorrentState,
    val status: String,
    val progress: Float,
    val totalSizeInBytes: Long,
    val downloadedBytes: Long,
    val downloadSpeed: Long,
    val uploadSpeed: Long,
    val seeders: Int?,
    val leechers: Int?,
    val peers: Int?,
    val totalPeers: Int?
)

@Entity(
    tableName = "provider_torrent_files",
    foreignKeys = [
        ForeignKey(
            entity = ProviderTorrentInfoEntity::class,
            parentColumns = ["id"],
            childColumns = ["providerInfoId"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE
        )
    ],
    indices = [Index("providerInfoId")]
)
data class ProviderTorrentFileEntity(
    @PrimaryKey(autoGenerate = true) val autoId: Long = 0,
    val providerInfoId: String,
    val fileId: Int,
    val path: String,
    val size: Long,
    val isSelected: Boolean,
    val progress: Float?,
    val downloadedBytes: Long?,
    val priority: FilePriority
)

@Entity(
    tableName = "provider_torrent_links",
    foreignKeys = [
        ForeignKey(
            entity = ProviderTorrentInfoEntity::class,
            parentColumns = ["id"],
            childColumns = ["providerInfoId"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE
        )
    ],
    indices = [Index("providerInfoId")]
)
data class ProviderTorrentLinkEntity(
    @PrimaryKey(autoGenerate = true) val autoId: Long = 0,
    val providerInfoId: String,
    val link: String
)

@Entity(
    tableName = "download_files",
    foreignKeys = [
        ForeignKey(
            entity = DownloadTaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["taskId"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE
        )
    ],
    indices = [Index("taskId")]
)
data class DownloadFileEntity(
    @PrimaryKey(autoGenerate = true) val autoId: Long = 0,
    val taskId: String,
    val link: String,
    val unrestrictedLink: String?,
    val state: LocalDownloadState,
    val stateDescription: String?,
    val filePath: String?,
    val speed: Long,
    val totalBytes: Long,
    val downloadedBytes: Long
)

data class ProviderTorrentInfoWithDetails(
    @Embedded val info: ProviderTorrentInfoEntity,
    @Relation(parentColumn = "id", entityColumn = "providerInfoId")
    val files: List<ProviderTorrentFileEntity>,
    @Relation(parentColumn = "id", entityColumn = "providerInfoId")
    val links: List<ProviderTorrentLinkEntity>
)

data class DownloadTaskWithDetails(
    @Embedded val task: DownloadTaskEntity,
    @Relation(
        entity = ProviderTorrentInfoEntity::class,
        parentColumn = "id",
        entityColumn = "taskId"
    )
    val providerInfo: ProviderTorrentInfoWithDetails?,
    @Relation(
        parentColumn = "id",
        entityColumn = "taskId"
    )
    val files: List<DownloadFileEntity>
) {
    fun toDomainModel(): DownloadTask {
        val providerTorrentInfoDomain = providerInfo?.let { details ->
            val info = details.info
            ProviderTorrentInfo(
                id = info.id,
                name = info.name,
                state = info.state,
                status = info.status,
                progress = info.progress,
                totalSizeInBytes = info.totalSizeInBytes,
                downloadedBytes = info.downloadedBytes,
                downloadSpeed = info.downloadSpeed,
                uploadSpeed = info.uploadSpeed,
                seeders = info.seeders,
                leechers = info.leechers,
                peers = info.peers,
                totalPeers = info.totalPeers,
                links = details.links.map { it.link },
                files = details.files.map { file ->
                    ProviderTorrentFile(
                        id = file.fileId,
                        path = file.path,
                        size = file.size,
                        isSelected = file.isSelected,
                        progress = file.progress,
                        downloadedBytes = file.downloadedBytes,
                        priority = file.priority
                    )
                }
            )
        }

        return DownloadTask(
            id = task.id,
            providerId = task.providerId,
            name = task.name,
            torrent = TorrentDescriptor(
                type = task.torrent.type,
                uri = task.torrent.uri
            ),
            state = task.state,
            providerTorrentInfo = providerTorrentInfoDomain,
            files = files.map { file ->
                val computedProgress = if (file.totalBytes > 0) ((file.downloadedBytes * 100L) / file.totalBytes).toInt() else 0
                DownloadFile(
                    link = file.link,
                    unrestrictedLink = file.unrestrictedLink,
                    state = file.state,
                    stateDescription = file.stateDescription,
                    progress = computedProgress,
                    filePath = file.filePath,
                    speed = file.speed,
                    totalBytes = file.totalBytes,
                    downloadedBytes = file.downloadedBytes
                )
            },
            errorMessage = task.errorMessage,
            destinationSubdirectory = task.destinationSubdirectory,
            createSubfolderByName = task.createSubfolderByName,
            notifyOnCompletion = task.notifyOnCompletion,
            fileSelectionMode = task.fileSelectionMode,
            onCompletionIntentUri = task.onCompletionIntentUri
        )
    }
}

@Entity(tableName = "rss_feeds")
data class RssFeedEntity(
    @PrimaryKey val id: String,
    val name: String,
    val url: String,
    val destinationSubdirectory: String?,
    val createSubfolderByName: Boolean,
    val notifyOnCompletion: Boolean,
    val fileSelectionMode: FileSelectionMode,
    val autoDownload: Boolean,
    val lastCheck: Long
)

@Entity(
    tableName = "rss_items",
    foreignKeys = [
        ForeignKey(
            entity = RssFeedEntity::class,
            parentColumns = ["id"],
            childColumns = ["feedId"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE
        )
    ],
    indices = [Index("feedId")]
)
data class RssItemEntity(
    @PrimaryKey val id: String,
    val feedId: String,
    val title: String,
    val link: String,
    val description: String?,
    val pubDate: Long?,
    val isRead: Boolean,
    val isDownloaded: Boolean
)

data class RssFeedWithItems(
    @Embedded val feed: RssFeedEntity,
    @Relation(parentColumn = "id", entityColumn = "feedId")
    val items: List<RssItemEntity>
) {
    fun toDomainModel(): RssFeed {
        return RssFeed(
            id = feed.id,
            name = feed.name,
            url = feed.url,
            destinationSubdirectory = feed.destinationSubdirectory,
            createSubfolderByName = feed.createSubfolderByName,
            notifyOnCompletion = feed.notifyOnCompletion,
            fileSelectionMode = feed.fileSelectionMode,
            autoDownload = feed.autoDownload,
            lastCheck = feed.lastCheck,
            items = items.map { item ->
                RssItem(
                    id = item.id,
                    title = item.title,
                    link = item.link,
                    description = item.description,
                    pubDate = item.pubDate,
                    isRead = item.isRead,
                    isDownloaded = item.isDownloaded
                )
            }
        )
    }
}

package com.felixbrucker.torrenthttpdownloader.core.database.entity

import androidx.room.Embedded
import androidx.room.Relation
import com.felixbrucker.torrenthttpdownloader.core.data.FileProgressInfo
import com.felixbrucker.torrenthttpdownloader.core.data.providers.ProviderTorrentInfo
import com.felixbrucker.torrenthttpdownloader.core.model.DownloadTask
import com.felixbrucker.torrenthttpdownloader.core.model.RssFeed
import com.felixbrucker.torrenthttpdownloader.extensions.asFile
import com.felixbrucker.torrenthttpdownloader.extensions.deleteIfExists

data class DownloadTaskWithFiles(
    @Embedded val task: DownloadTaskEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "taskId"
    )
    val files: List<DownloadFileEntity> = emptyList(), // should be sorted by filename ASC
) {
    fun deleteFiles() {
        for (filePath in files.mapNotNull { file -> file.filePath }) {
            filePath.asFile().deleteIfExists()
        }
    }

    fun toDownloadTask(
        providerTorrentInfo: ProviderTorrentInfo? = null,
        fileProgressMap: Map<String, FileProgressInfo>? = null,
    ): DownloadTask {
        val sortedFiles = files.sortedWith(compareBy(nullsLast()) { it.fileName })
        val combinedFiles = sortedFiles.map { fileEntity ->
            val progress = fileProgressMap?.get(fileEntity.link)
            fileEntity.toDownloadFile(progress)
        }
        return task.toDownloadTask(
            providerTorrentInfo = providerTorrentInfo,
            files = combinedFiles,
        )
    }
}

data class RssFeedWithItems(
    @Embedded val feed: RssFeedEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "feedId"
    )
    val items: List<RssItemEntity> = emptyList(), // should be sorted by pubdate DESC
) {
    fun toRssFeed(): RssFeed {
        val sortedItems = items.sortedWith(compareByDescending { it.pubDate ?: 0L })
        return feed.toRssFeed(items = sortedItems.map { it.toRssItem() })
    }
}

package com.felixbrucker.torrenthttpdownloader.core.database.entity

import androidx.room.Embedded
import com.felixbrucker.torrenthttpdownloader.core.data.FileProgressInfo
import com.felixbrucker.torrenthttpdownloader.core.data.providers.ProviderTorrentInfo
import com.felixbrucker.torrenthttpdownloader.core.model.DownloadTask
import com.felixbrucker.torrenthttpdownloader.core.model.RssFeed
import com.felixbrucker.torrenthttpdownloader.extensions.asFile
import com.felixbrucker.torrenthttpdownloader.extensions.deleteIfExists

data class DownloadTaskWithFiles(
    @Embedded val task: DownloadTaskEntity,
    val files: List<DownloadFileEntity> = emptyList(), // sorted by filename ASC in SQL
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
        val combinedFiles = files.map { fileEntity ->
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
    val items: List<RssItemEntity> = emptyList(), // sorted by pubdate DESC in SQL
) {
    fun toRssFeed(): RssFeed {
        return feed.toRssFeed(items = items.map { it.toRssItem() })
    }
}

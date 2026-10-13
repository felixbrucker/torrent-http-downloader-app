package com.felixbrucker.torrenthttpdownloader.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.felixbrucker.torrenthttpdownloader.core.model.FileSelectionMode
import com.felixbrucker.torrenthttpdownloader.core.model.RssFeed
import com.felixbrucker.torrenthttpdownloader.core.model.RssItem
import java.util.UUID

@Entity(
    tableName = "rss_feeds",
    indices = [
        Index(value = ["url"]),
        Index(value = ["name"]),
    ]
)
data class RssFeedEntity(
    @PrimaryKey val id: String,
    val name: String,
    val url: String,
    val destinationSubdirectory: String? = null,
    val createSubfolderByName: Boolean = true,
    val notifyOnCompletion: Boolean = true,
    val fileSelectionMode: FileSelectionMode = FileSelectionMode.ALL,
    val autoDownload: Boolean = false,
    val lastCheck: Long = 0,
) {
    fun updatedWith(feed: RssFeedEntity): RssFeedEntity {
        val isResetState = url != feed.url

        return copy(
            name = feed.name,
            url = feed.url,
            destinationSubdirectory = feed.destinationSubdirectory,
            createSubfolderByName = feed.createSubfolderByName,
            notifyOnCompletion = feed.notifyOnCompletion,
            fileSelectionMode = feed.fileSelectionMode,
            autoDownload = feed.autoDownload,
            lastCheck = if (isResetState) 0 else lastCheck,
        )
    }
    fun toRssFeed(items: List<RssItem> = emptyList()): RssFeed = RssFeed(
        id = id,
        name = name,
        url = url,
        destinationSubdirectory = destinationSubdirectory,
        createSubfolderByName = createSubfolderByName,
        notifyOnCompletion = notifyOnCompletion,
        fileSelectionMode = fileSelectionMode,
        autoDownload = autoDownload,
        lastCheck = lastCheck,
        items = items,
    )

    companion object {
        fun fromDomain(feed: RssFeed): RssFeedEntity = RssFeedEntity(
            id = feed.id.ifEmpty { UUID.randomUUID().toString() },
            name = feed.name,
            url = feed.url,
            destinationSubdirectory = feed.destinationSubdirectory,
            createSubfolderByName = feed.createSubfolderByName,
            notifyOnCompletion = feed.notifyOnCompletion,
            fileSelectionMode = feed.fileSelectionMode,
            autoDownload = feed.autoDownload,
            lastCheck = feed.lastCheck,
        )
    }
}

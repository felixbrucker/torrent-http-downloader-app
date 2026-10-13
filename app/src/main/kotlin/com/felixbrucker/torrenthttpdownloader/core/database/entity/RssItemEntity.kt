package com.felixbrucker.torrenthttpdownloader.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import com.felixbrucker.torrenthttpdownloader.core.model.RssItem

@Entity(
    tableName = "rss_items",
    primaryKeys = ["feedId", "id"],
    foreignKeys = [
        ForeignKey(
            entity = RssFeedEntity::class,
            parentColumns = ["id"],
            childColumns = ["feedId"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["feedId"]),
        Index(value = ["pubDate"]),
    ]
)
data class RssItemEntity(
    val feedId: String,
    val id: String,
    val title: String,
    val link: String,
    val description: String? = null,
    val pubDate: Long? = null,
    val isRead: Boolean = false,
    val isDownloaded: Boolean = false,
) {
    fun toRssItem(): RssItem = RssItem(
        id = id,
        title = title,
        link = link,
        description = description,
        pubDate = pubDate,
        isRead = isRead,
        isDownloaded = isDownloaded,
    )

    companion object {
        fun fromDomain(feedId: String, item: RssItem): RssItemEntity = RssItemEntity(
            feedId = feedId,
            id = item.id,
            title = item.title,
            link = item.link,
            description = item.description,
            pubDate = item.pubDate,
            isRead = item.isRead,
            isDownloaded = item.isDownloaded,
        )
    }
}

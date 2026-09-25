package com.felixbrucker.torrenthttpdownloader.data.database

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        DownloadTaskEntity::class,
        ProviderTorrentInfoEntity::class,
        ProviderTorrentFileEntity::class,
        ProviderTorrentLinkEntity::class,
        DownloadFileEntity::class,
        RssFeedEntity::class,
        RssItemEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun downloadDao(): DownloadDao
    abstract fun rssFeedDao(): RssFeedDao
}

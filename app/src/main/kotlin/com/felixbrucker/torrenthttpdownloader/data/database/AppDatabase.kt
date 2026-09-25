package com.felixbrucker.torrenthttpdownloader.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

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
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun downloadDao(): DownloadDao
    abstract fun rssFeedDao(): RssFeedDao
}

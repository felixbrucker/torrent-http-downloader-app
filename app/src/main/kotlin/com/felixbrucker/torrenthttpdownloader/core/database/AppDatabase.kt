package com.felixbrucker.torrenthttpdownloader.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.felixbrucker.torrenthttpdownloader.core.database.converter.Converters
import com.felixbrucker.torrenthttpdownloader.core.database.dao.DownloadDao
import com.felixbrucker.torrenthttpdownloader.core.database.dao.RssDao
import com.felixbrucker.torrenthttpdownloader.core.database.entity.DownloadFileEntity
import com.felixbrucker.torrenthttpdownloader.core.database.entity.DownloadTaskEntity
import com.felixbrucker.torrenthttpdownloader.core.database.entity.RssFeedEntity
import com.felixbrucker.torrenthttpdownloader.core.database.entity.RssItemEntity

@Database(
    entities = [
        DownloadTaskEntity::class,
        DownloadFileEntity::class,
        RssFeedEntity::class,
        RssItemEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun downloadDao(): DownloadDao
    abstract fun rssDao(): RssDao
}

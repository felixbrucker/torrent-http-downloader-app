package com.felixbrucker.torrenthttpdownloader.core.database

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.felixbrucker.torrenthttpdownloader.core.database.converter.Converters
import com.felixbrucker.torrenthttpdownloader.core.database.dao.DownloadDao
import com.felixbrucker.torrenthttpdownloader.core.database.dao.RssDao
import com.felixbrucker.torrenthttpdownloader.core.database.entity.DownloadFileEntity
import com.felixbrucker.torrenthttpdownloader.core.database.entity.DownloadFinalFileEntity
import com.felixbrucker.torrenthttpdownloader.core.database.entity.DownloadTaskEntity
import com.felixbrucker.torrenthttpdownloader.core.database.entity.RssFeedEntity
import com.felixbrucker.torrenthttpdownloader.core.database.entity.RssItemEntity

@Database(
    entities = [
        DownloadTaskEntity::class,
        DownloadFileEntity::class,
        DownloadFinalFileEntity::class,
        RssFeedEntity::class,
        RssItemEntity::class,
    ],
    version = 3,
    autoMigrations = [
        AutoMigration(from = 2, to = 3)
    ],
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun downloadDao(): DownloadDao
    abstract fun rssDao(): RssDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE download_tasks_new (id TEXT NOT NULL PRIMARY KEY, providerId TEXT, name TEXT NOT NULL, torrent_type TEXT NOT NULL, torrent_uri TEXT NOT NULL, state TEXT NOT NULL, errorMessage TEXT, destinationSubdirectory TEXT, createSubfolderByName INTEGER NOT NULL, notifyOnCompletion INTEGER NOT NULL, fileSelectionMode TEXT NOT NULL, onCompletionIntentUri TEXT, displayOrder REAL NOT NULL DEFAULT 0.0)")
                db.execSQL("INSERT INTO download_tasks_new SELECT id, providerId, name, torrent_type, torrent_uri, state, errorMessage, destinationSubdirectory, createSubfolderByName, notifyOnCompletion, fileSelectionMode, onCompletionIntentUri, CAST(displayOrder AS REAL) FROM download_tasks")
                db.execSQL("DROP TABLE download_tasks")
                db.execSQL("ALTER TABLE download_tasks_new RENAME TO download_tasks")
            }
        }
    }
}

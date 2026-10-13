package com.felixbrucker.torrenthttpdownloader.di

import android.content.Context
import androidx.room.Room
import com.felixbrucker.torrenthttpdownloader.core.database.AppDatabase
import com.felixbrucker.torrenthttpdownloader.core.database.dao.DownloadDao
import com.felixbrucker.torrenthttpdownloader.core.database.dao.RssDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "torrent_http_downloader.db",
        ).addMigrations(AppDatabase.MIGRATION_1_2).build()
    }

    @Provides
    @Singleton
    fun provideDownloadDao(database: AppDatabase): DownloadDao = database.downloadDao()

    @Provides
    @Singleton
    fun provideRssDao(database: AppDatabase): RssDao = database.rssDao()
}

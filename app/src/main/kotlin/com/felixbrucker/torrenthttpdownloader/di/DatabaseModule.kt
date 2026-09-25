package com.felixbrucker.torrenthttpdownloader.di

import android.content.Context
import androidx.room.Room
import com.felixbrucker.torrenthttpdownloader.data.database.AppDatabase
import com.felixbrucker.torrenthttpdownloader.data.database.DownloadDao
import com.felixbrucker.torrenthttpdownloader.data.database.RssFeedDao
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
            "torrenthttpdownloader.db"
        ).build()
    }

    @Provides
    @Singleton
    fun provideDownloadDao(database: AppDatabase): DownloadDao {
        return database.downloadDao()
    }

    @Provides
    @Singleton
    fun provideRssFeedDao(database: AppDatabase): RssFeedDao {
        return database.rssFeedDao()
    }
}

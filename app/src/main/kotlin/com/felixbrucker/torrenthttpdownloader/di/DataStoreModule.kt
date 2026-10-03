package com.felixbrucker.torrenthttpdownloader.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {

    @Provides
    @Singleton
    fun provideSettingsDataStore(@ApplicationContext context: Context): DataStore<Preferences> {
        return PreferenceDataStoreFactory.create(
            migrations = listOf(
                SharedPreferencesMigration(
                    context = context,
                    sharedPreferencesName = "settings",
                    keysToMigrate = setOf(
                        "provider",
                        "real_debrid_api_token",
                        "local_parallel_downloads",
                        "libtorrent_parallel_downloads",
                        "libtorrent_require_vpn_connection",
                        "rss_sync_enabled",
                        "rss_sync_interval_hours",
                        "default_sub_dir",
                        "default_create_subfolder",
                        "default_notify_on_completion",
                        "default_file_selection_mode",
                        "last_used_sub_dir",
                        "last_used_create_subfolder",
                        "last_used_notify_on_completion",
                        "last_used_file_selection_mode"
                    )
                )
            ),
            produceFile = { context.preferencesDataStoreFile("settings_preferences") }
        )
    }
}

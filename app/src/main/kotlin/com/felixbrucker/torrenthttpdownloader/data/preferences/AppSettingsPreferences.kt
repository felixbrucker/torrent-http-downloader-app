package com.felixbrucker.torrenthttpdownloader.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.felixbrucker.torrenthttpdownloader.models.FileSelectionMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

enum class TorrentProviderType {
    REAL_DEBRID,
    LIBTORRENT
}

val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "settings",
    produceMigrations = { context ->
        listOf(
            SharedPreferencesMigration(
                context = context,
                sharedPreferencesName = "settings",
                keysToMigrate = setOf(
                    "provider",
                    "real_debrid_api_token",
                    "local_parallel_downloads",
                    "libtorrent_parallel_downloads",
                    "libtorrent_require_vpn_connection",
                    "default_destination_subdirectory",
                    "rss_check_interval",
                    "notify_on_completion",
                    "file_selection_mode",
                    "auto_extract_archives",
                    "delete_archives_after_extraction"
                )
            )
        )
    }
)

data class AppSettingsPreferences(
    val selectedProvider: TorrentProviderType = TorrentProviderType.REAL_DEBRID,
    val realDebridApiToken: String = "",
    val localParallelDownloads: Int = 2,
    val libTorrentParallelDownloads: Int = 3,
    val libTorrentRequireVpnConnection: Boolean = false,
    val defaultDestinationSubdirectory: String = "",
    val rssCheckIntervalHours: Int = 1,
    val notifyOnCompletion: Boolean = true,
    val fileSelectionMode: FileSelectionMode = FileSelectionMode.ALL,
    val autoExtractArchives: Boolean = true,
    val deleteArchivesAfterExtraction: Boolean = false
)

@Singleton
class AppSettingsRepository @Inject constructor(
    @param:ApplicationContext context: Context
) {
    private val dataStore = context.settingsDataStore

    companion object {
        private val KEY_PROVIDER = stringPreferencesKey("provider")
        private val KEY_REAL_DEBRID_API_TOKEN = stringPreferencesKey("real_debrid_api_token")
        private val KEY_LOCAL_PARALLEL_DOWNLOADS = intPreferencesKey("local_parallel_downloads")
        private val KEY_LIBTORRENT_PARALLEL_DOWNLOADS = intPreferencesKey("libtorrent_parallel_downloads")
        private val KEY_LIBTORRENT_REQUIRE_VPN = booleanPreferencesKey("libtorrent_require_vpn_connection")
        private val KEY_DEFAULT_DESTINATION_SUBDIRECTORY = stringPreferencesKey("default_destination_subdirectory")
        private val KEY_RSS_CHECK_INTERVAL = intPreferencesKey("rss_check_interval")
        private val KEY_NOTIFY_ON_COMPLETION = booleanPreferencesKey("notify_on_completion")
        private val KEY_FILE_SELECTION_MODE = stringPreferencesKey("file_selection_mode")
        private val KEY_AUTO_EXTRACT_ARCHIVES = booleanPreferencesKey("auto_extract_archives")
        private val KEY_DELETE_ARCHIVES_AFTER_EXTRACTION = booleanPreferencesKey("delete_archives_after_extraction")
    }

    val preferencesFlow: Flow<AppSettingsPreferences> = dataStore.data.map { preferences ->
        val providerStr = preferences[KEY_PROVIDER]
        val providerType = try {
            if (providerStr != null) {
                if (providerStr.contains("libtorrent", ignoreCase = true)) {
                    TorrentProviderType.LIBTORRENT
                } else {
                    TorrentProviderType.REAL_DEBRID
                }
            } else TorrentProviderType.REAL_DEBRID
        } catch (_: Exception) {
            TorrentProviderType.REAL_DEBRID
        }

        val fileSelectionModeStr = preferences[KEY_FILE_SELECTION_MODE]
        val mode = try {
            if (fileSelectionModeStr != null) FileSelectionMode.valueOf(fileSelectionModeStr) else FileSelectionMode.ALL
        } catch (_: Exception) {
            FileSelectionMode.ALL
        }

        AppSettingsPreferences(
            selectedProvider = providerType,
            realDebridApiToken = preferences[KEY_REAL_DEBRID_API_TOKEN] ?: "",
            localParallelDownloads = preferences[KEY_LOCAL_PARALLEL_DOWNLOADS] ?: 2,
            libTorrentParallelDownloads = preferences[KEY_LIBTORRENT_PARALLEL_DOWNLOADS] ?: 3,
            libTorrentRequireVpnConnection = preferences[KEY_LIBTORRENT_REQUIRE_VPN] ?: false,
            defaultDestinationSubdirectory = preferences[KEY_DEFAULT_DESTINATION_SUBDIRECTORY] ?: "",
            rssCheckIntervalHours = preferences[KEY_RSS_CHECK_INTERVAL] ?: 1,
            notifyOnCompletion = preferences[KEY_NOTIFY_ON_COMPLETION] ?: true,
            fileSelectionMode = mode,
            autoExtractArchives = preferences[KEY_AUTO_EXTRACT_ARCHIVES] ?: true,
            deleteArchivesAfterExtraction = preferences[KEY_DELETE_ARCHIVES_AFTER_EXTRACTION] ?: false
        )
    }

    suspend fun setSelectedProvider(provider: TorrentProviderType) {
        dataStore.edit { it[KEY_PROVIDER] = provider.name }
    }

    suspend fun setRealDebridApiToken(token: String) {
        dataStore.edit { it[KEY_REAL_DEBRID_API_TOKEN] = token }
    }

    suspend fun setLocalParallelDownloads(limit: Int) {
        dataStore.edit { it[KEY_LOCAL_PARALLEL_DOWNLOADS] = limit }
    }

    suspend fun setLibTorrentParallelDownloads(limit: Int) {
        dataStore.edit { it[KEY_LIBTORRENT_PARALLEL_DOWNLOADS] = limit }
    }

    suspend fun setLibTorrentRequireVpnConnection(require: Boolean) {
        dataStore.edit { it[KEY_LIBTORRENT_REQUIRE_VPN] = require }
    }

    suspend fun setDefaultDestinationSubdirectory(subdirectory: String) {
        dataStore.edit { it[KEY_DEFAULT_DESTINATION_SUBDIRECTORY] = subdirectory }
    }

    suspend fun setRssCheckIntervalHours(hours: Int) {
        dataStore.edit { it[KEY_RSS_CHECK_INTERVAL] = hours }
    }

    suspend fun setNotifyOnCompletion(enabled: Boolean) {
        dataStore.edit { it[KEY_NOTIFY_ON_COMPLETION] = enabled }
    }

    suspend fun setFileSelectionMode(mode: FileSelectionMode) {
        dataStore.edit { it[KEY_FILE_SELECTION_MODE] = mode.name }
    }

    suspend fun setAutoExtractArchives(enabled: Boolean) {
        dataStore.edit { it[KEY_AUTO_EXTRACT_ARCHIVES] = enabled }
    }

    suspend fun setDeleteArchivesAfterExtraction(enabled: Boolean) {
        dataStore.edit { it[KEY_DELETE_ARCHIVES_AFTER_EXTRACTION] = enabled }
    }

    suspend fun clear() {
        dataStore.edit { it.clear() }
    }
}

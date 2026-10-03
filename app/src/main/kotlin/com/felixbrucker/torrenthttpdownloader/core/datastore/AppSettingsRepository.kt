package com.felixbrucker.torrenthttpdownloader.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.felixbrucker.torrenthttpdownloader.core.data.providers.LibTorrentProvider
import com.felixbrucker.torrenthttpdownloader.core.model.FileSelectionMode
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

data class AppSettings(
    val selectedProvider: String = LibTorrentProvider.NAME,
    val realDebridApiToken: String = "",
    val localParallelDownloads: Int = 2,
    val libTorrentParallelDownloads: Int = 3,
    val libTorrentRequireVpnConnection: Boolean = false,
    val rssSyncEnabled: Boolean = true,
    val rssSyncIntervalHours: Int = 3,
    val lastUsedSubDir: String? = null,
    val lastUsedCreateSubfolder: Boolean = true,
    val lastUsedNotifyOnCompletion: Boolean = false,
    val lastUsedFileSelectionMode: FileSelectionMode = FileSelectionMode.ALL,
)

@Singleton
class AppSettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    val settingsFlow: Flow<AppSettings> = dataStore.data.map { preferences ->
        AppSettings(
            selectedProvider = preferences[KEY_PROVIDER] ?: LibTorrentProvider.NAME,
            realDebridApiToken = preferences[KEY_REAL_DEBRID_API_TOKEN] ?: "",
            localParallelDownloads = preferences[KEY_LOCAL_PARALLEL_DOWNLOADS] ?: 2,
            libTorrentParallelDownloads = preferences[KEY_LIBTORRENT_PARALLEL_DOWNLOADS] ?: 3,
            libTorrentRequireVpnConnection = preferences[KEY_LIBTORRENT_REQUIRE_VPN] ?: false,
            rssSyncEnabled = preferences[KEY_RSS_SYNC_ENABLED] ?: true,
            rssSyncIntervalHours = preferences[KEY_RSS_SYNC_INTERVAL_HOURS] ?: 3,
            lastUsedSubDir = preferences[KEY_LAST_USED_SUB_DIR] ?: preferences[KEY_DEFAULT_SUB_DIR_LEGACY],
            lastUsedCreateSubfolder = preferences[KEY_LAST_USED_CREATE_SUBFOLDER]
                ?: preferences[KEY_DEFAULT_CREATE_SUBFOLDER_LEGACY]
                ?: true,
            lastUsedNotifyOnCompletion = preferences[KEY_LAST_USED_NOTIFY_ON_COMPLETION]
                ?: preferences[KEY_DEFAULT_NOTIFY_ON_COMPLETION_LEGACY]
                ?: false,
            lastUsedFileSelectionMode = (preferences[KEY_LAST_USED_FILE_SELECTION_MODE] ?: preferences[KEY_DEFAULT_FILE_SELECTION_MODE_LEGACY])
                ?.let { modeName ->
                    runCatching { FileSelectionMode.valueOf(modeName) }.getOrDefault(FileSelectionMode.ALL)
                } ?: FileSelectionMode.ALL
        )
    }

    suspend fun getSettings(): AppSettings = settingsFlow.first()

    suspend fun updateSelectedProvider(provider: String) {
        dataStore.edit { preferences ->
            preferences[KEY_PROVIDER] = provider
        }
    }

    suspend fun updateRealDebridApiToken(token: String) {
        dataStore.edit { preferences ->
            preferences[KEY_REAL_DEBRID_API_TOKEN] = token
        }
    }

    suspend fun updateLocalParallelDownloads(limit: Int) {
        dataStore.edit { preferences ->
            preferences[KEY_LOCAL_PARALLEL_DOWNLOADS] = limit
        }
    }

    suspend fun updateLibTorrentParallelDownloads(limit: Int) {
        dataStore.edit { preferences ->
            preferences[KEY_LIBTORRENT_PARALLEL_DOWNLOADS] = limit
        }
    }

    suspend fun updateLibTorrentRequireVpnConnection(requireVpn: Boolean) {
        dataStore.edit { preferences ->
            preferences[KEY_LIBTORRENT_REQUIRE_VPN] = requireVpn
        }
    }

    suspend fun updateRssSyncEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[KEY_RSS_SYNC_ENABLED] = enabled
        }
    }

    suspend fun updateRssSyncIntervalHours(hours: Int) {
        dataStore.edit { preferences ->
            preferences[KEY_RSS_SYNC_INTERVAL_HOURS] = hours
        }
    }

    suspend fun updateLastUsedAddTorrentOptions(
        lastUsedSubDir: String?,
        lastUsedCreateSubfolder: Boolean,
        lastUsedNotifyOnCompletion: Boolean,
        lastUsedFileSelectionMode: FileSelectionMode
    ) {
        dataStore.edit { preferences ->
            if (lastUsedSubDir != null) {
                preferences[KEY_LAST_USED_SUB_DIR] = lastUsedSubDir
            } else {
                preferences.remove(KEY_LAST_USED_SUB_DIR)
            }
            preferences[KEY_LAST_USED_CREATE_SUBFOLDER] = lastUsedCreateSubfolder
            preferences[KEY_LAST_USED_NOTIFY_ON_COMPLETION] = lastUsedNotifyOnCompletion
            preferences[KEY_LAST_USED_FILE_SELECTION_MODE] = lastUsedFileSelectionMode.name

            // Clean legacy keys if present
            preferences.remove(KEY_DEFAULT_SUB_DIR_LEGACY)
            preferences.remove(KEY_DEFAULT_CREATE_SUBFOLDER_LEGACY)
            preferences.remove(KEY_DEFAULT_NOTIFY_ON_COMPLETION_LEGACY)
            preferences.remove(KEY_DEFAULT_FILE_SELECTION_MODE_LEGACY)
        }
    }

    companion object {
        val KEY_PROVIDER = stringPreferencesKey("provider")
        val KEY_REAL_DEBRID_API_TOKEN = stringPreferencesKey("real_debrid_api_token")
        val KEY_LOCAL_PARALLEL_DOWNLOADS = intPreferencesKey("local_parallel_downloads")
        val KEY_LIBTORRENT_PARALLEL_DOWNLOADS = intPreferencesKey("libtorrent_parallel_downloads")
        val KEY_LIBTORRENT_REQUIRE_VPN = booleanPreferencesKey("libtorrent_require_vpn_connection")
        val KEY_RSS_SYNC_ENABLED = booleanPreferencesKey("rss_sync_enabled")
        val KEY_RSS_SYNC_INTERVAL_HOURS = intPreferencesKey("rss_sync_interval_hours")

        val KEY_LAST_USED_SUB_DIR = stringPreferencesKey("last_used_sub_dir")
        val KEY_LAST_USED_CREATE_SUBFOLDER = booleanPreferencesKey("last_used_create_subfolder")
        val KEY_LAST_USED_NOTIFY_ON_COMPLETION = booleanPreferencesKey("last_used_notify_on_completion")
        val KEY_LAST_USED_FILE_SELECTION_MODE = stringPreferencesKey("last_used_file_selection_mode")

        // Legacy keys for migration from SharedPreferences
        val KEY_DEFAULT_SUB_DIR_LEGACY = stringPreferencesKey("default_sub_dir")
        val KEY_DEFAULT_CREATE_SUBFOLDER_LEGACY = booleanPreferencesKey("default_create_subfolder")
        val KEY_DEFAULT_NOTIFY_ON_COMPLETION_LEGACY = booleanPreferencesKey("default_notify_on_completion")
        val KEY_DEFAULT_FILE_SELECTION_MODE_LEGACY = stringPreferencesKey("default_file_selection_mode")
    }
}

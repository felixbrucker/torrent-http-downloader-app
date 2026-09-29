package com.felixbrucker.torrenthttpdownloader.feature.settings

import android.app.backup.BackupManager
import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Named
import com.felixbrucker.torrenthttpdownloader.core.data.providers.LibTorrentProvider
import com.felixbrucker.torrenthttpdownloader.core.data.providers.RealDebridProvider

data class SettingsUiState(
    val selectedProvider: String = LibTorrentProvider.NAME,
    val realDebridApiToken: String = "",
    val localParallelDownloads: String = "2",
    val libTorrentParallelDownloads: String = "3",
    val libTorrentRequireVpnConnection: Boolean = false,
    val lastBackupTime: Long = 0L,
    val lastBackupSize: Long = 0L
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @param:Named("settings") private val sharedPreferences: SharedPreferences,
    @param:ApplicationContext private val context: Context
) : ViewModel() {

    val availableProviders: List<String> = listOf(
        LibTorrentProvider.NAME,
        RealDebridProvider.NAME
    )

    fun loadSettings(): SettingsUiState {
        return SettingsUiState(
            selectedProvider = sharedPreferences.getString("provider", availableProviders[0]) ?: availableProviders[0],
            realDebridApiToken = sharedPreferences.getString("real_debrid_api_token", "") ?: "",
            localParallelDownloads = sharedPreferences.getInt("local_parallel_downloads", 2).toString(),
            libTorrentParallelDownloads = sharedPreferences.getInt("libtorrent_parallel_downloads", 3).toString(),
            libTorrentRequireVpnConnection = sharedPreferences.getBoolean("libtorrent_require_vpn_connection", false),
            lastBackupTime = sharedPreferences.getLong("last_backup_time", 0L),
            lastBackupSize = sharedPreferences.getLong("last_backup_size", 0L)
        )
    }

    fun saveSettings(
        selectedProvider: String,
        realDebridApiToken: String,
        localParallelDownloads: String,
        libTorrentParallelDownloads: String,
        libTorrentRequireVpnConnection: Boolean
    ) {
        sharedPreferences.edit {
            putString("provider", selectedProvider)
            putString("real_debrid_api_token", realDebridApiToken.trim())
            putInt("local_parallel_downloads", localParallelDownloads.toIntOrNull() ?: 2)
            putInt("libtorrent_parallel_downloads", libTorrentParallelDownloads.toIntOrNull() ?: 3)
            putBoolean("libtorrent_require_vpn_connection", libTorrentRequireVpnConnection)
        }
        BackupManager.dataChanged(context.packageName)
    }
}

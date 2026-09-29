package com.felixbrucker.torrenthttpdownloader.feature.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Named
import com.felixbrucker.torrenthttpdownloader.core.data.providers.LibTorrentProvider
import com.felixbrucker.torrenthttpdownloader.core.data.providers.RealDebridProvider
import com.felixbrucker.torrenthttpdownloader.core.data.providers.ProviderFactory
import com.felixbrucker.torrenthttpdownloader.feature.rss.RssSyncLauncher

data class SettingsUiState(
    val selectedProvider: String = LibTorrentProvider.NAME,
    val realDebridApiToken: String = "",
    val localParallelDownloads: String = "2",
    val libTorrentParallelDownloads: String = "3",
    val libTorrentRequireVpnConnection: Boolean = false,
    val rssSyncEnabled: Boolean = true,
    val rssSyncIntervalHours: Int = 3
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @param:Named("settings") private val sharedPreferences: SharedPreferences,
    @param:ApplicationContext private val context: Context,
    private val rssSyncLauncher: RssSyncLauncher,
    private val providerFactory: ProviderFactory
) : ViewModel() {

    val availableProviders: List<String> = listOf(
        LibTorrentProvider.NAME,
        RealDebridProvider.NAME
    )

    private val _uiState = MutableStateFlow(loadInitialSettings())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private fun loadInitialSettings(): SettingsUiState {
        return SettingsUiState(
            selectedProvider = sharedPreferences.getString("provider", availableProviders[0]) ?: availableProviders[0],
            realDebridApiToken = sharedPreferences.getString("real_debrid_api_token", "") ?: "",
            localParallelDownloads = sharedPreferences.getInt("local_parallel_downloads", 2).toString(),
            libTorrentParallelDownloads = sharedPreferences.getInt("libtorrent_parallel_downloads", 3).toString(),
            libTorrentRequireVpnConnection = sharedPreferences.getBoolean("libtorrent_require_vpn_connection", false),
            rssSyncEnabled = sharedPreferences.getBoolean("rss_sync_enabled", true),
            rssSyncIntervalHours = sharedPreferences.getInt("rss_sync_interval_hours", 3)
        )
    }

    fun updateSelectedProvider(provider: String) {
        _uiState.update { it.copy(selectedProvider = provider) }
        saveSettings()
    }

    fun updateRealDebridApiToken(token: String) {
        _uiState.update { it.copy(realDebridApiToken = token) }
        saveSettings()
    }

    fun updateLocalParallelDownloads(limit: String) {
        val filtered = limit.filter { it.isDigit() }
        _uiState.update { it.copy(localParallelDownloads = filtered) }
        saveSettings()
    }

    fun updateLibTorrentParallelDownloads(limit: String) {
        val filtered = limit.filter { it.isDigit() }
        _uiState.update { it.copy(libTorrentParallelDownloads = filtered) }
        saveSettings()
    }

    fun updateLibTorrentRequireVpnConnection(requireVpn: Boolean) {
        _uiState.update { it.copy(libTorrentRequireVpnConnection = requireVpn) }
        saveSettings()
    }

    fun updateRssSyncEnabled(enabled: Boolean) {
        _uiState.update { it.copy(rssSyncEnabled = enabled) }
        saveSettings()
    }

    fun updateRssSyncIntervalHours(hours: Int) {
        _uiState.update { it.copy(rssSyncIntervalHours = hours) }
        saveSettings()
    }

    private fun saveSettings() {
        val currentState = _uiState.value
        sharedPreferences.edit {
            putString("provider", currentState.selectedProvider)
            putString("real_debrid_api_token", currentState.realDebridApiToken.trim())
            putInt("local_parallel_downloads", currentState.localParallelDownloads.toIntOrNull() ?: 2)
            putInt("libtorrent_parallel_downloads", currentState.libTorrentParallelDownloads.toIntOrNull() ?: 3)
            putBoolean("libtorrent_require_vpn_connection", currentState.libTorrentRequireVpnConnection)
            putBoolean("rss_sync_enabled", currentState.rssSyncEnabled)
            putInt("rss_sync_interval_hours", currentState.rssSyncIntervalHours)
        }
        rssSyncLauncher.updateRssSyncSchedule(
            enabled = currentState.rssSyncEnabled,
            intervalHours = currentState.rssSyncIntervalHours.toLong()
        )
        providerFactory.getProvider().reloadSettings()
    }
}

package com.felixbrucker.torrenthttpdownloader.feature.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.felixbrucker.torrenthttpdownloader.core.data.providers.LibTorrentProvider
import com.felixbrucker.torrenthttpdownloader.core.data.providers.ProviderFactory
import com.felixbrucker.torrenthttpdownloader.core.data.providers.RealDebridProvider
import com.felixbrucker.torrenthttpdownloader.feature.rss.RssSyncLauncher
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Named
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import timber.log.Timber

data class SettingsUiState(
    val selectedProvider: String = LibTorrentProvider.NAME,
    val realDebridApiToken: String = "",
    val localParallelDownloads: String = "2",
    val libTorrentParallelDownloads: String = "3",
    val libTorrentRequireVpnConnection: Boolean = false,
    val rssSyncEnabled: Boolean = true,
    val rssSyncIntervalHours: Int = 3
)

private data class ProviderSettings(
    val selectedProvider: String,
    val realDebridApiToken: String,
    val localParallelDownloads: String,
    val libTorrentParallelDownloads: String,
    val libTorrentRequireVpnConnection: Boolean
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

    private val selectedProviderFlow = _uiState.map { it.selectedProvider }.distinctUntilChanged()
    private val realDebridApiTokenFlow = _uiState.map { it.realDebridApiToken.trim() }.distinctUntilChanged()
    private val localParallelDownloadsFlow = _uiState.map { it.localParallelDownloads }.distinctUntilChanged()
    private val libTorrentParallelDownloadsFlow = _uiState.map { it.libTorrentParallelDownloads }.distinctUntilChanged()
    private val libTorrentRequireVpnConnectionFlow = _uiState.map { it.libTorrentRequireVpnConnection }.distinctUntilChanged()
    private val rssSyncEnabledFlow = _uiState.map { it.rssSyncEnabled }.distinctUntilChanged()
    private val rssSyncIntervalHoursFlow = _uiState.map { it.rssSyncIntervalHours }.distinctUntilChanged()

    init {
        selectedProviderFlow.drop(1).onEach { provider ->
            Timber.d("Settings saved: selectedProvider=%s", provider)
            sharedPreferences.edit { putString("provider", provider) }
        }.launchIn(viewModelScope)

        realDebridApiTokenFlow.drop(1).onEach { token ->
            Timber.d("Settings saved: realDebridApiToken=%s", if (token.isBlank()) "<empty>" else "***")
            sharedPreferences.edit { putString("real_debrid_api_token", token) }
        }.launchIn(viewModelScope)

        localParallelDownloadsFlow.drop(1).onEach { limit ->
            val value = limit.toIntOrNull() ?: 2
            Timber.d("Settings saved: localParallelDownloads=%d", value)
            sharedPreferences.edit { putInt("local_parallel_downloads", value) }
        }.launchIn(viewModelScope)

        libTorrentParallelDownloadsFlow.drop(1).onEach { limit ->
            val value = limit.toIntOrNull() ?: 3
            Timber.d("Settings saved: libTorrentParallelDownloads=%d", value)
            sharedPreferences.edit { putInt("libtorrent_parallel_downloads", value) }
        }.launchIn(viewModelScope)

        libTorrentRequireVpnConnectionFlow.drop(1).onEach { requireVpn ->
            Timber.d("Settings saved: libTorrentRequireVpnConnection=%b", requireVpn)
            sharedPreferences.edit { putBoolean("libtorrent_require_vpn_connection", requireVpn) }
        }.launchIn(viewModelScope)

        rssSyncEnabledFlow.drop(1).onEach { enabled ->
            Timber.d("Settings saved: rssSyncEnabled=%b", enabled)
            sharedPreferences.edit { putBoolean("rss_sync_enabled", enabled) }
        }.launchIn(viewModelScope)

        rssSyncIntervalHoursFlow.drop(1).onEach { interval ->
            Timber.d("Settings saved: rssSyncIntervalHours=%d", interval)
            sharedPreferences.edit { putInt("rss_sync_interval_hours", interval) }
        }.launchIn(viewModelScope)

        combine(
            rssSyncEnabledFlow,
            rssSyncIntervalHoursFlow
        ) { enabled, interval ->
            enabled to interval
        }
            .distinctUntilChanged()
            .drop(1)
            .onEach { (enabled, interval) ->
                rssSyncLauncher.updateRssSyncSchedule(
                    enabled = enabled,
                    intervalHours = interval.toLong()
                )
            }
            .launchIn(viewModelScope)

        combine(
            selectedProviderFlow,
            realDebridApiTokenFlow,
            localParallelDownloadsFlow,
            libTorrentParallelDownloadsFlow,
            libTorrentRequireVpnConnectionFlow
        ) { provider, token, localParallel, libTorrentParallel, requireVpn ->
            ProviderSettings(
                selectedProvider = provider,
                realDebridApiToken = token,
                localParallelDownloads = localParallel,
                libTorrentParallelDownloads = libTorrentParallel,
                libTorrentRequireVpnConnection = requireVpn
            )
        }
            .distinctUntilChanged()
            .drop(1)
            .onEach {
                providerFactory.getProvider().reloadSettings()
            }
            .launchIn(viewModelScope)
    }

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
    }

    fun updateRealDebridApiToken(token: String) {
        _uiState.update { it.copy(realDebridApiToken = token) }
    }

    fun updateLocalParallelDownloads(limit: String) {
        val filtered = limit.filter { it.isDigit() }
        _uiState.update { it.copy(localParallelDownloads = filtered) }
    }

    fun updateLibTorrentParallelDownloads(limit: String) {
        val filtered = limit.filter { it.isDigit() }
        _uiState.update { it.copy(libTorrentParallelDownloads = filtered) }
    }

    fun updateLibTorrentRequireVpnConnection(requireVpn: Boolean) {
        _uiState.update { it.copy(libTorrentRequireVpnConnection = requireVpn) }
    }

    fun updateRssSyncEnabled(enabled: Boolean) {
        _uiState.update { it.copy(rssSyncEnabled = enabled) }
    }

    fun updateRssSyncIntervalHours(hours: Int) {
        _uiState.update { it.copy(rssSyncIntervalHours = hours) }
    }
}

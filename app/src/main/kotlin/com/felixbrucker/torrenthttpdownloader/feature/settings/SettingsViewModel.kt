package com.felixbrucker.torrenthttpdownloader.feature.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.felixbrucker.torrenthttpdownloader.core.data.providers.LibTorrentProvider
import com.felixbrucker.torrenthttpdownloader.core.data.providers.ProviderFactory
import com.felixbrucker.torrenthttpdownloader.core.data.providers.RealDebridProvider
import com.felixbrucker.torrenthttpdownloader.core.datastore.AppSettingsRepository
import com.felixbrucker.torrenthttpdownloader.feature.rss.RssSyncLauncher
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
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
import kotlinx.coroutines.launch
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
    private val appSettingsRepository: AppSettingsRepository,
    @param:ApplicationContext private val context: Context,
    private val rssSyncLauncher: RssSyncLauncher,
    private val providerFactory: ProviderFactory
) : ViewModel() {

    val availableProviders: List<String> = listOf(
        LibTorrentProvider.NAME,
        RealDebridProvider.NAME
    )

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val selectedProviderFlow = _uiState.map { it.selectedProvider }.distinctUntilChanged()
    private val realDebridApiTokenFlow = _uiState.map { it.realDebridApiToken.trim() }.distinctUntilChanged()
    private val localParallelDownloadsFlow = _uiState.map { it.localParallelDownloads }.distinctUntilChanged()
    private val libTorrentParallelDownloadsFlow = _uiState.map { it.libTorrentParallelDownloads }.distinctUntilChanged()
    private val libTorrentRequireVpnConnectionFlow = _uiState.map { it.libTorrentRequireVpnConnection }.distinctUntilChanged()
    private val rssSyncEnabledFlow = _uiState.map { it.rssSyncEnabled }.distinctUntilChanged()
    private val rssSyncIntervalHoursFlow = _uiState.map { it.rssSyncIntervalHours }.distinctUntilChanged()

    init {
        appSettingsRepository.settingsFlow
            .onEach { settings ->
                _uiState.update {
                    it.copy(
                        selectedProvider = settings.selectedProvider,
                        realDebridApiToken = settings.realDebridApiToken,
                        localParallelDownloads = settings.localParallelDownloads.toString(),
                        libTorrentParallelDownloads = settings.libTorrentParallelDownloads.toString(),
                        libTorrentRequireVpnConnection = settings.libTorrentRequireVpnConnection,
                        rssSyncEnabled = settings.rssSyncEnabled,
                        rssSyncIntervalHours = settings.rssSyncIntervalHours
                    )
                }
            }
            .launchIn(viewModelScope)

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

    fun updateSelectedProvider(provider: String) {
        Timber.d("Settings saved: selectedProvider=%s", provider)
        _uiState.update { it.copy(selectedProvider = provider) }
        viewModelScope.launch {
            appSettingsRepository.updateSelectedProvider(provider)
        }
    }

    fun updateRealDebridApiToken(token: String) {
        Timber.d("Settings saved: realDebridApiToken=%s", if (token.isBlank()) "<empty>" else "***")
        _uiState.update { it.copy(realDebridApiToken = token) }
        viewModelScope.launch {
            appSettingsRepository.updateRealDebridApiToken(token)
        }
    }

    fun updateLocalParallelDownloads(limit: String) {
        val filtered = limit.filter { it.isDigit() }
        val value = filtered.toIntOrNull() ?: 2
        Timber.d("Settings saved: localParallelDownloads=%d", value)
        _uiState.update { it.copy(localParallelDownloads = filtered) }
        viewModelScope.launch {
            appSettingsRepository.updateLocalParallelDownloads(value)
        }
    }

    fun updateLibTorrentParallelDownloads(limit: String) {
        val filtered = limit.filter { it.isDigit() }
        val value = filtered.toIntOrNull() ?: 3
        Timber.d("Settings saved: libTorrentParallelDownloads=%d", value)
        _uiState.update { it.copy(libTorrentParallelDownloads = filtered) }
        viewModelScope.launch {
            appSettingsRepository.updateLibTorrentParallelDownloads(value)
        }
    }

    fun updateLibTorrentRequireVpnConnection(requireVpn: Boolean) {
        Timber.d("Settings saved: libTorrentRequireVpnConnection=%b", requireVpn)
        _uiState.update { it.copy(libTorrentRequireVpnConnection = requireVpn) }
        viewModelScope.launch {
            appSettingsRepository.updateLibTorrentRequireVpnConnection(requireVpn)
        }
    }

    fun updateRssSyncEnabled(enabled: Boolean) {
        Timber.d("Settings saved: rssSyncEnabled=%b", enabled)
        _uiState.update { it.copy(rssSyncEnabled = enabled) }
        viewModelScope.launch {
            appSettingsRepository.updateRssSyncEnabled(enabled)
        }
    }

    fun updateRssSyncIntervalHours(hours: Int) {
        Timber.d("Settings saved: rssSyncIntervalHours=%d", hours)
        _uiState.update { it.copy(rssSyncIntervalHours = hours) }
        viewModelScope.launch {
            appSettingsRepository.updateRssSyncIntervalHours(hours)
        }
    }
}

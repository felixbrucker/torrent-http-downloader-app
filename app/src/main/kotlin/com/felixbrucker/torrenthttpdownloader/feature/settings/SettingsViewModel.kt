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
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
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

    init {
        appSettingsRepository.settingsFlow
            .onEach { settings ->
                _uiState.value = SettingsUiState(
                    selectedProvider = settings.selectedProvider,
                    realDebridApiToken = settings.realDebridApiToken,
                    localParallelDownloads = settings.localParallelDownloads.toString(),
                    libTorrentParallelDownloads = settings.libTorrentParallelDownloads.toString(),
                    libTorrentRequireVpnConnection = settings.libTorrentRequireVpnConnection,
                    rssSyncEnabled = settings.rssSyncEnabled,
                    rssSyncIntervalHours = settings.rssSyncIntervalHours
                )
            }
            .launchIn(viewModelScope)
    }

    fun updateSelectedProvider(provider: String) {
        Timber.d("Settings saved: selectedProvider=%s", provider)
        viewModelScope.launch {
            appSettingsRepository.updateSelectedProvider(provider)
        }
    }

    fun updateRealDebridApiToken(token: String) {
        Timber.d("Settings saved: realDebridApiToken=%s", if (token.isBlank()) "<empty>" else "***")
        viewModelScope.launch {
            appSettingsRepository.updateRealDebridApiToken(token)
        }
    }

    fun updateLocalParallelDownloads(limit: String) {
        val filtered = limit.filter { it.isDigit() }
        val value = filtered.toIntOrNull() ?: 2
        Timber.d("Settings saved: localParallelDownloads=%d", value)
        viewModelScope.launch {
            appSettingsRepository.updateLocalParallelDownloads(value)
        }
    }

    fun updateLibTorrentParallelDownloads(limit: String) {
        val filtered = limit.filter { it.isDigit() }
        val value = filtered.toIntOrNull() ?: 3
        Timber.d("Settings saved: libTorrentParallelDownloads=%d", value)
        viewModelScope.launch {
            appSettingsRepository.updateLibTorrentParallelDownloads(value)
        }
    }

    fun updateLibTorrentRequireVpnConnection(requireVpn: Boolean) {
        Timber.d("Settings saved: libTorrentRequireVpnConnection=%b", requireVpn)
        viewModelScope.launch {
            appSettingsRepository.updateLibTorrentRequireVpnConnection(requireVpn)
        }
    }

    fun updateRssSyncEnabled(enabled: Boolean) {
        Timber.d("Settings saved: rssSyncEnabled=%b", enabled)
        viewModelScope.launch {
            appSettingsRepository.updateRssSyncEnabled(enabled)
        }
    }

    fun updateRssSyncIntervalHours(hours: Int) {
        Timber.d("Settings saved: rssSyncIntervalHours=%d", hours)
        viewModelScope.launch {
            appSettingsRepository.updateRssSyncIntervalHours(hours)
        }
    }
}

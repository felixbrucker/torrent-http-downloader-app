package com.felixbrucker.torrenthttpdownloader.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.felixbrucker.torrenthttpdownloader.data.preferences.AppSettingsPreferences
import com.felixbrucker.torrenthttpdownloader.data.preferences.AppSettingsRepository
import com.felixbrucker.torrenthttpdownloader.data.preferences.TorrentProviderType
import com.felixbrucker.torrenthttpdownloader.models.FileSelectionMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val appSettingsRepository: AppSettingsRepository
) : ViewModel() {

    val preferences: StateFlow<AppSettingsPreferences> = appSettingsRepository.preferencesFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = AppSettingsPreferences()
        )

    fun setSelectedProvider(provider: TorrentProviderType) {
        viewModelScope.launch {
            appSettingsRepository.setSelectedProvider(provider)
        }
    }

    fun setRealDebridApiToken(token: String) {
        viewModelScope.launch {
            appSettingsRepository.setRealDebridApiToken(token)
        }
    }

    fun setLocalParallelDownloads(limit: Int) {
        viewModelScope.launch {
            appSettingsRepository.setLocalParallelDownloads(limit)
        }
    }

    fun setLibTorrentParallelDownloads(limit: Int) {
        viewModelScope.launch {
            appSettingsRepository.setLibTorrentParallelDownloads(limit)
        }
    }

    fun setLibTorrentRequireVpnConnection(require: Boolean) {
        viewModelScope.launch {
            appSettingsRepository.setLibTorrentRequireVpnConnection(require)
        }
    }

    fun setDefaultDestinationSubdirectory(subdirectory: String) {
        viewModelScope.launch {
            appSettingsRepository.setDefaultDestinationSubdirectory(subdirectory)
        }
    }

    fun setRssCheckIntervalHours(hours: Int) {
        viewModelScope.launch {
            appSettingsRepository.setRssCheckIntervalHours(hours)
        }
    }

    fun setNotifyOnCompletion(enabled: Boolean) {
        viewModelScope.launch {
            appSettingsRepository.setNotifyOnCompletion(enabled)
        }
    }

    fun setFileSelectionMode(mode: FileSelectionMode) {
        viewModelScope.launch {
            appSettingsRepository.setFileSelectionMode(mode)
        }
    }

    fun setAutoExtractArchives(enabled: Boolean) {
        viewModelScope.launch {
            appSettingsRepository.setAutoExtractArchives(enabled)
        }
    }

    fun setDeleteArchivesAfterExtraction(enabled: Boolean) {
        viewModelScope.launch {
            appSettingsRepository.setDeleteArchivesAfterExtraction(enabled)
        }
    }
}

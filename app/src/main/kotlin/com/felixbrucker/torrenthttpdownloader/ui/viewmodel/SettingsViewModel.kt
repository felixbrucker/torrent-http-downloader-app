package com.felixbrucker.torrenthttpdownloader.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.felixbrucker.torrenthttpdownloader.data.preferences.AppSettingsPreferences
import com.felixbrucker.torrenthttpdownloader.data.preferences.AppSettingsRepository
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

    val settings: StateFlow<AppSettingsPreferences> = appSettingsRepository.preferencesFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AppSettingsPreferences()
    )

    fun setSelectedProvider(provider: String) {
        viewModelScope.launch {
            appSettingsRepository.setSelectedProvider(provider)
        }
    }

    fun setRealDebridApiKey(apiKey: String) {
        viewModelScope.launch {
            appSettingsRepository.setRealDebridApiKey(apiKey)
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

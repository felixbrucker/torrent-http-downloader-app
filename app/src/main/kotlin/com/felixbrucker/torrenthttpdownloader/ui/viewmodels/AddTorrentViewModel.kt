package com.felixbrucker.torrenthttpdownloader.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.felixbrucker.torrenthttpdownloader.data.preferences.AppSettingsPreferences
import com.felixbrucker.torrenthttpdownloader.data.preferences.AppSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class AddTorrentViewModel @Inject constructor(
    private val appSettingsRepository: AppSettingsRepository
) : ViewModel() {

    val preferences: StateFlow<AppSettingsPreferences> = appSettingsRepository.preferencesFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = AppSettingsPreferences()
        )
}

package com.felixbrucker.torrenthttpdownloader.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.felixbrucker.torrenthttpdownloader.data.logging.LogEntry
import com.felixbrucker.torrenthttpdownloader.data.logging.LogRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LogViewerViewModel @Inject constructor(
    private val logRepository: LogRepository
) : ViewModel() {

    val logs: StateFlow<List<LogEntry>> = logRepository.logsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun clearLogs() {
        viewModelScope.launch {
            logRepository.clearLogs()
        }
    }
}

package com.felixbrucker.torrenthttpdownloader.feature.logs

import androidx.lifecycle.ViewModel
import com.felixbrucker.torrenthttpdownloader.core.logging.LogEntry
import com.felixbrucker.torrenthttpdownloader.core.logging.LogRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class LogViewerViewModel @Inject constructor(
    private val logRepository: LogRepository
) : ViewModel() {

    val allLogs: StateFlow<List<LogEntry>> = logRepository.logsFlow

    fun clearLogs() {
        logRepository.clearLogs()
    }
}

package com.felixbrucker.torrenthttpdownloader.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.felixbrucker.torrenthttpdownloader.data.repository.DownloadRepository
import com.felixbrucker.torrenthttpdownloader.models.DownloadTask
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val downloadRepository: DownloadRepository
) : ViewModel() {

    val tasks: StateFlow<List<DownloadTask>> = downloadRepository.tasksFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
}

package com.felixbrucker.torrenthttpdownloader

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.felixbrucker.torrenthttpdownloader.core.data.DownloadRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch
import timber.log.Timber

@HiltViewModel
class MainViewModel @Inject constructor(
    private val downloadRepository: DownloadRepository
) : ViewModel() {

    fun checkAndStartDownloadService(context: Context) {
        viewModelScope.launch {
            if (downloadRepository.hasTasksWhichNeedProcessing()) {
                Timber.i("Starting DownloadService for tasks needing processing")
                context.startService(Intent(context, DownloadService::class.java))
            }
        }
    }
}

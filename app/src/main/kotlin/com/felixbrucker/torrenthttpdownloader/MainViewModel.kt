package com.felixbrucker.torrenthttpdownloader

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import com.felixbrucker.torrenthttpdownloader.core.data.DownloadTracker
import com.felixbrucker.torrenthttpdownloader.feature.rss.RssSyncLauncher

@HiltViewModel
class MainViewModel @Inject constructor(
    private val downloadTracker: DownloadTracker,
    private val rssSyncLauncher: RssSyncLauncher
) : ViewModel() {

    fun checkAndStartDownloadService(context: Context) {
        if (downloadTracker.hasTasksWhichNeedProcessing()) {
            context.startService(Intent(context, DownloadService::class.java))
        }
    }

    fun ensureRssSyncIsScheduled() {
        rssSyncLauncher.ensureRssSyncIsScheduled()
    }
}

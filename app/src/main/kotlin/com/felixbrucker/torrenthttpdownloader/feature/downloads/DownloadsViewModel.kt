package com.felixbrucker.torrenthttpdownloader.feature.downloads

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import com.felixbrucker.torrenthttpdownloader.DownloadService
import com.felixbrucker.torrenthttpdownloader.core.data.DownloadTracker
import com.felixbrucker.torrenthttpdownloader.core.data.providers.ProviderFactory
import com.felixbrucker.torrenthttpdownloader.core.data.providers.TorrentProvider
import com.felixbrucker.torrenthttpdownloader.core.model.DownloadTask

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val downloadTracker: DownloadTracker,
    private val providerFactory: ProviderFactory
) : ViewModel() {

    val tasks: StateFlow<List<DownloadTask>> = downloadTracker.tasks
    val totalUnreadRssCount: Flow<Int> = downloadTracker.totalUnreadRssCount

    fun getProvider(): TorrentProvider? {
        return providerFactory.getProvider()
    }

    fun moveTask(fromIndex: Int, toIndex: Int) {
        downloadTracker.moveTask(fromIndex, toIndex)
    }

    fun removeTask(
        context: Context,
        taskId: String,
        deleteFiles: Boolean,
        deleteTorrentFile: Boolean
    ) {
        val intent = Intent(context, DownloadService::class.java).apply {
            action = DownloadService.ACTION_REMOVE_TASK
            putExtra(DownloadService.EXTRA_TASK_ID, taskId)
            putExtra(DownloadService.EXTRA_DELETE_FILES, deleteFiles)
            putExtra(DownloadService.EXTRA_DELETE_TORRENT_FILE, deleteTorrentFile)
        }
        context.startService(intent)
    }

    fun resumeAll(context: Context) {
        context.startService(Intent(context, DownloadService::class.java).apply {
            action = DownloadService.ACTION_RESUME_ALL_LOCAL_DOWNLOADS
        })
        context.startService(Intent(context, DownloadService::class.java).apply {
            action = DownloadService.ACTION_RESUME_ALL_ON_PROVIDER
        })
    }

    fun pauseAll(context: Context) {
        context.startService(Intent(context, DownloadService::class.java).apply {
            action = DownloadService.ACTION_PAUSE_ALL_LOCAL_DOWNLOADS
        })
        context.startService(Intent(context, DownloadService::class.java).apply {
            action = DownloadService.ACTION_PAUSE_ALL_ON_PROVIDER
        })
    }
}

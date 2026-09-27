package com.felixbrucker.torrenthttpdownloader.feature.downloads

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import com.felixbrucker.torrenthttpdownloader.core.data.DownloadTracker
import com.felixbrucker.torrenthttpdownloader.core.data.providers.ProviderFactory
import com.felixbrucker.torrenthttpdownloader.core.data.providers.TorrentProvider
import com.felixbrucker.torrenthttpdownloader.core.model.DownloadTask

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val downloadTracker: DownloadTracker,
    private val providerFactory: ProviderFactory,
    private val serviceLauncher: DownloadServiceLauncher
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
        taskId: String,
        deleteFiles: Boolean,
        deleteTorrentFile: Boolean
    ) {
        serviceLauncher.removeTask(taskId, deleteFiles, deleteTorrentFile)
    }

    fun resumeAll() {
        serviceLauncher.resumeAll()
    }

    fun pauseAll() {
        serviceLauncher.pauseAll()
    }
}

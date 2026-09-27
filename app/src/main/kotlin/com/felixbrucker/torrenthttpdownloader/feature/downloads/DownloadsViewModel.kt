package com.felixbrucker.torrenthttpdownloader.feature.downloads

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import com.felixbrucker.torrenthttpdownloader.core.data.DownloadTracker
import com.felixbrucker.torrenthttpdownloader.core.data.providers.FilePriority
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

    fun removeTask(taskId: String, deleteFiles: Boolean, deleteTorrentFile: Boolean) {
        serviceLauncher.removeTask(taskId, deleteFiles, deleteTorrentFile)
    }

    fun resumeAllLocalDownloads() {
        serviceLauncher.resumeAllLocalDownloads()
    }

    fun resumeAllOnProvider() {
        serviceLauncher.resumeAllOnProvider()
    }

    fun pauseAllLocalDownloads() {
        serviceLauncher.pauseAllLocalDownloads()
    }

    fun pauseAllOnProvider() {
        serviceLauncher.pauseAllOnProvider()
    }

    fun toggleAllProviderFileSelection(taskId: String, selectAll: Boolean) {
        serviceLauncher.toggleAllProviderFileSelection(taskId, selectAll)
    }

    fun confirmFileSelection(taskId: String) {
        serviceLauncher.confirmFileSelection(taskId)
    }

    fun pauseTaskOnProvider(taskId: String) {
        serviceLauncher.pauseTaskOnProvider(taskId)
    }

    fun resumeTaskOnProvider(taskId: String) {
        serviceLauncher.resumeTaskOnProvider(taskId)
    }

    fun pauseTaskLocalDownloads(taskId: String) {
        serviceLauncher.pauseTaskLocalDownloads(taskId)
    }

    fun resumeTaskLocalDownloads(taskId: String) {
        serviceLauncher.resumeTaskLocalDownloads(taskId)
    }

    fun restartTask(taskId: String) {
        serviceLauncher.restartTask(taskId)
    }

    fun resumeLocalFileDownload(taskId: String, fileLink: String) {
        serviceLauncher.resumeLocalFileDownload(taskId, fileLink)
    }

    fun pauseLocalFileDownload(taskId: String, fileLink: String) {
        serviceLauncher.pauseLocalFileDownload(taskId, fileLink)
    }

    fun toggleProviderFileSelection(taskId: String, fileId: Int) {
        serviceLauncher.toggleProviderFileSelection(taskId, fileId)
    }

    fun setProviderFilePriority(taskId: String, fileId: Int, priority: FilePriority) {
        serviceLauncher.setProviderFilePriority(taskId, fileId, priority)
    }
}

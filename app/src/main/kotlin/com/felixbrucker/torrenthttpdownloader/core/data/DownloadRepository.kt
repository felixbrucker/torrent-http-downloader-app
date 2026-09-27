package com.felixbrucker.torrenthttpdownloader.core.data

import com.felixbrucker.torrenthttpdownloader.core.data.providers.FilePriority
import com.felixbrucker.torrenthttpdownloader.core.data.providers.ProviderTorrentInfo
import com.felixbrucker.torrenthttpdownloader.core.data.providers.ProviderTorrentState
import com.felixbrucker.torrenthttpdownloader.core.model.DownloadFile
import com.felixbrucker.torrenthttpdownloader.core.model.LocalDownloadState
import com.felixbrucker.torrenthttpdownloader.core.model.TorrentState
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadRepository @Inject constructor(
    private val downloadTracker: DownloadTracker,
) {
    fun updateFileDownloadingState(
        taskId: String,
        fileLink: String,
        totalBytes: Long,
        downloadedBytes: Long,
    ) {
        downloadTracker.updateTaskFile(taskId, fileLink) { file ->
            file.copy(
                state = LocalDownloadState.DOWNLOADING,
                totalBytes = totalBytes,
                downloadedBytes = downloadedBytes,
            )
        }
    }

    fun updateFileProgress(
        taskId: String,
        fileLink: String,
        progress: Int,
        downloadedBytes: Long,
        speed: Long,
        lastTimestamp: Long,
        lastBytes: Long,
    ) {
        downloadTracker.updateTaskFile(taskId, fileLink) { file ->
            file.copy(
                progress = progress,
                downloadedBytes = downloadedBytes,
                speed = speed,
                lastTimestamp = lastTimestamp,
                lastBytes = lastBytes,
            )
        }
    }

    fun updateFileState(
        taskId: String,
        fileLink: String,
        state: LocalDownloadState,
        stateDescription: String? = null,
    ) {
        downloadTracker.updateTaskFile(taskId, fileLink) { file ->
            file.copy(state = state, stateDescription = stateDescription, speed = 0)
        }
    }

    fun updateProviderTorrentState(taskId: String, state: ProviderTorrentState) {
        downloadTracker.updateTask(taskId) { task ->
            task.copy(
                providerTorrentInfo = task.providerTorrentInfo?.copy(
                    state = state,
                )
            )
        }
    }

    fun setProviderFilePriority(taskId: String, fileId: Int, priority: FilePriority) {
        downloadTracker.updateTask(taskId) { currentTask ->
            val updatedFiles = currentTask.providerTorrentInfo?.files?.map { file ->
                if (file.id == fileId) {
                    file.copy(priority = priority, isSelected = priority != FilePriority.IGNORE)
                } else {
                    file
                }
            } ?: emptyList()
            currentTask.copy(
                providerTorrentInfo = currentTask.providerTorrentInfo?.copy(files = updatedFiles)
            )
        }
    }

    fun toggleProviderFileSelectionLocally(taskId: String, fileId: Int) {
        downloadTracker.updateTask(taskId) { currentTask ->
            val updatedFiles = currentTask.providerTorrentInfo?.files?.map { file ->
                if (file.id == fileId) {
                    file.copy(isSelected = !file.isSelected)
                } else {
                    file
                }
            } ?: emptyList()
            currentTask.copy(
                providerTorrentInfo = currentTask.providerTorrentInfo?.copy(files = updatedFiles)
            )
        }
    }

    fun toggleAllProviderFilesSelectionLocally(taskId: String, selectAll: Boolean) {
        downloadTracker.updateTask(taskId) { currentTask ->
            val updatedFiles = currentTask.providerTorrentInfo?.files?.map { file ->
                file.copy(isSelected = selectAll)
            } ?: emptyList()
            currentTask.copy(
                providerTorrentInfo = currentTask.providerTorrentInfo?.copy(files = updatedFiles)
            )
        }
    }

    fun updateTaskState(taskId: String, state: TorrentState) {
        downloadTracker.updateTask(taskId) { task ->
            task.copy(state = state)
        }
    }

    fun updateTaskError(taskId: String, errorMessage: String?) {
        downloadTracker.updateTask(taskId) { task ->
            task.copy(
                state = TorrentState.ERROR,
                errorMessage = errorMessage,
            )
        }
    }

    fun updateTaskFileInfo(
        taskId: String,
        fileLink: String,
        totalBytes: Long,
        filePath: String,
        unrestrictedLink: String,
    ) {
        downloadTracker.updateTaskFile(taskId, fileLink) { file ->
            file.copy(
                totalBytes = totalBytes,
                filePath = filePath,
                unrestrictedLink = unrestrictedLink,
            )
        }
    }

    fun updateTaskProviderIdAndState(taskId: String, providerId: String, state: TorrentState) {
        downloadTracker.updateTask(taskId) { task ->
            task.copy(
                providerId = providerId,
                state = state,
            )
        }
    }

    fun setProviderTorrentInfoWaitingForFileSelection(taskId: String) {
        downloadTracker.updateTask(taskId) { task ->
            task.copy(
                providerTorrentInfo = task.providerTorrentInfo?.copy(
                    state = ProviderTorrentState.WAITING_FOR_FILE_SELECTION,
                    status = "waiting_for_file_selection",
                    downloadSpeed = 0,
                    uploadSpeed = 0,
                )
            )
        }
    }

    fun updateTaskFilesAndState(taskId: String, files: List<DownloadFile>, state: TorrentState) {
        downloadTracker.updateTask(taskId) { task ->
            task.copy(
                files = files,
                state = state,
            )
        }
    }

    fun markAllTaskFilesCompletedLocally(taskId: String) {
        downloadTracker.updateTaskFiles(taskId) { file ->
            file.copy(
                unrestrictedLink = null,
                state = LocalDownloadState.COMPLETED,
                progress = 100,
                downloadedBytes = file.totalBytes,
            )
        }
    }

    fun resetTaskForRetry(taskId: String) {
        downloadTracker.updateTask(taskId) { task ->
            task.copy(
                providerId = null,
                state = TorrentState.ADDING_TO_PROVIDER,
                providerTorrentInfo = null,
                files = emptyList(),
                errorMessage = null,
            )
        }
    }

    fun updateTaskWithTorrentInfo(taskId: String, torrentInfo: ProviderTorrentInfo) {
        downloadTracker.updateTask(taskId) { task ->
            task.copy(
                name = if (task.name == task.torrent.uri) torrentInfo.name else task.name,
                providerTorrentInfo = torrentInfo,
            )
        }
    }
}

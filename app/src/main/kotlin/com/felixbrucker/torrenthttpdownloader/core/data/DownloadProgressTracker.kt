package com.felixbrucker.torrenthttpdownloader.core.data

import com.felixbrucker.torrenthttpdownloader.core.data.providers.FilePriority
import com.felixbrucker.torrenthttpdownloader.core.data.providers.ProviderTorrentInfo
import com.felixbrucker.torrenthttpdownloader.core.data.providers.ProviderTorrentState
import com.felixbrucker.torrenthttpdownloader.core.model.DownloadFileEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class FileProgressInfo(
    val progress: Int = 0,
    val speed: Long = 0,
    val downloadedBytes: Long = 0,
    val lastBytes: Long = 0,
    val lastTimestamp: Long = System.currentTimeMillis(),
)

data class TaskProgressInfo(
    val providerTorrentInfo: ProviderTorrentInfo? = null,
    val fileProgressMap: Map<String, FileProgressInfo> = emptyMap(),
)

@Singleton
class DownloadProgressTracker @Inject constructor() {
    private val _progressInfo = MutableStateFlow<Map<String, TaskProgressInfo>>(emptyMap())
    val progressInfo = _progressInfo.asStateFlow()

    fun updateFileProgress(
        taskId: String,
        fileLink: String,
        progress: Int,
        downloadedBytes: Long,
        speed: Long,
        lastTimestamp: Long,
        lastBytes: Long,
    ) {
        _progressInfo.update { currentMap ->
            val taskInfo = currentMap[taskId] ?: TaskProgressInfo()
            val fileInfo = taskInfo.fileProgressMap[fileLink] ?: FileProgressInfo()
            val updatedFileInfo = fileInfo.copy(
                progress = progress,
                downloadedBytes = downloadedBytes,
                speed = speed,
                lastTimestamp = lastTimestamp,
                lastBytes = lastBytes,
            )
            val updatedFileMap = taskInfo.fileProgressMap + (fileLink to updatedFileInfo)
            currentMap + (taskId to taskInfo.copy(fileProgressMap = updatedFileMap))
        }
    }

    fun updateFileDownloadedBytes(
        taskId: String,
        fileLink: String,
        downloadedBytes: Long,
    ) {
        _progressInfo.update { currentMap ->
            val taskInfo = currentMap[taskId] ?: TaskProgressInfo()
            val fileInfo = taskInfo.fileProgressMap[fileLink] ?: FileProgressInfo()
            val updatedFileInfo = fileInfo.copy(downloadedBytes = downloadedBytes)
            val updatedFileMap = taskInfo.fileProgressMap + (fileLink to updatedFileInfo)
            currentMap + (taskId to taskInfo.copy(fileProgressMap = updatedFileMap))
        }
    }

    fun updateFileSpeed(
        taskId: String,
        fileLink: String,
        speed: Long,
    ) {
        _progressInfo.update { currentMap ->
            val taskInfo = currentMap[taskId] ?: TaskProgressInfo()
            val fileInfo = taskInfo.fileProgressMap[fileLink] ?: FileProgressInfo()
            val updatedFileInfo = fileInfo.copy(speed = speed)
            val updatedFileMap = taskInfo.fileProgressMap + (fileLink to updatedFileInfo)
            currentMap + (taskId to taskInfo.copy(fileProgressMap = updatedFileMap))
        }
    }

    fun updateProviderTorrentInfo(taskId: String, info: ProviderTorrentInfo?) {
        _progressInfo.update { currentMap ->
            val taskInfo = currentMap[taskId] ?: TaskProgressInfo()
            currentMap + (taskId to taskInfo.copy(providerTorrentInfo = info))
        }
    }

    fun updateProviderTorrentState(taskId: String, state: ProviderTorrentState) {
        _progressInfo.update { currentMap ->
            val taskInfo = currentMap[taskId] ?: return@update currentMap
            val currentInfo = taskInfo.providerTorrentInfo ?: return@update currentMap
            val updatedInfo = currentInfo.copy(state = state)
            currentMap + (taskId to taskInfo.copy(providerTorrentInfo = updatedInfo))
        }
    }

    fun setProviderFilePriority(taskId: String, fileId: Int, priority: FilePriority) {
        _progressInfo.update { currentMap ->
            val taskInfo = currentMap[taskId] ?: return@update currentMap
            val currentInfo = taskInfo.providerTorrentInfo ?: return@update currentMap
            val updatedFiles = currentInfo.files.map { file ->
                if (file.id == fileId) {
                    file.copy(priority = priority, isSelected = priority != FilePriority.IGNORE)
                } else {
                    file
                }
            }
            val updatedInfo = currentInfo.copy(files = updatedFiles)
            currentMap + (taskId to taskInfo.copy(providerTorrentInfo = updatedInfo))
        }
    }

    fun toggleProviderFileSelectionLocally(taskId: String, fileId: Int) {
        _progressInfo.update { currentMap ->
            val taskInfo = currentMap[taskId] ?: return@update currentMap
            val currentInfo = taskInfo.providerTorrentInfo ?: return@update currentMap
            val updatedFiles = currentInfo.files.map { file ->
                if (file.id == fileId) {
                    file.copy(isSelected = !file.isSelected)
                } else {
                    file
                }
            }
            val updatedInfo = currentInfo.copy(files = updatedFiles)
            currentMap + (taskId to taskInfo.copy(providerTorrentInfo = updatedInfo))
        }
    }

    fun toggleAllProviderFilesSelectionLocally(taskId: String, selectAll: Boolean) {
        _progressInfo.update { currentMap ->
            val taskInfo = currentMap[taskId] ?: return@update currentMap
            val currentInfo = taskInfo.providerTorrentInfo ?: return@update currentMap
            val updatedFiles = currentInfo.files.map { file ->
                file.copy(isSelected = selectAll)
            }
            val updatedInfo = currentInfo.copy(files = updatedFiles)
            currentMap + (taskId to taskInfo.copy(providerTorrentInfo = updatedInfo))
        }
    }

    fun setProviderTorrentInfoWaitingForFileSelection(taskId: String) {
        _progressInfo.update { currentMap ->
            val taskInfo = currentMap[taskId] ?: return@update currentMap
            val currentInfo = taskInfo.providerTorrentInfo ?: return@update currentMap
            val updatedInfo = currentInfo.copy(
                state = ProviderTorrentState.WAITING_FOR_FILE_SELECTION,
                status = "waiting_for_file_selection",
                downloadSpeed = 0,
                uploadSpeed = 0,
            )
            currentMap + (taskId to taskInfo.copy(providerTorrentInfo = updatedInfo))
        }
    }

    fun markAllTaskFilesCompletedLocally(taskId: String, files: List<DownloadFileEntity>) {
        _progressInfo.update { currentMap ->
            val taskInfo = currentMap[taskId] ?: TaskProgressInfo()
            val updatedFileMap = taskInfo.fileProgressMap.toMutableMap()
            files.forEach { file ->
                val currentFileProgress = updatedFileMap[file.link] ?: FileProgressInfo()
                updatedFileMap[file.link] = currentFileProgress.copy(
                    progress = 100,
                    downloadedBytes = file.totalBytes,
                    speed = 0,
                )
            }
            currentMap + (taskId to taskInfo.copy(fileProgressMap = updatedFileMap))
        }
    }

    fun clearTask(taskId: String) {
        _progressInfo.update { currentMap ->
            currentMap - taskId
        }
    }
}

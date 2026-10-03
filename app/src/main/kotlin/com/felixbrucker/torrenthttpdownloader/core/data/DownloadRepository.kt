package com.felixbrucker.torrenthttpdownloader.core.data

import com.felixbrucker.torrenthttpdownloader.core.data.providers.FilePriority
import com.felixbrucker.torrenthttpdownloader.core.data.providers.ProviderTorrentInfo
import com.felixbrucker.torrenthttpdownloader.core.data.providers.ProviderTorrentState
import com.felixbrucker.torrenthttpdownloader.core.model.DownloadFile
import com.felixbrucker.torrenthttpdownloader.core.model.DownloadTask
import com.felixbrucker.torrenthttpdownloader.core.model.LocalDownloadState
import com.felixbrucker.torrenthttpdownloader.core.model.TorrentState
import com.felixbrucker.torrenthttpdownloader.di.ApplicationScope
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import timber.log.Timber

@Singleton
class DownloadRepository @Inject constructor(
    private val downloadTracker: DownloadTracker,
    private val downloadProgressTracker: DownloadProgressTracker,
    @ApplicationScope private val scope: CoroutineScope,
) {
    @get:JvmName("getTasksFlow")
    val tasks: StateFlow<List<DownloadTask>> = combine(
        downloadTracker.tasks,
        downloadProgressTracker.progressInfo,
    ) { staticTasks, progressMap ->
        combineTasksWithProgressInfo(staticTasks, progressMap)
    }.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = combineTasksWithProgressInfo(
            downloadTracker.getTasks(),
            downloadProgressTracker.progressInfo.value,
        ),
    )

    fun getTasks(): List<DownloadTask> = tasks.value

    fun hasTasksWhichNeedProcessing(): Boolean = downloadTracker.hasTasksWhichNeedProcessing()

    fun addTask(task: DownloadTask) {
        Timber.i("Adding download task id=%s, name=%s", task.id, task.name)
        if (task.providerTorrentInfo != null) {
            downloadProgressTracker.updateProviderTorrentInfo(task.id, task.providerTorrentInfo)
        }
        downloadTracker.addTask(task.copy(providerTorrentInfo = null))
    }

    fun moveTask(fromIndex: Int, toIndex: Int) {
        downloadTracker.moveTask(fromIndex, toIndex)
    }

    fun findTask(id: String): DownloadTask? = tasks.value.find { it.id == id }

    fun hasTask(id: String): Boolean = tasks.value.any { it.id == id }

    fun findTaskFile(taskId: String, fileLink: String): DownloadFile? {
        return findTask(taskId)?.files?.find { it.link == fileLink }
    }

    fun removeTask(id: String) {
        Timber.i("Removing download task id=%s", id)
        downloadTracker.removeTask(id)
        downloadProgressTracker.clearTask(id)
    }

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
            )
        }
        downloadProgressTracker.updateFileDownloadedBytes(taskId, fileLink, downloadedBytes)
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
        downloadProgressTracker.updateFileProgress(
            taskId = taskId,
            fileLink = fileLink,
            progress = progress,
            downloadedBytes = downloadedBytes,
            speed = speed,
            lastTimestamp = lastTimestamp,
            lastBytes = lastBytes,
        )
    }

    fun updateFileState(
        taskId: String,
        fileLink: String,
        state: LocalDownloadState,
        stateDescription: String? = null,
    ) {
        downloadTracker.updateTaskFile(taskId, fileLink) { file ->
            file.copy(state = state, stateDescription = stateDescription)
        }
        downloadProgressTracker.updateFileSpeed(taskId, fileLink, 0)
    }

    fun updateProviderTorrentState(taskId: String, state: ProviderTorrentState) {
        downloadProgressTracker.updateProviderTorrentState(taskId, state)
    }

    fun setProviderFilePriority(taskId: String, fileId: Int, priority: FilePriority) {
        downloadProgressTracker.setProviderFilePriority(taskId, fileId, priority)
    }

    fun toggleProviderFileSelectionLocally(taskId: String, fileId: Int) {
        downloadProgressTracker.toggleProviderFileSelectionLocally(taskId, fileId)
    }

    fun toggleAllProviderFilesSelectionLocally(taskId: String, selectAll: Boolean) {
        downloadProgressTracker.toggleAllProviderFilesSelectionLocally(taskId, selectAll)
    }

    fun updateTaskState(taskId: String, state: TorrentState) {
        Timber.d("Updating task id=%s state to %s", taskId, state)
        downloadTracker.updateTask(taskId) { task ->
            task.copy(state = state)
        }
    }

    fun updateTaskError(taskId: String, errorMessage: String?) {
        Timber.e("Task id=%s error: %s", taskId, errorMessage)
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
        downloadProgressTracker.setProviderTorrentInfoWaitingForFileSelection(taskId)
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
        val taskFiles = findTask(taskId)?.files ?: emptyList()
        downloadTracker.updateTaskFiles(taskId) { file ->
            file.copy(
                unrestrictedLink = null,
                state = LocalDownloadState.COMPLETED,
            )
        }
        downloadProgressTracker.markAllTaskFilesCompletedLocally(taskId, taskFiles)
    }

    fun resetTaskForRetry(taskId: String) {
        Timber.i("Resetting task id=%s for retry", taskId)
        downloadTracker.updateTask(taskId) { task ->
            task.copy(
                providerId = null,
                state = TorrentState.ADDING_TO_PROVIDER,
                files = emptyList(),
                errorMessage = null,
            )
        }
        downloadProgressTracker.clearTask(taskId)
    }

    fun updateTaskWithTorrentInfo(taskId: String, torrentInfo: ProviderTorrentInfo) {
        downloadTracker.updateTask(taskId) { task ->
            task.copy(
                name = if (task.name == task.torrent.uri) torrentInfo.name else task.name,
            )
        }
        downloadProgressTracker.updateProviderTorrentInfo(taskId, torrentInfo)
    }

    private companion object {
        fun combineTasksWithProgressInfo(
            staticTasks: List<DownloadTask>,
            progressMap: Map<String, TaskProgressInfo>,
        ): List<DownloadTask> {
            return staticTasks.map { task ->
                val progressInfo = progressMap[task.id] ?: return@map task
                val updatedProviderInfo = progressInfo.providerTorrentInfo ?: task.providerTorrentInfo
                val updatedFiles = task.files.map { file ->
                    val fileProgress = progressInfo.fileProgressMap[file.link] ?: return@map file
                    file.copy(
                        progress = fileProgress.progress,
                        speed = fileProgress.speed,
                        downloadedBytes = fileProgress.downloadedBytes,
                        lastBytes = fileProgress.lastBytes,
                        lastTimestamp = fileProgress.lastTimestamp,
                    )
                }
                task.copy(
                    providerTorrentInfo = updatedProviderInfo,
                    files = updatedFiles,
                )
            }
        }
    }
}

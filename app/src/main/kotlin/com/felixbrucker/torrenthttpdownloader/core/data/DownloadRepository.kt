package com.felixbrucker.torrenthttpdownloader.core.data

import com.felixbrucker.torrenthttpdownloader.core.data.providers.FilePriority
import com.felixbrucker.torrenthttpdownloader.core.data.providers.ProviderTorrentInfo
import com.felixbrucker.torrenthttpdownloader.core.data.providers.ProviderTorrentState
import com.felixbrucker.torrenthttpdownloader.core.model.DownloadFile
import com.felixbrucker.torrenthttpdownloader.core.model.DownloadFileEntity
import com.felixbrucker.torrenthttpdownloader.core.model.DownloadTask
import com.felixbrucker.torrenthttpdownloader.core.model.DownloadTaskEntity
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
    @param:ApplicationScope private val scope: CoroutineScope,
) {
    @get:JvmName("getTasksFlow")
    val tasks: StateFlow<List<DownloadTask>> = combine(
        downloadTracker.tasks,
        downloadProgressTracker.progressInfo,
    ) { taskEntities, progressMap ->
        combineTasksWithProgressInfo(taskEntities, progressMap)
    }.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = combineTasksWithProgressInfo(
            downloadTracker.getTasks(),
            downloadProgressTracker.progressInfo.value,
        ),
    )

    fun getTasks(): List<DownloadTask> = tasks.value

    fun getTaskIds(): List<String> = tasks.value.map { it.id }

    fun hasTasksWhichNeedProcessing(): Boolean = downloadTracker.hasTasksWhichNeedProcessing()

    fun addTask(task: DownloadTaskEntity) {
        Timber.i("Adding download task id=%s, name=%s", task.id, task.name)
        downloadTracker.addTask(task)
    }

    fun addTaskFiles(taskId: String, files: List<DownloadFileEntity>) {
        downloadTracker.addTaskFiles(taskId, files)
    }

    fun moveTask(fromIndex: Int, toIndex: Int) {
        downloadTracker.moveTask(fromIndex, toIndex)
    }

    fun findTask(id: String): DownloadTask? = tasks.value.find { it.id == id }

    fun findTaskEntity(id: String): DownloadTaskEntity? = downloadTracker.findTaskEntity(id)

    fun hasTask(id: String): Boolean = downloadTracker.hasTask(id)

    fun findTaskFile(taskId: String, fileLink: String): DownloadFile? {
        return findTask(taskId)?.files?.find { it.link == fileLink }
    }

    fun findTaskFileEntity(taskId: String, fileLink: String): DownloadFileEntity? {
        return downloadTracker.findTaskFileEntity(taskId, fileLink)
    }

    fun findTaskFileEntities(taskId: String): List<DownloadFileEntity> {
        return downloadTracker.findTaskFileEntities(taskId)
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
        fileName: String,
        unrestrictedLink: String,
    ) {
        downloadTracker.updateTaskFile(taskId, fileLink) { file ->
            file.copy(
                totalBytes = totalBytes,
                filePath = filePath,
                fileName = fileName,
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

    fun markAllTaskFilesCompletedLocally(taskId: String) {
        downloadTracker.markAllTaskFilesCompletedLocally(taskId)
        val taskFiles = findTaskFileEntities(taskId)
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
            taskEntities: List<DownloadTaskEntity>,
            progressMap: Map<String, TaskProgressInfo>,
        ): List<DownloadTask> {
            return taskEntities.map { taskEntity ->
                val progressInfo = progressMap[taskEntity.id]
                val providerInfo = progressInfo?.providerTorrentInfo
                val combinedFiles = taskEntity.files.map { fileEntity ->
                    val fileProgress = progressInfo?.fileProgressMap?.get(fileEntity.link)
                    fileEntity.toDownloadFile(fileProgress)
                }
                taskEntity.toDownloadTask(
                    providerTorrentInfo = providerInfo,
                    files = combinedFiles,
                )
            }
        }
    }
}

package com.felixbrucker.torrenthttpdownloader.core.data

import com.felixbrucker.torrenthttpdownloader.core.data.providers.FilePriority
import com.felixbrucker.torrenthttpdownloader.core.data.providers.ProviderTorrentInfo
import com.felixbrucker.torrenthttpdownloader.core.data.providers.ProviderTorrentState
import com.felixbrucker.torrenthttpdownloader.core.database.dao.DownloadDao
import com.felixbrucker.torrenthttpdownloader.core.database.entity.DownloadFileEntity
import com.felixbrucker.torrenthttpdownloader.core.database.entity.DownloadFinalFileEntity
import com.felixbrucker.torrenthttpdownloader.core.database.entity.DownloadTaskEntity
import com.felixbrucker.torrenthttpdownloader.core.database.entity.DownloadTaskWithFiles
import com.felixbrucker.torrenthttpdownloader.core.model.DownloadTask
import com.felixbrucker.torrenthttpdownloader.core.model.LocalDownloadState
import com.felixbrucker.torrenthttpdownloader.core.model.TorrentState
import com.felixbrucker.torrenthttpdownloader.di.ApplicationScope
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import timber.log.Timber

@Singleton
class DownloadRepository @Inject constructor(
    private val downloadDao: DownloadDao,
    private val downloadProgressTracker: DownloadProgressTracker,
    @param:ApplicationScope private val scope: CoroutineScope,
) {
    @get:JvmName("getTasksFlow")
    val tasks: StateFlow<List<DownloadTask>> = combine(
        downloadDao.getTasksWithFilesFlow().map { map ->
            map.map { (task, files) -> DownloadTaskWithFiles(task, files) }
        },
        downloadProgressTracker.progressInfo,
    ) { tasksWithFiles, progressMap ->
        combineTasksWithProgressInfo(tasksWithFiles, progressMap)
    }.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly, // getTasks() might be broken if we don't start eagerly
        initialValue = emptyList(),
    )

    fun getTasks(): List<DownloadTask> = tasks.value

    suspend fun getTaskIds(): List<String> = downloadDao.getTaskIds()

    suspend fun hasTasksWhichNeedProcessing(): Boolean = downloadDao.hasTasksWhichNeedProcessing()
    fun hasTasksWhichNeedProcessingFlow(): Flow<Boolean> = downloadDao.hasTasksWhichNeedProcessingFlow()

    suspend fun addTask(task: DownloadTaskEntity) {
        Timber.i("Adding download task id=%s, name=%s", task.id, task.name)
        val maxOrder = downloadDao.getMaxDisplayOrder() ?: 0.0
        val taskWithOrder = task.copy(displayOrder = maxOrder + 10.0)
        downloadDao.insertTask(taskWithOrder)
    }

    suspend fun addTaskFiles(files: List<DownloadFileEntity>) {
        downloadDao.insertFiles(files)
    }

    suspend fun addFinalFiles(files: List<DownloadFinalFileEntity>) {
        downloadDao.insertFinalFiles(files)
    }

    suspend fun getFinalFilesForTask(taskId: String): List<String> {
        return downloadDao.getFinalFilesForTask(taskId)
    }

    suspend fun moveTask(fromIndex: Int, toIndex: Int) {
        val currentTasks = getTasksWithFiles()
        if (fromIndex !in currentTasks.indices || toIndex !in currentTasks.indices) return
        val mutable = currentTasks.map { it.task }.toMutableList()
        val moved = mutable.removeAt(fromIndex)
        mutable.add(toIndex, moved)
        val prevOrder = if (toIndex > 0) mutable[toIndex - 1].displayOrder else 0.0
        val nextOrder = if (toIndex < mutable.size - 1) mutable[toIndex + 1].displayOrder else prevOrder + 20.0
        val newOrder = (prevOrder + nextOrder) / 2.0
        downloadDao.updateTaskDisplayOrder(moved.id, newOrder)
    }

    fun findTask(id: String): DownloadTask? = tasks.value.find { it.id == id }

    suspend fun findTaskEntity(id: String): DownloadTaskEntity? = downloadDao.getTask(id)

    suspend fun findTaskWithFiles(id: String): DownloadTaskWithFiles? {
        val map = downloadDao.getTaskWithFiles(id)
        return map.entries.firstOrNull()?.let { (task, files) -> DownloadTaskWithFiles(task, files) }
    }

    suspend fun hasTask(id: String): Boolean = downloadDao.hasTask(id)

    suspend fun findTaskFileEntity(taskId: String, fileLink: String): DownloadFileEntity? {
        return downloadDao.getFile(taskId, fileLink)
    }

    suspend fun findTaskFileEntities(taskId: String): List<DownloadFileEntity> {
        return downloadDao.getFilesForTask(taskId)
    }

    suspend fun removeTask(id: String) {
        Timber.i("Removing download task id=%s", id)
        downloadDao.deleteTask(id)
        downloadProgressTracker.clearTask(id)
    }

    suspend fun updateFileDownloadingState(
        taskId: String,
        fileLink: String,
        totalBytes: Long,
        downloadedBytes: Long,
    ) {
        downloadDao.updateFileDownloadingState(taskId, fileLink, LocalDownloadState.DOWNLOADING, totalBytes)
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

    suspend fun updateFileState(
        taskId: String,
        fileLink: String,
        state: LocalDownloadState,
        stateDescription: String? = null,
    ) {
        downloadDao.updateFileState(taskId, fileLink, state, stateDescription)
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

    suspend fun updateTaskState(taskId: String, state: TorrentState) {
        Timber.d("Updating task id=%s state to %s", taskId, state)
        downloadDao.updateTaskState(taskId, state)
    }

    suspend fun updateTaskError(taskId: String, errorMessage: String?) {
        Timber.e("Task id=%s error: %s", taskId, errorMessage)
        downloadDao.updateTaskError(taskId, errorMessage, TorrentState.ERROR)
    }

    suspend fun updateTaskFileInfo(
        taskId: String,
        fileLink: String,
        totalBytes: Long,
        filePath: String,
        fileName: String,
        unrestrictedLink: String,
    ) {
        downloadDao.updateTaskFileInfo(taskId, fileLink, totalBytes, filePath, fileName, unrestrictedLink)
    }

    suspend fun updateTaskProviderIdAndState(taskId: String, providerId: String, state: TorrentState) {
        downloadDao.updateTaskProviderIdAndState(taskId, providerId, state)
    }

    fun setProviderTorrentInfoWaitingForFileSelection(taskId: String) {
        downloadProgressTracker.setProviderTorrentInfoWaitingForFileSelection(taskId)
    }

    suspend fun markAllTaskFilesCompletedLocally(taskId: String) {
        val taskFiles = downloadDao.getFilesForTask(taskId)
        downloadProgressTracker.markAllTaskFilesCompletedLocally(taskId, taskFiles)
    }

    suspend fun resetTaskForRetry(taskId: String) {
        Timber.i("Resetting task id=%s for retry", taskId)
        downloadDao.resetTaskForRetry(taskId, TorrentState.ADDING_TO_PROVIDER)
        downloadProgressTracker.clearTask(taskId)
    }

    suspend fun updateTaskWithTorrentInfo(taskId: String, torrentInfo: ProviderTorrentInfo) {
        downloadDao.updateTaskNameIfDefault(taskId, torrentInfo.name)
        downloadProgressTracker.updateProviderTorrentInfo(taskId, torrentInfo)
    }

    private suspend fun getTasksWithFiles(): List<DownloadTaskWithFiles> {
        val map = downloadDao.getTasksWithFiles()
        return map.map { (task, files) -> DownloadTaskWithFiles(task, files) }
    }

    private companion object {
        fun combineTasksWithProgressInfo(
            tasksWithFiles: List<DownloadTaskWithFiles>,
            progressMap: Map<String, TaskProgressInfo>,
        ): List<DownloadTask> {
            return tasksWithFiles.map { taskWithFiles ->
                val progressInfo = progressMap[taskWithFiles.task.id]
                val providerInfo = progressInfo?.providerTorrentInfo
                val fileProgressMap = progressInfo?.fileProgressMap
                taskWithFiles.toDownloadTask(
                    providerTorrentInfo = providerInfo,
                    fileProgressMap = fileProgressMap,
                )
            }
        }
    }
}

package com.felixbrucker.torrenthttpdownloader.core.data

import android.app.BackgroundServiceStartNotAllowedException
import android.content.ContentResolver
import android.content.Intent
import androidx.annotation.VisibleForTesting
import androidx.core.net.toUri
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.*
import com.felixbrucker.torrenthttpdownloader.R
import com.felixbrucker.torrenthttpdownloader.core.data.providers.FilePriority
import com.felixbrucker.torrenthttpdownloader.core.data.providers.ProviderFeature
import com.felixbrucker.torrenthttpdownloader.core.data.providers.ProviderTorrentInfo
import com.felixbrucker.torrenthttpdownloader.core.data.providers.ProviderTorrentState
import com.felixbrucker.torrenthttpdownloader.core.data.providers.TorrentProvider
import com.felixbrucker.torrenthttpdownloader.core.designsystem.icons.downloading
import com.felixbrucker.torrenthttpdownloader.core.model.DownloadFile
import com.felixbrucker.torrenthttpdownloader.core.model.DownloadTask
import com.felixbrucker.torrenthttpdownloader.core.model.FileSelectionMode
import com.felixbrucker.torrenthttpdownloader.core.model.LocalDownloadState
import com.felixbrucker.torrenthttpdownloader.core.model.TaskLocation
import com.felixbrucker.torrenthttpdownloader.core.model.TorrentState
import com.felixbrucker.torrenthttpdownloader.core.model.TorrentType
import com.felixbrucker.torrenthttpdownloader.core.network.BandwidthLimitExceededException
import com.felixbrucker.torrenthttpdownloader.core.network.RateLimitExceededException
import com.felixbrucker.torrenthttpdownloader.core.network.ResourceNotFoundException
import com.felixbrucker.torrenthttpdownloader.core.util.PathFactory
import com.felixbrucker.torrenthttpdownloader.extensions.asFile
import com.felixbrucker.torrenthttpdownloader.extensions.cleanedForUseAsPath
import com.felixbrucker.torrenthttpdownloader.extensions.createDirectoryRecursivelyIfNotExists
import com.felixbrucker.torrenthttpdownloader.extensions.flatten
import com.felixbrucker.torrenthttpdownloader.extensions.makeOpenFileIntent
import com.felixbrucker.torrenthttpdownloader.extensions.mergeIntoDirectory
import com.felixbrucker.torrenthttpdownloader.extensions.torrentInfo
import com.felixbrucker.torrenthttpdownloader.extensions.tryToExtractArchiveInPlace

class TorrentStateMachine(
    private val scope: CoroutineScope,
    private val provider: TorrentProvider,
    private val localDownloadManager: LocalDownloadManager,
    private val contentResolver: ContentResolver,
    private val downloadRepository: DownloadRepository,
    private val pathFactory: PathFactory,
    private val onTaskCompleted: (DownloadTask) -> Unit,
    private val onPostNotification: (String, String, Intent?, Int?) -> Unit,
) {
    private val processingTasks = ConcurrentHashMap.newKeySet<String>()
    private val taskIdsToProcess = ConcurrentLinkedQueue<String>()

    fun start() {
        scope.launch(Dispatchers.IO) {
            while (isActive) {
                val taskId = taskIdsToProcess.poll()
                if (taskId == null) {
                    delay(500.milliseconds) // Wait before polling again
                    continue
                }
                scope.launch(Dispatchers.IO) {
                    val newId = processTaskSafely(taskId)
                    if (newId != null) {
                        taskIdsToProcess.add(newId)
                    }
                }
            }
        }
        scope.launch(Dispatchers.IO) { resumeDownloads() }
    }

    fun stop() {
        taskIdsToProcess.clear()
    }

    fun addTask(task: DownloadTask) {
        downloadRepository.addTask(task)
        taskIdsToProcess.add(task.id)
    }

    suspend fun pauseAllTasksOnProvider() {
        if (!provider.supports(ProviderFeature.PauseResume)) return
        downloadRepository
            .getTasks()
            .filter { it.providerTorrentInfo?.state == ProviderTorrentState.DOWNLOADING }
            .forEach { task -> pauseTaskOnProvider(task.id) }
    }

    suspend fun resumeAllTasksOnProvider() {
        if (!provider.supports(ProviderFeature.PauseResume)) return
        downloadRepository
            .getTasks()
            .filter { it.providerTorrentInfo?.state == ProviderTorrentState.PAUSED }
            .forEach { task -> resumeTaskOnProvider(task.id) }
    }

    suspend fun pauseTaskOnProvider(taskId: String) {
        val task = downloadRepository.findTask(taskId) ?: return
        val providerId = task.providerId ?: return
        provider.pause(providerId)
        downloadRepository.updateProviderTorrentState(task.id, ProviderTorrentState.PAUSED)
    }

    suspend fun resumeTaskOnProvider(taskId: String) {
        val task = downloadRepository.findTask(taskId) ?: return
        val providerId = task.providerId ?: return
        provider.resume(providerId)
        downloadRepository.updateProviderTorrentState(task.id, ProviderTorrentState.DOWNLOADING)
    }

    suspend fun setFilePriority(taskId: String, fileId: Int, priority: FilePriority) {
        val task = downloadRepository.findTask(taskId) ?: return
        val providerId = task.providerId ?: return
        provider.setFilePriority(providerId, fileId, priority)

        downloadRepository.setProviderFilePriority(taskId, fileId, priority)
    }

    fun toggleFileSelectionLocally(taskId: String, fileId: Int) {
        downloadRepository.toggleProviderFileSelectionLocally(taskId, fileId)
    }

    fun toggleAllFilesSelectionLocally(taskId: String, selectAll: Boolean) {
        downloadRepository.toggleAllProviderFilesSelectionLocally(taskId, selectAll)
    }

    suspend fun confirmFileSelection(taskId: String) {
        val task = downloadRepository.findTask(taskId) ?: return
        if (task.state != TorrentState.SELECTING_FILES) return

        val providerId = task.providerId ?: return
        val selectedFileIds = task.providerTorrentInfo?.files?.filter { it.isSelected }?.map { it.id } ?: listOf()

        try {
            val isSuccessful = provider.selectFiles(providerId, selectedFileIds)
            if (isSuccessful) {
                downloadRepository.updateTaskState(taskId, TorrentState.WAITING_FOR_PROVIDER_DOWNLOAD)
                if (provider.supports(ProviderFeature.PauseResume)) {
                    provider.resume(providerId)
                }
                taskIdsToProcess.add(taskId)
            }
        } catch (e: Exception) {
            downloadRepository.updateTaskError(
                taskId,
                "Failed to select files: ${e.message}"
            )
        }
    }

    suspend fun restartTask(taskId: String) {
        val task = downloadRepository.findTask(taskId) ?: return
        try {
            resetTorrent(task)
            taskIdsToProcess.add(task.id)
        } catch (e: Exception) {
            downloadRepository.updateTaskError(
                task.id,
                "Failed to restart task: ${e.message}"
            )
        }
    }

    suspend fun removeTask(
        taskId: String,
        deleteFiles: Boolean = true,
        deleteTorrentFile: Boolean = true
    ) {
        val task = downloadRepository.findTask(taskId) ?: return

        localDownloadManager.removeTaskFromQueues(task)

        if (deleteFiles) {
            task.deleteFiles()
            task.removeScopedTemporaryDirectory(pathFactory)
            task.removeResumeData(pathFactory)
        }

        if (deleteTorrentFile) {
            task.removeTorrentFile()
        }

        // If the task is on Provider, delete it there
        if (task.state.ordinal < TorrentState.DELETING_FROM_PROVIDER.ordinal && task.providerId != null) {
            provider.deleteTorrent(task.providerId, deleteFiles = deleteFiles)
        }

        downloadRepository.removeTask(taskId)
    }

    suspend fun updateFileInfo(task: DownloadTask, file: DownloadFile) {
        val providerId = task.providerId ?: return
        val unrestrictLinkResponse = provider.unrestrictLink(providerId, file.link)
        val filePath = File(
            pathFactory.getScopedTemporaryDirectory(task.name).absolutePath,
            unrestrictLinkResponse.filename.cleanedForUseAsPath()
        ).absolutePath

        downloadRepository.updateTaskFileInfo(
            taskId = task.id,
            fileLink = file.link,
            totalBytes = unrestrictLinkResponse.size,
            filePath = filePath,
            unrestrictedLink = unrestrictLinkResponse.downloadUrl,
        )
    }

    private suspend fun resumeDownloads() {
        downloadRepository
            .getTasks()
            .filter { it.location == TaskLocation.PROVIDER }
            .mapNotNull { it.providerId }
            .forEach { provider.restoreTorrent(it) }

        downloadRepository.getTasks().forEach { task -> taskIdsToProcess.add(task.id) }
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    internal suspend fun processTaskSafely(taskId: String?): String? {
        if (taskId == null) return null
        if (!processingTasks.add(taskId)) return null
        var newId: String?
        try {
            newId = processTask(taskId)
        } finally {
            processingTasks.remove(taskId)
        }

        return newId
    }

    private suspend fun processTask(taskId: String): String? {
        val task = downloadRepository.findTask(taskId) ?: return null
        val delayBetweenInitialProviderUpdates = if (provider.isLocalProvider) 500.milliseconds else 2.seconds
        val delayBetweenProviderUpdates = if (provider.isLocalProvider) 1.seconds else 5.seconds

        try {
            when (task.state) {
                TorrentState.ADDING_TO_PROVIDER -> {
                    val providerId = if (task.torrent.type == TorrentType.MAGNET) {
                        provider.addMagnet(task.torrent.uri, task.name)
                    } else {
                        val inputStream = try {
                            contentResolver.openInputStream(task.torrent.uri.toUri())
                        } catch (_: Exception) {
                            contentResolver.openInputStream(task.torrent.uri.asFile().toUri())
                        }
                        inputStream?.use {
                            provider.addTorrent(it.readBytes(), task.name)
                        } ?: throw Exception("Could not open torrent file")
                    }
                    downloadRepository.updateTaskProviderIdAndState(
                        taskId = task.id,
                        providerId = providerId,
                        state = TorrentState.WAITING_FOR_FILE_SELECTION,
                    )

                    return task.id
                }

                TorrentState.WAITING_FOR_FILE_SELECTION -> {
                    require(task.providerId != null) { "Missing provider id" }
                    val torrentInfo = provider.getTorrentInfo(task.providerId)
                    updateTaskWithTorrentInfo(task.id, torrentInfo)

                    if (torrentInfo.state == ProviderTorrentState.ERROR) {
                        handleFailedProviderTask(task)

                        return task.id
                    }

                    if (
                        torrentInfo.files.isNotEmpty()
                        && (torrentInfo.state == ProviderTorrentState.WAITING_FOR_FILE_SELECTION
                            || torrentInfo.state == ProviderTorrentState.DOWNLOADING
                            || torrentInfo.state == ProviderTorrentState.COMPLETED
                        )
                    ) {
                        downloadRepository.updateTaskState(task.id, TorrentState.SELECTING_FILES)
                    } else {
                        // Still processing, check again later
                        delay(delayBetweenInitialProviderUpdates)
                    }

                    return task.id
                }

                TorrentState.SELECTING_FILES -> {
                    require(task.providerId != null) { "Missing provider id" }
                    val torrentInfo = provider.getTorrentInfo(task.providerId)
                    updateTaskWithTorrentInfo(task.id, torrentInfo)

                    if (task.fileSelectionMode == FileSelectionMode.MANUAL) {
                        // Wait for user to confirm selection, stop polling for updates and simulate
                        // state support by setting it manually.
                        if (provider.supports(ProviderFeature.PauseResume)) {
                            provider.pause(task.providerId)
                        }
                        downloadRepository.setProviderTorrentInfoWaitingForFileSelection(task.id)

                        return null
                    }

                    val fileIdsToSelect = if (task.fileSelectionMode == FileSelectionMode.BIGGEST) {
                        val biggestFile = torrentInfo.files.maxBy { it.size }

                        listOf(biggestFile.id)
                    } else {
                        torrentInfo.files.map { it.id }
                    }
                    try {
                        val isSuccessful = provider.selectFiles(task.providerId, fileIdsToSelect)
                        if (isSuccessful) {
                            downloadRepository.updateTaskState(task.id, TorrentState.WAITING_FOR_PROVIDER_DOWNLOAD)

                            return task.id
                        }
                    } catch (e: Exception) {
                        downloadRepository.updateTaskError(
                            task.id,
                            "Failed to select files: ${e.message}"
                        )
                    }
                }

                TorrentState.WAITING_FOR_PROVIDER_DOWNLOAD -> {
                    require(task.providerId != null) { "Missing provider id" }
                    val torrentInfo = provider.getTorrentInfo(task.providerId)
                    updateTaskWithTorrentInfo(task.id, torrentInfo)

                    if (torrentInfo.state == ProviderTorrentState.ERROR) {
                        handleFailedProviderTask(task)

                        return task.id
                    }

                    if (torrentInfo.state == ProviderTorrentState.COMPLETED) {
                        val files = torrentInfo.links.map { DownloadFile(it) }
                        downloadRepository.updateTaskFilesAndState(
                            taskId = task.id,
                            files = files,
                            state = TorrentState.POPULATING_FILE_INFOS,
                        )
                    } else {
                        // Still downloading on provider, check again later
                        delay(delayBetweenProviderUpdates)
                    }

                    return task.id
                }

                TorrentState.POPULATING_FILE_INFOS -> {
                    for (file in task.files.filter { it.filePath == null || it.unrestrictedLink == null }) {
                        updateFileInfo(task, file)
                    }
                    if (provider.isLocalProvider) {
                        downloadRepository.markAllTaskFilesCompletedLocally(task.id)
                    }

                    val updatedTask = downloadRepository.findTask(task.id) ?: return null

                    downloadRepository.updateTaskFilesAndState(
                        taskId = task.id,
                        files = updatedTask.files.sortedBy { it.fileName },
                        state = TorrentState.DOWNLOADING_LOCALLY,
                    )

                    return task.id
                }

                TorrentState.DOWNLOADING_LOCALLY -> {
                    task.files.filter { it.state != LocalDownloadState.COMPLETED && it.state != LocalDownloadState.PAUSED }.forEach {
                        localDownloadManager.enqueueDownload(DownloadWork(task.id, it))
                    }

                    var files = task.files
                    while (files.any { it.state != LocalDownloadState.COMPLETED }) {
                        delay(2.seconds)
                        files = downloadRepository.findTask(task.id)?.files ?: return null
                    }

                    // All done, continue to next state
                    downloadRepository.updateTaskState(task.id, TorrentState.DELETING_FROM_PROVIDER)

                    return task.id
                }

                TorrentState.DELETING_FROM_PROVIDER -> {
                    require(task.providerId != null) { "Missing provider id" }
                    val isTorrentDeleted = provider.deleteTorrent(task.providerId)
                    if (isTorrentDeleted) {
                        task.removeTorrentFile()
                        task.removeResumeData(pathFactory)
                        downloadRepository.updateTaskState(task.id, TorrentState.CHECKING_FOR_ARCHIVES)
                    } else {
                        delay(5.seconds)
                    }

                    return task.id
                }

                TorrentState.CHECKING_FOR_ARCHIVES -> {
                    if (task.files.any { it.filePath?.endsWith(".rar", true) == true }) {
                        downloadRepository.updateTaskState(task.id, TorrentState.EXTRACTING_ARCHIVES)
                    } else {
                        downloadRepository.updateTaskState(task.id, TorrentState.MOVING_TO_DESTINATION)
                    }

                    return task.id
                }

                TorrentState.EXTRACTING_ARCHIVES -> {
                    task.files
                        .filter { it.filePath !== null && it.filePath.endsWith(".rar", true) }
                        .map { File(it.filePath!!) }
                        .filter { it.exists() && it.isFile }
                        .forEach { rarFile ->
                            rarFile.tryToExtractArchiveInPlace(
                                scope,
                                deleteArchiveAfterExtraction = true
                            )
                        }
                    downloadRepository.updateTaskState(task.id, TorrentState.MOVING_TO_DESTINATION)

                    return task.id
                }

                TorrentState.MOVING_TO_DESTINATION -> {
                    val source = pathFactory.getScopedTemporaryDirectory(task.name)
                    source.flatten()

                    val destination = pathFactory.getScopedDestinationDirectory(task)
                    destination.parentFile?.createDirectoryRecursivelyIfNotExists()

                    if (destination.exists()) {
                        // Directory merge, move files individually
                        source.mergeIntoDirectory(destination)
                    } else {
                        // No conflict, just move the whole directory
                        source.renameTo(destination)
                    }

                    downloadRepository.updateTaskState(task.id, TorrentState.COMPLETED)

                    return task.id
                }

                TorrentState.COMPLETED -> {
                    if (task.notifyOnCompletion) {
                        val destination = pathFactory.getScopedDestinationDirectory(task)

                        onPostNotification(
                            "Download finished",
                            "${task.name} finished downloading",
                            destination.makeOpenFileIntent(),
                            R.drawable.check_24px,
                        )
                    }
                    downloadRepository.removeTask(taskId)

                    onTaskCompleted(task)
                }
                else -> { /* No action needed */ }
            }
        } catch (e: CancellationException) {
            throw e // Do not update task state for this exception
        } catch (e: BackgroundServiceStartNotAllowedException) {
            throw e // Do not update task state for this exception
        } catch (e: RateLimitExceededException) {
            e.printStackTrace()

            // Retry after 15 sec
            delay(15.seconds)

            return task.id
        } catch (e: BandwidthLimitExceededException) {
            e.printStackTrace()

            // Retry after 1 min
            delay(1.minutes)

            return task.id
        } catch (_: ResourceNotFoundException) {
            removeTask(taskId)
        } catch (e: Exception) {
            downloadRepository.updateTaskError(task.id, e.message)
            e.printStackTrace()
        }

        return null
    }

    private suspend fun handleFailedProviderTask(task: DownloadTask) {
        resetTorrent(task)
        onPostNotification(
            "Torrent has been restarted",
            "Torrent ${task.name} encountered an error on provider and has been restarted",
            null,
            R.drawable.restart_alt_24px,
        )
    }

    private suspend fun resetTorrent(task: DownloadTask) {
        localDownloadManager.removeTaskFromQueues(task)
        // Tasks not added to provider yet don't need to get deleted from there
        if (task.providerId != null) {
            provider.deleteTorrent(task.providerId)
        }
        downloadRepository.resetTaskForRetry(task.id)
    }

    private fun updateTaskWithTorrentInfo(taskId: String, torrentInfo: ProviderTorrentInfo) {
        downloadRepository.updateTaskWithTorrentInfo(taskId, torrentInfo)
    }
}

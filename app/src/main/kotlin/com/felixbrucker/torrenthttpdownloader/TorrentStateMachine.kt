package com.felixbrucker.torrenthttpdownloader

import android.app.BackgroundServiceStartNotAllowedException
import android.content.ContentResolver
import androidx.core.net.toUri
import com.felixbrucker.torrenthttpdownloader.models.*
import com.felixbrucker.torrenthttpdownloader.network.BandwidthLimitExceededException
import com.felixbrucker.torrenthttpdownloader.network.RateLimitExceededException
import com.felixbrucker.torrenthttpdownloader.network.ResourceNotFoundException
import com.felixbrucker.torrenthttpdownloader.providers.*
import com.felixbrucker.torrenthttpdownloader.storage.PathFactory
import kotlinx.coroutines.*
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class TorrentStateMachine(
    private val scope: CoroutineScope,
    private val provider: TorrentProvider,
    private val localDownloadManager: LocalDownloadManager,
    private val contentResolver: ContentResolver,
    private val onTaskCompleted: (DownloadTask) -> Unit,
    private val onPostNotification: (String, String) -> Unit,
) {
    private val processingTasks = mutableSetOf<String>()
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

        resumeDownloads()
    }

    fun stop() {
        taskIdsToProcess.clear()
    }

    fun addTask(task: DownloadTask) {
        DownloadTracker.addTask(task)
        taskIdsToProcess.add(task.id)
    }

    suspend fun pauseAllTasksOnProvider() {
        if (!provider.supportsPauseResume) return
        DownloadTracker
            .getTasks()
            .filter { it.providerTorrentInfo?.state == ProviderTorrentState.DOWNLOADING }
            .forEach { task -> pauseTaskOnProvider(task.id) }
    }

    suspend fun resumeAllTasksOnProvider() {
        if (!provider.supportsPauseResume) return
        DownloadTracker
            .getTasks()
            .filter { it.providerTorrentInfo?.state == ProviderTorrentState.PAUSED }
            .forEach { task -> resumeTaskOnProvider(task.id) }
    }

    suspend fun pauseTaskOnProvider(taskId: String) {
        val task = DownloadTracker.findTask(taskId) ?: return
        val providerId = task.providerId ?: return
        provider.pause(providerId)
        DownloadTracker.updateTask(task.id) {
            it.copy(
                providerTorrentInfo = it.providerTorrentInfo?.copy(
                    state = ProviderTorrentState.PAUSED
                )
            )
        }
    }

    suspend fun resumeTaskOnProvider(taskId: String) {
        val task = DownloadTracker.findTask(taskId) ?: return
        val providerId = task.providerId ?: return
        provider.resume(providerId)
        DownloadTracker.updateTask(task.id) {
            it.copy(
                providerTorrentInfo = it.providerTorrentInfo?.copy(
                    state = ProviderTorrentState.DOWNLOADING
                )
            )
        }
    }

    suspend fun restartTask(taskId: String) {
        val task = DownloadTracker.findTask(taskId) ?: return
        try {
            resetTorrent(task)
            taskIdsToProcess.add(task.id)
        } catch (e: Exception) {
            DownloadTracker.updateTask(task.id) {
                it.copy(
                    state = TorrentState.ERROR,
                    errorMessage = "Failed to restart task: ${e.message}",
                )
            }
        }
    }

    suspend fun removeTask(
        taskId: String,
        deleteFiles: Boolean = true,
        deleteTorrentFile: Boolean = true
    ) {
        val task = DownloadTracker.findTask(taskId) ?: return

        localDownloadManager.removeTaskFromQueues(task)

        if (deleteFiles) {
            task.deleteFiles()
            task.removeScopedTemporaryDirectory()
            task.removeResumeData()
        }

        if (deleteTorrentFile) {
            task.removeTorrentFile()
        }

        // If the task is on Provider, delete it there
        if (task.state.ordinal < TorrentState.DELETING_FROM_PROVIDER.ordinal && task.providerId != null) {
            provider.deleteTorrent(task.providerId, deleteFiles = deleteFiles)
        }

        DownloadTracker.removeTask(taskId)
    }

    suspend fun updateFileInfo(task: DownloadTask, file: DownloadFile) {
        val unrestrictLinkResponse = provider.unrestrictLink(task.id, file.link)
        val filePath = File(
            PathFactory.getScopedTemporaryDirectory(task.name).absolutePath,
            unrestrictLinkResponse.filename.cleanedForUseAsPath()
        ).absolutePath

        DownloadTracker.updateTaskFile(task.id, file.link) {
            it.copy(
                totalBytes = unrestrictLinkResponse.size,
                filePath = filePath,
                unrestrictedLink = unrestrictLinkResponse.downloadUrl,
            )
        }
    }

    private fun resumeDownloads() {
        DownloadTracker
            .getTasks()
            .filter { it.location == TaskLocation.PROVIDER }
            .forEach { provider.restoreTorrent(it.id) }

        DownloadTracker.getTasks().forEach { task -> taskIdsToProcess.add(task.id) }
    }

    private suspend fun processTaskSafely(taskId: String?): String? {
        if (taskId == null) return null
        if (processingTasks.contains(taskId)) return null
        processingTasks.add(taskId)
        var newId: String?
        try {
            newId = processTask(taskId)
        } finally {
            processingTasks.remove(taskId)
        }

        return newId
    }

    private suspend fun processTask(taskId: String): String? {
        val task = DownloadTracker.findTask(taskId) ?: return null

        try {
            when (task.state) {
                TorrentState.ADDING_TO_PROVIDER -> {
                    val newId = if (task.torrent.type == TorrentType.MAGNET) {
                        provider.addMagnet(task.torrent.uri, task.name)
                    } else {
                        contentResolver.openInputStream(task.torrent.uri.toUri())?.use {
                            provider.addTorrent(it.readBytes(), task.name)
                        } ?: throw Exception("Could not open torrent file")
                    }
                    DownloadTracker.replaceTask(
                        task.id,
                        task.copy(
                            id = newId,
                            providerId = newId,
                            state = TorrentState.WAITING_FOR_FILE_SELECTION
                        )
                    )

                    return newId
                }

                TorrentState.WAITING_FOR_FILE_SELECTION -> {
                    val torrentInfo = provider.getTorrentInfo(task.id)
                    updateTaskWithTorrentInfo(task.id, torrentInfo)

                    if (torrentInfo.state == ProviderTorrentState.ERROR) {
                        handleFailedProviderTask(task)

                        return task.id
                    }

                    if (
                        torrentInfo.state == ProviderTorrentState.WAITING_FOR_FILE_SELECTION
                        || torrentInfo.state == ProviderTorrentState.DOWNLOADING
                        || torrentInfo.state == ProviderTorrentState.COMPLETED
                    ) {
                        DownloadTracker.updateTask(task.id) {
                            it.copy(state = TorrentState.SELECTING_FILES)
                        }
                    } else {
                        // Still processing, check again later
                        delay(2.seconds)
                    }

                    return task.id
                }

                TorrentState.SELECTING_FILES -> {
                    val torrentInfo = provider.getTorrentInfo(task.id)
                    val fileIdsToSelect = if (task.onlyDownloadBiggestFile) {
                        val biggestFile = torrentInfo.files.maxBy { it.size }

                        listOf(biggestFile.id)
                    } else {
                        torrentInfo.files.map { it.id }
                    }
                    try {
                        val isSuccessful = provider.selectFiles(task.id, fileIdsToSelect)
                        if (isSuccessful) {
                            DownloadTracker.updateTask(task.id) { it.copy(state = TorrentState.WAITING_FOR_PROVIDER_DOWNLOAD) }

                            return task.id
                        }
                    } catch (e: Exception) {
                        DownloadTracker.updateTask(task.id) {
                            it.copy(
                                state = TorrentState.ERROR,
                                errorMessage = "Failed to select files: ${e.message}"
                            )
                        }
                    }
                }

                TorrentState.WAITING_FOR_PROVIDER_DOWNLOAD -> {
                    val torrentInfo = provider.getTorrentInfo(task.id)
                    updateTaskWithTorrentInfo(task.id, torrentInfo)

                    if (torrentInfo.state == ProviderTorrentState.ERROR) {
                        handleFailedProviderTask(task)

                        return task.id
                    }

                    if (torrentInfo.state == ProviderTorrentState.COMPLETED) {
                        val files = torrentInfo.links.map { DownloadFile(it) }
                        DownloadTracker.updateTask(task.id) {
                            it.copy(
                                files = files,
                                state = TorrentState.POPULATING_FILE_INFOS,
                            )
                        }
                    } else {
                        // Still downloading on provider, check again later
                        delay(5.seconds)
                    }

                    return task.id
                }

                TorrentState.POPULATING_FILE_INFOS -> {
                    for (file in task.files.filter { it.filePath == null || it.unrestrictedLink == null }) {
                        updateFileInfo(task, file)
                    }
                    if (!provider.requiresLocalDownloads) {
                        DownloadTracker.updateTaskFiles(task.id) {
                            it.copy(
                                unrestrictedLink = null,
                                state = LocalDownloadState.COMPLETED,
                                progress = 100,
                                downloadedBytes = it.totalBytes,
                            )
                        }
                    }

                    val updatedTask = DownloadTracker.findTask(task.id) ?: return null

                    DownloadTracker.updateTask(task.id) { currentTask ->
                        currentTask.copy(
                            files = updatedTask.files.sortedBy { it.fileName },
                            state = TorrentState.DOWNLOADING_LOCALLY,
                        )
                    }

                    return task.id
                }

                TorrentState.DOWNLOADING_LOCALLY -> {
                    task.files.filter { it.state != LocalDownloadState.COMPLETED && it.state != LocalDownloadState.PAUSED }.forEach {
                        localDownloadManager.enqueueDownload(DownloadWork(task.id, it))
                    }

                    var files = task.files
                    while (files.any { it.state != LocalDownloadState.COMPLETED }) {
                        delay(2.seconds)
                        files = DownloadTracker.findTask(task.id)?.files ?: return null
                    }

                    // All done, continue to next state
                    DownloadTracker.updateTask(task.id) { it.copy(state = TorrentState.DELETING_FROM_PROVIDER) }

                    return task.id
                }

                TorrentState.DELETING_FROM_PROVIDER -> {
                    val isTorrentDeleted = provider.deleteTorrent(task.id)
                    if (isTorrentDeleted) {
                        task.removeTorrentFile()
                        task.removeResumeData()
                        DownloadTracker.updateTask(task.id) { it.copy(state = TorrentState.CHECKING_FOR_ARCHIVES) }
                    } else {
                        delay(5.seconds)
                    }

                    return task.id
                }

                TorrentState.CHECKING_FOR_ARCHIVES -> {
                    if (task.files.any { it.filePath?.endsWith(".rar", true) == true }) {
                        DownloadTracker.updateTask(task.id) { it.copy(state = TorrentState.EXTRACTING_ARCHIVES) }
                    } else {
                        DownloadTracker.updateTask(task.id) { it.copy(state = TorrentState.MOVING_TO_DESTINATION) }
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
                    DownloadTracker.updateTask(task.id) { it.copy(state = TorrentState.MOVING_TO_DESTINATION) }

                    return task.id
                }

                TorrentState.MOVING_TO_DESTINATION -> {
                    val source = PathFactory.getScopedTemporaryDirectory(task.name)
                    source.flatten()

                    val destination = PathFactory.getScopedDestinationDirectory(task)
                    destination.parentFile?.createDirectoryRecursivelyIfNotExists()

                    if (destination.exists()) {
                        // Directory merge, move files individually
                        source.mergeIntoDirectory(destination)
                    } else {
                        // No conflict, just move the whole directory
                        source.renameTo(destination)
                    }

                    DownloadTracker.updateTask(task.id) { it.copy(state = TorrentState.COMPLETED) }

                    return task.id
                }

                TorrentState.COMPLETED -> {
                    if (task.notifyOnCompletion) {
                        onPostNotification(
                            "Download finished",
                            "${task.name} finished downloading",
                        )
                    }
                    DownloadTracker.removeTask(taskId)

                    onTaskCompleted(task)
                }
                else -> { /* No action needed */ }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: BackgroundServiceStartNotAllowedException) {
            throw e // Ignore
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
            DownloadTracker.updateTask(task.id) { it.copy(state = TorrentState.ERROR, errorMessage = e.message) }
            e.printStackTrace()
        }

        return null
    }

    private suspend fun handleFailedProviderTask(task: DownloadTask) {
        resetTorrent(task)
        onPostNotification(
            "Torrent has been restarted",
            "Torrent ${task.name} encountered an error on provider and has been restarted"
        )
    }

    private suspend fun resetTorrent(task: DownloadTask) {
        localDownloadManager.removeTaskFromQueues(task)
        // Tasks not added to provider yet don't need to get deleted from there
        if (task.providerId != null) {
            provider.deleteTorrent(task.providerId)
        }
        DownloadTracker.updateTask(task.id) {
            it.copy(
                providerId = null,
                state = TorrentState.ADDING_TO_PROVIDER,
                providerTorrentInfo = null,
                files = listOf(),
                errorMessage = null,
            )
        }
    }

    private fun updateTaskWithTorrentInfo(taskId: String, torrentInfo: ProviderTorrentInfo) {
        DownloadTracker.updateTask(taskId) {
            it.copy(
                name = if (it.name == it.torrent.uri) torrentInfo.name else it.name,
                providerTorrentInfo = torrentInfo,
            )
        }
    }
}

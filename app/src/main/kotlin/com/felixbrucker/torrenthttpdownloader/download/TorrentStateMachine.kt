package com.felixbrucker.torrenthttpdownloader.download

import android.app.BackgroundServiceStartNotAllowedException
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import androidx.annotation.VisibleForTesting
import androidx.core.net.toUri
import com.felixbrucker.torrenthttpdownloader.data.repository.DownloadRepository
import com.felixbrucker.torrenthttpdownloader.models.DownloadFile
import com.felixbrucker.torrenthttpdownloader.models.DownloadTask
import com.felixbrucker.torrenthttpdownloader.models.FileSelectionMode
import com.felixbrucker.torrenthttpdownloader.models.LocalDownloadState
import com.felixbrucker.torrenthttpdownloader.models.TaskLocation
import com.felixbrucker.torrenthttpdownloader.models.TorrentState
import com.felixbrucker.torrenthttpdownloader.models.TorrentType
import com.felixbrucker.torrenthttpdownloader.network.BandwidthLimitExceededException
import com.felixbrucker.torrenthttpdownloader.network.RateLimitExceededException
import com.felixbrucker.torrenthttpdownloader.network.ResourceNotFoundException
import com.felixbrucker.torrenthttpdownloader.providers.FilePriority
import com.felixbrucker.torrenthttpdownloader.providers.ProviderFactory
import com.felixbrucker.torrenthttpdownloader.providers.ProviderFeature
import com.felixbrucker.torrenthttpdownloader.providers.ProviderTorrentInfo
import com.felixbrucker.torrenthttpdownloader.providers.ProviderTorrentState
import com.felixbrucker.torrenthttpdownloader.providers.TorrentProvider
import com.felixbrucker.torrenthttpdownloader.R
import com.felixbrucker.torrenthttpdownloader.storage.PathFactory
import com.felixbrucker.torrenthttpdownloader.util.asFile
import com.felixbrucker.torrenthttpdownloader.util.cleanedForUseAsPath
import com.felixbrucker.torrenthttpdownloader.util.createDirectoryRecursivelyIfNotExists
import com.felixbrucker.torrenthttpdownloader.util.flatten
import com.felixbrucker.torrenthttpdownloader.util.makeOpenFileIntent
import com.felixbrucker.torrenthttpdownloader.util.mergeIntoDirectory
import com.felixbrucker.torrenthttpdownloader.util.tryToExtractArchiveInPlace
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@Singleton
class TorrentStateMachine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val providerFactory: ProviderFactory,
    private val localDownloadManager: LocalDownloadManager,
    private val downloadRepository: DownloadRepository
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val contentResolver: ContentResolver get() = context.contentResolver
    private var provider: TorrentProvider? = null

    var onTaskCompleted: ((DownloadTask) -> Unit)? = null
    var onPostNotification: ((String, String, Intent?, Int?) -> Unit)? = null
    private val processingTasks = ConcurrentHashMap.newKeySet<String>()
    private val taskIdsToProcess = ConcurrentLinkedQueue<String>()

    fun start() {
        scope.launch(Dispatchers.IO) {
            if (provider == null) {
                provider = providerFactory.getSelectedProvider()
            }
            while (isActive) {
                val taskId = taskIdsToProcess.poll()
                if (taskId == null) {
                    delay(500.milliseconds)
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
        scope.launch(Dispatchers.IO) {
            downloadRepository.insertTask(task)
            taskIdsToProcess.add(task.id)
        }
    }

    private suspend fun getProvider(): TorrentProvider {
        return provider ?: providerFactory.getSelectedProvider().also { provider = it }
    }

    suspend fun pauseAllTasksOnProvider() {
        val activeProvider = getProvider()
        if (!activeProvider.supports(ProviderFeature.PauseResume)) return
        downloadRepository.getTaskIdsByProviderState(ProviderTorrentState.DOWNLOADING)
            .forEach { taskId -> pauseTaskOnProvider(taskId) }
    }

    suspend fun resumeAllTasksOnProvider() {
        val activeProvider = getProvider()
        if (!activeProvider.supports(ProviderFeature.PauseResume)) return
        downloadRepository.getTaskIdsByProviderState(ProviderTorrentState.PAUSED)
            .forEach { taskId -> resumeTaskOnProvider(taskId) }
    }

    suspend fun pauseTaskOnProvider(taskId: String) {
        val activeProvider = getProvider()
        val task = downloadRepository.getTaskById(taskId) ?: return
        val providerId = task.providerId ?: return
        activeProvider.pause(providerId)
        val info = task.providerTorrentInfo?.copy(state = ProviderTorrentState.PAUSED)
        if (info != null) {
            downloadRepository.updateProviderInfo(taskId, info)
        }
    }

    suspend fun resumeTaskOnProvider(taskId: String) {
        val activeProvider = getProvider()
        val task = downloadRepository.getTaskById(taskId) ?: return
        val providerId = task.providerId ?: return
        activeProvider.resume(providerId)
        val info = task.providerTorrentInfo?.copy(state = ProviderTorrentState.DOWNLOADING)
        if (info != null) {
            downloadRepository.updateProviderInfo(taskId, info)
        }
    }

    suspend fun setFilePriority(taskId: String, fileId: Int, priority: FilePriority) {
        val activeProvider = getProvider()
        val task = downloadRepository.getTaskById(taskId) ?: return
        val providerId = task.providerId ?: return
        activeProvider.setFilePriority(providerId, fileId, priority)

        val updatedFiles = task.providerTorrentInfo?.files?.map { file ->
            if (file.id == fileId) {
                file.copy(priority = priority, isSelected = priority != FilePriority.IGNORE)
            } else {
                file
            }
        } ?: listOf()
        val info = task.providerTorrentInfo?.copy(files = updatedFiles)
        if (info != null) {
            downloadRepository.updateProviderInfo(taskId, info)
        }
    }

    fun toggleFileSelectionLocally(taskId: String, fileId: Int) {
        scope.launch(Dispatchers.IO) {
            val task = downloadRepository.getTaskById(taskId) ?: return@launch
            val updatedFiles = task.providerTorrentInfo?.files?.map { file ->
                if (file.id == fileId) file.copy(isSelected = !file.isSelected) else file
            } ?: listOf()
            val info = task.providerTorrentInfo?.copy(files = updatedFiles)
            if (info != null) {
                downloadRepository.updateProviderInfo(taskId, info)
            }
        }
    }

    fun toggleAllFilesSelectionLocally(taskId: String, selectAll: Boolean) {
        scope.launch(Dispatchers.IO) {
            val task = downloadRepository.getTaskById(taskId) ?: return@launch
            val updatedFiles = task.providerTorrentInfo?.files?.map { file ->
                file.copy(isSelected = selectAll)
            } ?: listOf()
            val info = task.providerTorrentInfo?.copy(files = updatedFiles)
            if (info != null) {
                downloadRepository.updateProviderInfo(taskId, info)
            }
        }
    }

    suspend fun confirmFileSelection(taskId: String) {
        val activeProvider = getProvider()
        val task = downloadRepository.getTaskById(taskId) ?: return
        if (task.state != TorrentState.SELECTING_FILES) return

        val providerId = task.providerId ?: return
        val selectedFileIds = task.providerTorrentInfo?.files?.filter { it.isSelected }?.map { it.id } ?: listOf()

        try {
            val isSuccessful = activeProvider.selectFiles(providerId, selectedFileIds)
            if (isSuccessful) {
                downloadRepository.updateTaskState(taskId, TorrentState.WAITING_FOR_PROVIDER_DOWNLOAD)
                if (activeProvider.supports(ProviderFeature.PauseResume)) {
                    activeProvider.resume(providerId)
                }
                taskIdsToProcess.add(taskId)
            }
        } catch (e: Exception) {
            downloadRepository.updateTaskState(
                taskId,
                TorrentState.ERROR,
                "Failed to select files: ${e.message}"
            )
        }
    }

    suspend fun restartTask(taskId: String) {
        val task = downloadRepository.getTaskById(taskId) ?: return
        try {
            resetTorrent(task)
            taskIdsToProcess.add(task.id)
        } catch (e: Exception) {
            downloadRepository.updateTaskState(
                task.id,
                TorrentState.ERROR,
                "Failed to restart task: ${e.message}"
            )
        }
    }

    suspend fun removeTask(
        taskId: String,
        deleteFiles: Boolean = true,
        deleteTorrentFile: Boolean = true
    ) {
        val activeProvider = getProvider()
        val task = downloadRepository.getTaskById(taskId) ?: return

        localDownloadManager.removeTaskFromQueues(task)

        if (deleteFiles) {
            task.deleteFiles()
            task.removeScopedTemporaryDirectory()
            task.removeResumeData()
        }

        if (deleteTorrentFile) {
            task.removeTorrentFile()
        }

        if (task.state.ordinal < TorrentState.DELETING_FROM_PROVIDER.ordinal && task.providerId != null) {
            activeProvider.deleteTorrent(task.providerId, deleteFiles = deleteFiles)
        }

        downloadRepository.deleteTask(taskId)
    }

    suspend fun updateFileInfo(task: DownloadTask, file: DownloadFile) {
        val activeProvider = getProvider()
        val providerId = task.providerId ?: return
        val unrestrictLinkResponse = activeProvider.unrestrictLink(providerId, file.link)
        val filePath = File(
            PathFactory.getScopedTemporaryDirectory(task.name).absolutePath,
            unrestrictLinkResponse.filename.cleanedForUseAsPath()
        ).absolutePath

        downloadRepository.updateFileProgress(
            taskId = task.id,
            link = file.link,
            state = file.state,
            speed = file.speed,
            downloadedBytes = unrestrictLinkResponse.size
        )
    }

    private suspend fun resumeDownloads() {
        val activeProvider = provider ?: providerFactory.getSelectedProvider().also { provider = it }
        val tasks = downloadRepository.tasksFlow.first()
        tasks.filter { it.location == TaskLocation.PROVIDER }
            .mapNotNull { it.providerId }
            .forEach { activeProvider.restoreTorrent(it) }

        tasks.forEach { task -> taskIdsToProcess.add(task.id) }
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
        val activeProvider = getProvider()
        val task = downloadRepository.getTaskById(taskId) ?: return null
        val delayBetweenInitialProviderUpdates = if (activeProvider.isLocalProvider) 500.milliseconds else 2.seconds
        val delayBetweenProviderUpdates = if (activeProvider.isLocalProvider) 1.seconds else 5.seconds

        try {
            when (task.state) {
                TorrentState.ADDING_TO_PROVIDER -> {
                    val providerId = if (task.torrent.type == TorrentType.MAGNET) {
                        activeProvider.addMagnet(task.torrent.uri, task.name)
                    } else {
                        val inputStream = try {
                            contentResolver.openInputStream(task.torrent.uri.toUri())
                        } catch (_: Exception) {
                            contentResolver.openInputStream(task.torrent.uri.asFile().toUri())
                        }
                        inputStream?.use {
                            activeProvider.addTorrent(it.readBytes(), task.name)
                        } ?: throw Exception("Could not open torrent file")
                    }
                    downloadRepository.updateTaskStateAndProviderId(
                        id = task.id,
                        state = TorrentState.WAITING_FOR_FILE_SELECTION,
                        providerId = providerId
                    )

                    return task.id
                }

                TorrentState.WAITING_FOR_FILE_SELECTION -> {
                    require(task.providerId != null) { "Missing provider id" }
                    val torrentInfo = activeProvider.getTorrentInfo(task.providerId)
                    updateTaskWithTorrentInfo(task.id, torrentInfo)

                    if (torrentInfo.state == ProviderTorrentState.ERROR) {
                        handleFailedProviderTask(task)
                        return task.id
                    }

                    if (
                        torrentInfo.files.isNotEmpty() &&
                        (torrentInfo.state == ProviderTorrentState.WAITING_FOR_FILE_SELECTION ||
                                torrentInfo.state == ProviderTorrentState.DOWNLOADING ||
                                torrentInfo.state == ProviderTorrentState.COMPLETED)
                    ) {
                        downloadRepository.updateTaskState(task.id, TorrentState.SELECTING_FILES)
                    } else {
                        delay(delayBetweenInitialProviderUpdates)
                    }

                    return task.id
                }

                TorrentState.SELECTING_FILES -> {
                    require(task.providerId != null) { "Missing provider id" }
                    val torrentInfo = activeProvider.getTorrentInfo(task.providerId)
                    updateTaskWithTorrentInfo(task.id, torrentInfo)

                    if (task.fileSelectionMode == FileSelectionMode.MANUAL) {
                        if (activeProvider.supports(ProviderFeature.PauseResume)) {
                            activeProvider.pause(task.providerId)
                        }
                        val info = task.providerTorrentInfo?.copy(
                            state = ProviderTorrentState.WAITING_FOR_FILE_SELECTION,
                            status = "waiting_for_file_selection",
                            downloadSpeed = 0,
                            uploadSpeed = 0
                        )
                        if (info != null) {
                            downloadRepository.updateProviderInfo(task.id, info)
                        }

                        return null
                    }

                    val fileIdsToSelect = if (task.fileSelectionMode == FileSelectionMode.BIGGEST) {
                        val biggestFile = torrentInfo.files.maxBy { it.size }
                        listOf(biggestFile.id)
                    } else {
                        torrentInfo.files.map { it.id }
                    }
                    try {
                        val isSuccessful = activeProvider.selectFiles(task.providerId, fileIdsToSelect)
                        if (isSuccessful) {
                            downloadRepository.updateTaskState(task.id, TorrentState.WAITING_FOR_PROVIDER_DOWNLOAD)
                            return task.id
                        }
                    } catch (e: Exception) {
                        downloadRepository.updateTaskState(
                            task.id,
                            TorrentState.ERROR,
                            "Failed to select files: ${e.message}"
                        )
                    }
                }

                TorrentState.WAITING_FOR_PROVIDER_DOWNLOAD -> {
                    require(task.providerId != null) { "Missing provider id" }
                    val torrentInfo = activeProvider.getTorrentInfo(task.providerId)
                    updateTaskWithTorrentInfo(task.id, torrentInfo)

                    if (torrentInfo.state == ProviderTorrentState.ERROR) {
                        handleFailedProviderTask(task)
                        return task.id
                    }

                    if (torrentInfo.state == ProviderTorrentState.COMPLETED) {
                        val files = torrentInfo.links.map { DownloadFile(it) }
                        downloadRepository.insertFiles(task.id, files)
                        downloadRepository.updateTaskState(task.id, TorrentState.POPULATING_FILE_INFOS)
                    } else {
                        delay(delayBetweenProviderUpdates)
                    }

                    return task.id
                }

                TorrentState.POPULATING_FILE_INFOS -> {
                    for (file in task.files.filter { it.filePath == null || it.unrestrictedLink == null }) {
                        updateFileInfo(task, file)
                    }
                    val updatedTask = downloadRepository.getTaskById(task.id) ?: return null

                    downloadRepository.updateTaskState(task.id, TorrentState.DOWNLOADING_LOCALLY)

                    return task.id
                }

                TorrentState.DOWNLOADING_LOCALLY -> {
                    task.files.filter { it.state != LocalDownloadState.COMPLETED && it.state != LocalDownloadState.PAUSED }.forEach {
                        localDownloadManager.enqueueDownload(DownloadWork(task.id, it))
                    }

                    var files = task.files
                    while (files.any { it.state != LocalDownloadState.COMPLETED }) {
                        delay(2.seconds)
                        files = downloadRepository.getTaskById(task.id)?.files ?: return null
                    }

                    downloadRepository.updateTaskState(task.id, TorrentState.DELETING_FROM_PROVIDER)

                    return task.id
                }

                TorrentState.DELETING_FROM_PROVIDER -> {
                    require(task.providerId != null) { "Missing provider id" }
                    val isTorrentDeleted = activeProvider.deleteTorrent(task.providerId)
                    if (isTorrentDeleted) {
                        task.removeTorrentFile()
                        task.removeResumeData()
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
                    val source = PathFactory.getScopedTemporaryDirectory(task.name)
                    source.flatten()

                    val destination = PathFactory.getScopedDestinationDirectory(task)
                    destination.parentFile?.createDirectoryRecursivelyIfNotExists()

                    if (destination.exists()) {
                        source.mergeIntoDirectory(destination)
                    } else {
                        source.renameTo(destination)
                    }

                    downloadRepository.updateTaskState(task.id, TorrentState.COMPLETED)

                    return task.id
                }

                TorrentState.COMPLETED -> {
                    if (task.notifyOnCompletion) {
                        val destination = PathFactory.getScopedDestinationDirectory(task)

                        onPostNotification?.invoke(
                            "Download finished",
                            "${task.name} finished downloading",
                            destination.makeOpenFileIntent(),
                            R.drawable.check_24px
                        )
                    }
                    downloadRepository.deleteTask(taskId)

                    onTaskCompleted?.invoke(task)
                }
                else -> { }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: BackgroundServiceStartNotAllowedException) {
            throw e
        } catch (e: RateLimitExceededException) {
            e.printStackTrace()
            delay(15.seconds)
            return task.id
        } catch (e: BandwidthLimitExceededException) {
            e.printStackTrace()
            delay(1.minutes)
            return task.id
        } catch (_: ResourceNotFoundException) {
            removeTask(taskId)
        } catch (e: Exception) {
            downloadRepository.updateTaskState(task.id, TorrentState.ERROR, e.message)
            e.printStackTrace()
        }

        return null
    }

    private suspend fun handleFailedProviderTask(task: DownloadTask) {
        resetTorrent(task)
        onPostNotification?.invoke(
            "Torrent has been restarted",
            "Torrent ${task.name} encountered an error on provider and has been restarted",
            null,
            R.drawable.restart_alt_24px
        )
    }

    private suspend fun resetTorrent(task: DownloadTask) {
        val activeProvider = getProvider()
        localDownloadManager.removeTaskFromQueues(task)
        if (task.providerId != null) {
            activeProvider.deleteTorrent(task.providerId)
        }
        downloadRepository.updateTaskState(task.id, TorrentState.ADDING_TO_PROVIDER, null)
        downloadRepository.deleteProviderInfo(task.id)
        downloadRepository.deleteFiles(task.id)
    }

    private suspend fun updateTaskWithTorrentInfo(taskId: String, torrentInfo: ProviderTorrentInfo) {
        downloadRepository.updateProviderInfo(taskId, torrentInfo)
    }
}

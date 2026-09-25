package com.felixbrucker.torrenthttpdownloader

import android.content.Context
import android.content.Intent
import com.felixbrucker.torrenthttpdownloader.data.preferences.AppSettingsRepository
import com.felixbrucker.torrenthttpdownloader.data.repository.DownloadRepository
import com.felixbrucker.torrenthttpdownloader.models.DownloadFile
import com.felixbrucker.torrenthttpdownloader.models.DownloadTask
import com.felixbrucker.torrenthttpdownloader.models.LocalDownloadState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.BufferedSink
import okio.BufferedSource
import okio.buffer
import okio.sink
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext
import kotlin.time.Duration.Companion.seconds

data class DownloadWork(val taskId: String, val file: DownloadFile)

@Singleton
class LocalDownloadManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val downloadRepository: DownloadRepository,
    private val appSettingsRepository: AppSettingsRepository,
    private val httpClient: OkHttpClient
) {
    private val downloadQueue = ConcurrentLinkedQueue<DownloadWork>()
    private val inProgressWork = ConcurrentHashMap.newKeySet<DownloadWork>()
    private val activeDownloads = ConcurrentHashMap<String, Job>()
    private var scope: CoroutineScope? = null

    var onLinkExpired: (suspend (DownloadTask, DownloadFile) -> Unit)? = null
    var onPostNotification: ((String, String, Intent?, Int?) -> Unit)? = null

    fun start(externalScope: CoroutineScope) {
        scope = externalScope
        externalScope.launch(Dispatchers.IO) {
            val parallelDownloads = 2
            repeat(parallelDownloads) {
                launchWorker(externalScope)
            }
        }
    }

    private fun launchWorker(coroutineScope: CoroutineScope) = coroutineScope.launch(Dispatchers.IO) {
        while (isActive) {
            val work = downloadQueue.poll()
            if (work == null) {
                delay(1.seconds)
                continue
            }
            inProgressWork.add(work)
            try {
                processDownload(work, coroutineScope)
            } finally {
                inProgressWork.remove(work)
            }
        }
    }

    private suspend fun processDownload(work: DownloadWork, coroutineScope: CoroutineScope) {
        var file = work.file
        if (file.state == LocalDownloadState.COMPLETED) {
            return
        }

        try {
            if (file.unrestrictedLink == null) {
                val task = downloadRepository.getTaskById(work.taskId) ?: return
                onLinkExpired?.invoke(task, file)
                file = downloadRepository.getTaskById(work.taskId)?.files?.find { it.link == file.link } ?: return
            }
            val downloadUrl = file.unrestrictedLink ?: return

            val job = coroutineScope.launch(Dispatchers.IO) {
                performDownload(work, downloadUrl, file)
            }
            activeDownloads[file.link] = job
            job.join()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            updateFileState(
                work.taskId,
                work.file.link,
                LocalDownloadState.ERROR,
                "Error: ${e.message}"
            )
        } finally {
            activeDownloads.remove(file.link)
        }
    }

    private suspend fun performDownload(work: DownloadWork, unrestrictedLink: String, downloadFile: DownloadFile) {
        val taskId = work.taskId
        val filePath = downloadFile.filePath ?: return
        val destFile = File(filePath)

        destFile.parentFile?.createDirectoryRecursivelyIfNotExists()

        val existingBytes = if (destFile.exists()) destFile.length() else 0L

        val request = Request.Builder()
            .url(unrestrictedLink)
            .apply {
                if (existingBytes > 0) {
                    header("Range", "bytes=$existingBytes-")
                }
            }
            .build()

        try {
            val call = httpClient.newCall(request)

            val currentJob = coroutineContext[Job]
            currentJob?.invokeOnCompletion {
                call.cancel()
            }

            withContext(Dispatchers.IO) {
                call.execute().use { response ->
                    if (!response.isSuccessful && response.code != 206) {
                        handleFailedLocalDownload(work, downloadFile, "HTTP ${response.code}")
                        return@withContext
                    }

                    val body = response.body
                    val contentLength = body?.contentLength() ?: 0L
                    val totalBytes = if (response.code == 206) contentLength + existingBytes else contentLength

                    downloadRepository.updateFileProgress(
                        taskId = taskId,
                        link = downloadFile.link,
                        state = LocalDownloadState.DOWNLOADING,
                        progress = 0,
                        speed = 0,
                        downloadedBytes = existingBytes
                    )

                    val sink: BufferedSink = if (existingBytes > 0) destFile.sink(append = true).buffer() else destFile.sink().buffer()
                    val source: BufferedSource = body!!.source()
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalDownloaded = existingBytes
                    var lastUpdate = System.currentTimeMillis()
                    var bytesSinceLastUpdate = 0L

                    sink.use { bufferedSink ->
                        while (source.read(buffer).also { bytesRead = it } != -1) {
                            if (!isActive) {
                                return@withContext
                            }
                            bufferedSink.write(buffer, 0, bytesRead)
                            totalDownloaded += bytesRead
                            bytesSinceLastUpdate += bytesRead

                            val now = System.currentTimeMillis()
                            if (now - lastUpdate > 1000) {
                                val speed = (bytesSinceLastUpdate * 1000) / (now - lastUpdate)
                                val progress = if (totalBytes > 0) ((totalDownloaded * 100L) / totalBytes).toInt() else 0

                                downloadRepository.updateFileProgress(
                                    taskId = taskId,
                                    link = downloadFile.link,
                                    state = LocalDownloadState.DOWNLOADING,
                                    progress = progress,
                                    speed = speed,
                                    downloadedBytes = totalDownloaded
                                )
                                lastUpdate = now
                                bytesSinceLastUpdate = 0
                            }
                        }
                    }
                    updateFileState(taskId, downloadFile.link, LocalDownloadState.COMPLETED)
                }
            }
        } catch (e: Exception) {
            if (e !is CancellationException) {
                handleFailedLocalDownload(work, downloadFile, e.message ?: "unknown error")
            }
        }
    }

    private fun handleFailedLocalDownload(work: DownloadWork, downloadFile: DownloadFile, errorMessage: String) {
        updateFileState(work.taskId, downloadFile.link, LocalDownloadState.ERROR, "Download failed: $errorMessage")
        onPostNotification?.invoke(
            "Torrent download encountered an error",
            "Torrent file ${downloadFile.fileName} encountered an error while downloading: $errorMessage",
            null,
            R.drawable.error_24px
        )
        scope?.launch(Dispatchers.IO) {
            delay(5.seconds)
            enqueueDownload(work.copy(file = downloadFile))
        }
    }

    private fun updateFileState(taskId: String, fileLink: String, state: LocalDownloadState, error: String? = null) {
        scope?.launch(Dispatchers.IO) {
            val task = downloadRepository.getTaskById(taskId)
            val file = task?.files?.find { it.link == fileLink }
            val downloadedBytes = file?.downloadedBytes ?: 0L
            val totalBytes = file?.totalBytes ?: 0L
            val progress = if (state == LocalDownloadState.COMPLETED) 100 else (file?.progress ?: 0)
            downloadRepository.updateFileProgress(
                taskId = taskId,
                link = fileLink,
                state = state,
                progress = progress,
                speed = 0,
                downloadedBytes = if (state == LocalDownloadState.COMPLETED && totalBytes > 0) totalBytes else downloadedBytes
            )
        }
    }

    fun enqueueDownload(work: DownloadWork) {
        if (!isWorkEnqueued(work.taskId, work.file.link)) {
            updateFileState(work.taskId, work.file.link, LocalDownloadState.PENDING)
            downloadQueue.add(work)
        }
    }

    private fun isWorkEnqueued(taskId: String, fileLink: String): Boolean {
        return downloadQueue.any { it.taskId == taskId && it.file.link == fileLink } ||
                inProgressWork.any { it.taskId == taskId && it.file.link == fileLink }
    }

    fun pauseFile(taskId: String, fileLink: String) {
        activeDownloads[fileLink]?.cancel()
        activeDownloads.remove(fileLink)
        downloadQueue.removeIf { it.taskId == taskId && it.file.link == fileLink }
        updateFileState(taskId, fileLink, LocalDownloadState.PAUSED)
    }

    fun resumeFile(taskId: String, fileLink: String) {
        scope?.launch(Dispatchers.IO) {
            val task = downloadRepository.getTaskById(taskId) ?: return@launch
            val file = task.files.find { it.link == fileLink } ?: return@launch
            enqueueDownload(DownloadWork(taskId, file))
        }
    }

    fun pauseTask(taskId: String) {
        scope?.launch(Dispatchers.IO) {
            val task = downloadRepository.getTaskById(taskId) ?: return@launch
            task.files.forEach { file ->
                if (file.state == LocalDownloadState.DOWNLOADING || file.state == LocalDownloadState.PENDING) {
                    pauseFile(taskId, file.link)
                }
            }
        }
    }

    fun resumeTask(taskId: String) {
        scope?.launch(Dispatchers.IO) {
            val task = downloadRepository.getTaskById(taskId) ?: return@launch
            task.files.forEach { file ->
                if (file.state == LocalDownloadState.PAUSED) {
                    resumeFile(taskId, file.link)
                }
            }
        }
    }

    fun pauseAll() {
        scope?.launch(Dispatchers.IO) {
            downloadRepository.tasksFlow.first().forEach { task ->
                pauseTask(task.id)
            }
        }
    }

    fun resumeAll() {
        scope?.launch(Dispatchers.IO) {
            downloadRepository.tasksFlow.first().forEach { task ->
                resumeTask(task.id)
            }
        }
    }

    fun removeTaskFromQueues(task: DownloadTask) {
        downloadQueue.removeIf { it.taskId == task.id }
        for (file in task.files) {
            activeDownloads[file.link]?.cancel()
            activeDownloads.remove(file.link)
        }
    }

    fun stop() {
        downloadQueue.clear()
        activeDownloads.values.forEach { it.cancel() }
        activeDownloads.clear()
        inProgressWork.clear()
    }
}

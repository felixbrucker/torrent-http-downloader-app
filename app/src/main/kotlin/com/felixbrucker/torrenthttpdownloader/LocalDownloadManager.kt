package com.felixbrucker.torrenthttpdownloader

import android.content.SharedPreferences
import com.felixbrucker.torrenthttpdownloader.models.DownloadFile
import com.felixbrucker.torrenthttpdownloader.models.DownloadTask
import com.felixbrucker.torrenthttpdownloader.models.LocalDownloadState
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.BufferedSink
import okio.BufferedSource
import okio.buffer
import okio.sink
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.*
import java.util.concurrent.CancellationException
import java.util.concurrent.TimeUnit

data class DownloadWork(val taskId: String, val file: DownloadFile)

class LocalDownloadManager(
    private val scope: CoroutineScope,
    private val sharedPreferences: SharedPreferences,
    private val onLinkExpired: suspend (DownloadTask, DownloadFile) -> Unit,
    private val onPostNotification: (String, String) -> Unit,
) {
    private val downloadQueue = ConcurrentLinkedQueue<DownloadWork>()
    private val inProgressWork = mutableListOf<DownloadWork>()
    private val activeDownloads = ConcurrentHashMap<String, Job>()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    fun start() {
        val parallelDownloads = sharedPreferences.getInt("local_parallel_downloads", 2)
        repeat(parallelDownloads) {
            launchWorker()
        }
    }

    private fun launchWorker() = scope.launch(Dispatchers.IO) {
        while (isActive) {
            val work = downloadQueue.poll()
            if (work == null) {
                delay(1.seconds)
                continue
            }
            inProgressWork.add(work)
            try {
                processDownload(work)
            } finally {
                inProgressWork.remove(work)
            }
        }
    }

    private suspend fun processDownload(work: DownloadWork) {
        var file = work.file
        // Should not happen, we enqueued an already completed file
        if (file.state == LocalDownloadState.COMPLETED) {
            return
        }

        try {
            // Support regenerating the link if the existing one expires (
            // TODO: how to detect expired links?
            if (file.unrestrictedLink == null) {
                val task = DownloadTracker.findTask(work.taskId) ?: return
                onLinkExpired(task, file)
                file = DownloadTracker.findTask(work.taskId)?.files?.find { it.link == file.link } ?: return
            }
            val downloadUrl = file.unrestrictedLink ?: return

            val job = scope.launch(Dispatchers.IO) {
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

        // Ensure we can create the file
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

            // Link coroutine cancellation to OkHttp call cancellation
            val currentJob = currentCoroutineContext()[Job]
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
                    val contentLength = body.contentLength()
                    val totalBytes = if (response.code == 206) contentLength + existingBytes else contentLength

                    DownloadTracker.updateTaskFile(taskId, downloadFile.link) { file ->
                        file.copy(
                            state = LocalDownloadState.DOWNLOADING,
                            totalBytes = totalBytes,
                            downloadedBytes = existingBytes
                        )
                    }

                    val sink: BufferedSink = if (existingBytes > 0) destFile.sink(append = true).buffer() else destFile.sink().buffer()
                    val source: BufferedSource = body.source()
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

                                DownloadTracker.updateTaskFile(taskId, downloadFile.link) { file ->
                                    file.copy(
                                        progress = progress,
                                        downloadedBytes = totalDownloaded,
                                        speed = speed,
                                        lastTimestamp = now,
                                        lastBytes = totalDownloaded
                                    )
                                }
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
        onPostNotification(
            "Torrent download encountered an error",
            "Torrent file ${downloadFile.fileName} encountered an error while downloading: $errorMessage"
        )
        scope.launch(Dispatchers.IO) {
            delay(5.seconds)
            enqueueDownload(work.copy(file = downloadFile))
        }
    }

    private fun updateFileState(taskId: String, fileLink: String, state: LocalDownloadState, error: String? = null) {
        DownloadTracker.updateTaskFile(taskId, fileLink) { file ->
            file.copy(state = state, stateDescription = error, speed = 0)
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
        val task = DownloadTracker.findTask(taskId) ?: return
        val file = task.files.find { it.link == fileLink } ?: return
        enqueueDownload(DownloadWork(taskId, file))
    }

    fun pauseTask(taskId: String) {
        val task = DownloadTracker.findTask(taskId) ?: return
        task.files.forEach { file ->
            if (file.state == LocalDownloadState.DOWNLOADING || file.state == LocalDownloadState.PENDING) {
                pauseFile(taskId, file.link)
            }
        }
    }

    fun resumeTask(taskId: String) {
        val task = DownloadTracker.findTask(taskId) ?: return
        task.files.forEach { file ->
            if (file.state == LocalDownloadState.PAUSED) {
                resumeFile(taskId, file.link)
            }
        }
    }

    fun pauseAll() {
        DownloadTracker.getTasks().forEach { task ->
            pauseTask(task.id)
        }
    }

    fun resumeAll() {
        DownloadTracker.getTasks().forEach { task ->
            resumeTask(task.id)
        }
    }

    fun removeTaskFromQueues(task: DownloadTask) {
        // Remove pending local downloads
        downloadQueue.removeIf { it.taskId == task.id }

        // Cancel active downloads
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

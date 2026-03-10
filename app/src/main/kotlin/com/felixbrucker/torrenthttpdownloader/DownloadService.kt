package com.felixbrucker.torrenthttpdownloader

import android.app.BackgroundServiceStartNotAllowedException
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Environment
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import com.felixbrucker.torrenthttpdownloader.models.*
import com.felixbrucker.torrenthttpdownloader.network.ResourceNotFoundException
import com.felixbrucker.torrenthttpdownloader.network.RetrofitClient
import com.felixbrucker.torrenthttpdownloader.network.TorrentInfo
import com.github.junrar.Junrar
import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import okio.BufferedSource
import okio.buffer
import okio.sink
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.min

private data class DownloadWork(val taskId: String, val file: DownloadFile, val apiToken: String)

class DownloadService : Service() {

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private val downloadQueue = ConcurrentLinkedQueue<DownloadWork>()
    private val inProgressWork: MutableList<DownloadWork> = mutableListOf()
    private val processingTasks: MutableSet<String> = mutableSetOf()
    private val activeDownloads = ConcurrentHashMap<String, Job>()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS) // No timeout for downloads
        .build()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val sharedPreferences = getSharedPreferences("settings", MODE_PRIVATE)
        val limit = sharedPreferences.getInt("parallel_downloads", 2)
        repeat(limit) {
            launchWorker()
        }
        resumeDownloadsAfterDelay()
    }

    private fun createNotificationChannel() {
        val name = "Download Service"
        val descriptionText = "Notifications for background downloads"
        val importance = NotificationManager.IMPORTANCE_LOW
        val channel = NotificationChannel(NOTIFICATION_CHANNEL_ID, name, importance).apply {
            description = descriptionText
        }
        val notificationManager: NotificationManager =
            getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    private fun getNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Torrent HTTP Downloader")
            .setContentText("Downloading in background...")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun startForegroundService() {
        startForeground(
            NOTIFICATION_ID,
            getNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )
    }

    private fun launchWorker() = serviceScope.launch {
        while (isActive) {
            val work = downloadQueue.poll()
            if (work == null) {
                delay(1000) // Wait before polling again
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
        val task = DownloadTracker.findTask(work.taskId) ?: return

        try {
            val unrestrictLinkResponse = RetrofitClient.instance.unrestrictLink("Bearer ${work.apiToken}", file.link)
            val filePath = File(
                getScopedTemporaryDirectory(task.name).absolutePath,
                unrestrictLinkResponse.filename
            ).absolutePath

            DownloadTracker.updateTask(work.taskId) { t ->
                t.copy(files = t.files.map {
                    if (it.link == file.link) {
                        it.copy(filePath = filePath)
                    } else {
                        it
                    }
                })
            }
            file = DownloadTracker.findTask(work.taskId)?.files?.find { it.link == file.link } ?: return

            val job = serviceScope.launch {
                performDownload(work.taskId, unrestrictLinkResponse.download, file)
            }
            activeDownloads[file.link] = job
            job.join()
        } catch (e: CancellationException) {
            throw e // Let the coroutine be cancelled
        } catch (e: Exception) {
            DownloadTracker.updateTask(work.taskId) { task ->
                task.copy(files = task.files.map {
                    if (it.link == work.file.link) {
                        it.copy(
                            state = LocalDownloadState.ERROR,
                            stateDescription = "Error: ${e.message}",
                            speed = 0,
                        )
                    } else {
                        it
                    }
                })
            }
        } finally {
            activeDownloads.remove(file.link)
        }
    }

    private suspend fun performDownload(taskId: String, unrestrictedLink: String, downloadFile: DownloadFile) {
        val filePath = downloadFile.filePath ?: return
        val destFile = File(filePath)

        // Ensure we can create the file
        if (destFile.parentFile?.exists() == false) {
            destFile.parentFile?.mkdirs()
        }

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
                        updateFileState(taskId, downloadFile.link, LocalDownloadState.ERROR, "HTTP ${response.code}")
                        return@withContext
                    }

                    val body = response.body
                    val contentLength = body.contentLength()
                    val totalBytes = if (response.code == 206) contentLength + existingBytes else contentLength

                    DownloadTracker.updateTask(taskId) { task ->
                        task.copy(files = task.files.map {
                            if (it.link == downloadFile.link) {
                                it.copy(state = LocalDownloadState.DOWNLOADING, totalBytes = totalBytes, downloadedBytes = existingBytes)
                            } else it
                        })
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

                                DownloadTracker.updateTask(taskId) { task ->
                                    task.copy(files = task.files.map {
                                        if (it.link == downloadFile.link) {
                                            it.copy(
                                                progress = progress,
                                                downloadedBytes = totalDownloaded,
                                                speed = speed,
                                                lastTimestamp = now,
                                                lastBytes = totalDownloaded
                                            )
                                        } else it
                                    })
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
                updateFileState(taskId, downloadFile.link, LocalDownloadState.ERROR, e.message)
            }
        }
    }

    private fun updateFileState(taskId: String, fileLink: String, state: LocalDownloadState, error: String? = null) {
        DownloadTracker.updateTask(taskId) { task ->
            task.copy(files = task.files.map {
                if (it.link == fileLink) {
                    it.copy(state = state, stateDescription = error, speed = 0)
                } else it
            })
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundService()

        when (intent?.action) {
            ACTION_PROCESS_TASK -> serviceScope.launch { processTaskSafely(intent.getStringExtra(EXTRA_TASK_ID)) }
            ACTION_REMOVE_TASK -> serviceScope.launch { removeTask(intent.getStringExtra(EXTRA_TASK_ID)) }
            ACTION_PAUSE_FILE -> pauseFile(intent.getStringExtra(EXTRA_TASK_ID), intent.getStringExtra(EXTRA_FILE_LINK))
            ACTION_RESUME_FILE -> resumeFile(intent.getStringExtra(EXTRA_TASK_ID), intent.getStringExtra(EXTRA_FILE_LINK))
            ACTION_ADD_TASK -> handleAddTask(intent)
        }

        return START_STICKY
    }

    private fun pauseFile(taskId: String?, fileLink: String?) {
        if (taskId == null || fileLink == null) return
        activeDownloads[fileLink]?.cancel()
        activeDownloads.remove(fileLink)
        downloadQueue.removeIf { it.taskId == taskId && it.file.link == fileLink }
        updateFileState(taskId, fileLink, LocalDownloadState.PAUSED)
    }

    private fun resumeFile(taskId: String?, fileLink: String?) {
        if (taskId == null || fileLink == null) return
        val task = DownloadTracker.findTask(taskId) ?: return
        val file = task.files.find { it.link == fileLink } ?: return
        val sharedPreferences = getSharedPreferences("settings", MODE_PRIVATE)
        val apiToken = sharedPreferences.getString("api_token", "") ?: return
        enqueueDownload(DownloadWork(taskId, file, apiToken))
    }

    private fun handleAddTask(intent: Intent) {
        val path = intent.getStringExtra(EXTRA_TORRENT_PATH) ?: return
        val type = TorrentType.valueOf(intent.getStringExtra(EXTRA_TORRENT_TYPE) ?: TorrentType.MAGNET.name)
        val destinationSubdirectory = intent.getStringExtra(EXTRA_DESTINATION_SUBDIRECTORY)
        val createSubfolderByName = intent.getBooleanExtra(EXTRA_CREATE_SUBFOLDER_BY_NAME, true)
        val torrentName = intent.getStringExtra(EXTRA_TORRENT_NAME)

        if (DownloadTracker.getTasks().any { it.torrent.path == path }) return

        val task = DownloadTask(
            id = path,
            name = torrentName ?: path,
            torrent = TorrentDescriptor(type, path),
            destinationSubdirectory = destinationSubdirectory,
            createSubfolderByName = createSubfolderByName,
            state = TorrentState.ADDING_TO_REAL_DEBRID
        )
        DownloadTracker.addTask(task)
        continueProcessing(task.id)
    }

    private suspend fun removeTask(taskId: String?) {
        if (taskId == null) return
        val task = DownloadTracker.findTask(taskId) ?: return

        val sharedPreferences = getSharedPreferences("settings", MODE_PRIVATE)
        val apiToken = sharedPreferences.getString("api_token", "") ?: ""

        // Remove pending local downloads
        downloadQueue.removeIf { it.taskId == taskId }

        // Cancel active downloads
        for (file in task.files) {
            activeDownloads[file.link]?.cancel()
            activeDownloads.remove(file.link)
        }

        // Explicitly make sure to delete all files
        for (filePath in task.files.mapNotNull { file -> file.filePath }) {
            val fileRef = File(filePath)
            if (fileRef.exists()) {
                fileRef.delete()
            }
        }

        // Remove scoped temp directory if available
        val tempDir = getScopedTemporaryDirectory(task.name)
        if (tempDir.exists()) {
            tempDir.deleteRecursively()
        }

        // If the task is on Real-Debrid, delete it there
        if (task.state.ordinal < TorrentState.DELETING_FROM_REAL_DEBRID.ordinal && apiToken.isNotEmpty()) {
            try {
                RetrofitClient.instance.deleteTorrent("Bearer $apiToken", task.id)
            } catch (_: Exception) {
                // Ignore if already deleted
            }
        }

        DownloadTracker.removeTask(taskId)
    }

    private suspend fun processTaskSafely(taskId: String?) {
        if (taskId == null) return
        if (processingTasks.contains(taskId)) return
        processingTasks.add(taskId)
        var newId: String?
        try {
            newId = processTask(taskId)
        } finally {
            processingTasks.remove(taskId)
        }
        if (newId != null) {
            continueProcessing(newId)
        }
    }

    private suspend fun processTask(taskId: String): String? {
        val task = DownloadTracker.findTask(taskId) ?: return null

        val sharedPreferences = getSharedPreferences("settings", MODE_PRIVATE)
        val apiToken = sharedPreferences.getString("api_token", "") ?: ""
        if (apiToken.isEmpty()) {
            DownloadTracker.updateTask(task.id) { it.copy(state = TorrentState.ERROR, errorMessage = "API Token not set") }

            return null
        }

        try {
            when (task.state) {
                TorrentState.ADDING_TO_REAL_DEBRID -> {
                    val addMagnetResponse = if (task.torrent.type == TorrentType.MAGNET) {
                        RetrofitClient.instance.addMagnet("Bearer $apiToken", task.torrent.path)
                    } else {
                        contentResolver.openInputStream(task.torrent.path.toUri())?.use {
                            val torrentData = it.readBytes()
                            val requestBody = torrentData.toRequestBody(
                                "application/x-bittorrent".toMediaTypeOrNull(),
                                0,
                                torrentData.size
                            )
                            RetrofitClient.instance.addTorrentFile("Bearer $apiToken", requestBody)
                        }!!
                    }
                    val newId = addMagnetResponse.id
                    DownloadTracker.replaceTask(task.id, task.copy(id = newId, state = TorrentState.WAITING_FOR_FILE_SELECTION))

                    return newId
                }

                TorrentState.WAITING_FOR_FILE_SELECTION -> {
                    val torrentInfo = RetrofitClient.instance.getTorrentInfo("Bearer $apiToken", task.id)
                    updateTaskWithTorrentInfo(task.id, torrentInfo)

                    if (torrentInfo.status == "waiting_files_selection") {
                        DownloadTracker.updateTask(task.id) {
                            it.copy(
                                state = TorrentState.SELECTING_FILES,
                                rdProgress = 0,
                                rdSpeed = 0,
                                rdDownloadedBytes = 0,
                                rdTotalBytes = 0,
                            )
                        }
                    } else {
                        // Still processing, check again later
                        delay(2000)
                    }

                    return task.id
                }

                TorrentState.SELECTING_FILES -> {
                    val response = RetrofitClient.instance.selectFiles("Bearer $apiToken", task.id, "all")
                    if (response.isSuccessful) {
                        DownloadTracker.updateTask(task.id) { it.copy(state = TorrentState.WAITING_FOR_REAL_DEBRID_DOWNLOAD) }

                        return task.id
                    } else {
                        DownloadTracker.updateTask(task.id) { it.copy(state = TorrentState.ERROR, errorMessage = "Failed to select files") }
                    }
                }

                TorrentState.WAITING_FOR_REAL_DEBRID_DOWNLOAD -> {
                    val torrentInfo = RetrofitClient.instance.getTorrentInfo("Bearer $apiToken", task.id)
                    updateTaskWithTorrentInfo(task.id, torrentInfo)

                    if (torrentInfo.status == "downloaded") {
                        val files = torrentInfo.links.map { DownloadFile(it) }
                        DownloadTracker.updateTask(task.id) {
                            it.copy(
                                files = files,
                                state = TorrentState.ENQUEUING_LOCAL_DOWNLOADS,
                                rdSpeed = 0,
                            )
                        }
                    } else {
                        // Still downloading on RD, check again later
                        delay(5000)
                    }

                    return task.id
                }

                TorrentState.ENQUEUING_LOCAL_DOWNLOADS -> {
                    task.files.filter { it.state != LocalDownloadState.COMPLETED }.forEach {
                        enqueueDownload(DownloadWork(task.id, it, apiToken))
                    }

                    // Once downloads have been queued, change state to wait for all remaining ones
                    DownloadTracker.updateTask(task.id) { it.copy(state = TorrentState.WAITING_FOR_LOCAL_DOWNLOADS) }

                    return task.id
                }

                TorrentState.WAITING_FOR_LOCAL_DOWNLOADS -> {
                    // All done, continue to next state
                    if (task.files.all { it.state == LocalDownloadState.COMPLETED }) {
                        DownloadTracker.updateTask(task.id) { it.copy(state = TorrentState.DELETING_FROM_REAL_DEBRID) }
                    } else {
                        delay(2000)
                    }

                    return task.id
                }

                TorrentState.DELETING_FROM_REAL_DEBRID -> {
                    val response = RetrofitClient.instance.deleteTorrent("Bearer $apiToken", task.id)
                    if (response.isSuccessful || response.code() == 404) {
                        DownloadTracker.updateTask(task.id) { it.copy(state = TorrentState.CHECKING_FOR_ARCHIVES) }
                    } else {
                        delay(5000)
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
                    val rarFile = task.files.firstOrNull { it.filePath?.endsWith(".rar", true) == true }
                    if (rarFile?.filePath != null) {
                        extractFile(rarFile.filePath).join()
                        val rarFileDescriptor = File(rarFile.filePath)
                        if (rarFileDescriptor.exists() && rarFileDescriptor.isFile) {
                            rarFileDescriptor.delete()
                        }
                    }
                    DownloadTracker.updateTask(task.id) { it.copy(state = TorrentState.MOVING_TO_DESTINATION) }

                    return task.id
                }

                TorrentState.MOVING_TO_DESTINATION -> {
                    val source = getScopedTemporaryDirectory(task.name)
                    val destination = getScopedDestinationDirectory(task)

                    val parentDestinationDir = destination.parentFile
                    if (parentDestinationDir != null && !parentDestinationDir.exists()) {
                        parentDestinationDir.mkdirs()
                    }

                    if (destination.exists()) {
                        // Directory merge, move files individually
                        source.listFiles()?.forEach { file ->
                            val destFile = File(destination, file.name)
                            file.renameTo(destFile)
                        }
                        source.deleteRecursively()
                    } else {
                        // No conflict, just move the whole directory
                        source.renameTo(destination)
                    }

                    DownloadTracker.updateTask(task.id) { it.copy(state = TorrentState.COMPLETED) }

                    return task.id
                }

                TorrentState.COMPLETED -> {
                    DownloadTracker.removeTask(taskId)

                    if (downloadQueue.isEmpty() && inProgressWork.isEmpty() && activeDownloads.isEmpty()) {
                        stopSelf()
                    }
                }
                else -> { /* No action needed for COMPLETED or ERROR */ }
            }
        } catch (e: CancellationException) {
            throw e // Let the coroutine be cancelled
        } catch (e: BackgroundServiceStartNotAllowedException) {
            throw e // Ignore
        } catch (_: ResourceNotFoundException) {
            removeTask(taskId)
        } catch (e: Exception) {
            DownloadTracker.updateTask(task.id) { it.copy(state = TorrentState.ERROR, errorMessage = e.message) }
            e.printStackTrace()
        }

        return null
    }

    private fun continueProcessing(taskId: String) {
        val intent = Intent(this, DownloadService::class.java).apply {
            action = ACTION_PROCESS_TASK
            putExtra(EXTRA_TASK_ID, taskId)
        }
        startService(intent)
    }

    private fun extractFile(filePath: String): Job {
        return serviceScope.launch {
            try {
                val file = File(filePath)
                val destination = file.parentFile
                Junrar.extract(file, destination)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun resumeDownloadsAfterDelay() {
        serviceScope.launch {
            delay(1000)
            val sharedPreferences = getSharedPreferences("settings", MODE_PRIVATE)
            val apiToken = sharedPreferences.getString("api_token", "") ?: ""

            for (task in DownloadTracker.getTasks()) {
                if (task.state == TorrentState.WAITING_FOR_LOCAL_DOWNLOADS) {
                    // Refill the download queue with tasks that are not yet completed and not yet in the queue
                    task.files.filter { it.state != LocalDownloadState.COMPLETED }.forEach { file ->
                        val notInQueueYet = downloadQueue.none { work ->  work.taskId == task.id && work.file.link == file.link }
                        val notProcessingYet = inProgressWork.none { work -> work.taskId == task.id && work.file.link == file.link }
                        if (notInQueueYet && notProcessingYet) {
                            enqueueDownload(DownloadWork(task.id, file, apiToken))
                        }
                    }
                }

                continueProcessing(task.id)
            }
        }
    }

    private fun enqueueDownload(work: DownloadWork) {
        updateFileState(work.taskId, work.file.link, LocalDownloadState.PENDING)
        downloadQueue.add(work)
    }

    private fun getScopedTemporaryDirectory(taskName: String): File {
        return File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath,
            "tmp/${taskName}"
        )
    }

    private fun getScopedDestinationDirectory(task: DownloadTask): File {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val baseDir = if (!task.destinationSubdirectory.isNullOrEmpty()) {
            File(downloadsDir, task.destinationSubdirectory)
        } else {
            downloadsDir
        }

        return if (task.createSubfolderByName) {
            File(baseDir, task.name)
        } else {
            baseDir
        }
    }

    private fun updateTaskWithTorrentInfo(taskId: String, torrentInfo: TorrentInfo) {
        val totalBytes = torrentInfo.bytes
        val downloadedBytes = min((totalBytes * (torrentInfo.progress / 100.0)).toLong(), totalBytes)

        DownloadTracker.updateTask(taskId) {
            it.copy(
                name = if (it.name == it.torrent.path) torrentInfo.filename else it.name,
                rdState = torrentInfo.status,
                rdProgress = torrentInfo.progress.toInt(),
                rdSpeed = torrentInfo.speed ?: 0,
                rdDownloadedBytes = downloadedBytes,
                rdTotalBytes = totalBytes,
            )
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
    }

    companion object {
        private const val NOTIFICATION_ID = 1
        private const val NOTIFICATION_CHANNEL_ID = "download_service"
        const val ACTION_PROCESS_TASK = "ACTION_PROCESS_TASK"
        const val ACTION_REMOVE_TASK = "ACTION_REMOVE_TASK"
        const val ACTION_PAUSE_FILE = "ACTION_PAUSE_FILE"
        const val ACTION_RESUME_FILE = "ACTION_RESUME_FILE"
        const val ACTION_ADD_TASK = "ACTION_ADD_TASK"
        const val EXTRA_TASK_ID = "EXTRA_TASK_ID"
        const val EXTRA_FILE_LINK = "EXTRA_FILE_LINK"
        const val EXTRA_TORRENT_PATH = "EXTRA_TORRENT_PATH"
        const val EXTRA_TORRENT_TYPE = "EXTRA_TORRENT_TYPE"
        const val EXTRA_DESTINATION_SUBDIRECTORY = "EXTRA_DESTINATION_SUBDIRECTORY"
        const val EXTRA_CREATE_SUBFOLDER_BY_NAME = "EXTRA_CREATE_SUBFOLDER_BY_NAME"
        const val EXTRA_TORRENT_NAME = "EXTRA_TORRENT_NAME"
    }
}

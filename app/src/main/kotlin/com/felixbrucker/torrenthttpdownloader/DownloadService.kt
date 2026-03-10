package com.felixbrucker.torrenthttpdownloader

import android.app.BackgroundServiceStartNotAllowedException
import android.app.DownloadManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.database.Cursor
import android.os.Build
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
import okhttp3.MediaType
import okhttp3.RequestBody
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.min

private data class DownloadWork(val taskId: String, val file: DownloadFile, val apiToken: String)

class DownloadService : Service() {

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private val downloadQueue = ConcurrentLinkedQueue<DownloadWork>()
    private val inProgressWork: MutableList<DownloadWork> = mutableListOf()
    private val processingTasks: MutableSet<String> = mutableSetOf()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val sharedPreferences = getSharedPreferences("settings", MODE_PRIVATE)
        val limit = sharedPreferences.getInt("parallel_downloads", 2)
        repeat(limit) {
            launchWorker()
        }
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
        var downloadManagerId = work.file.downloadManagerId
        try {
            // Not yet added to the download manager, generate link and add
            if (downloadManagerId == null) {
                val task = DownloadTracker.findTask(work.taskId) ?: return
                val unrestrictLinkResponse = RetrofitClient.instance.unrestrictLink("Bearer ${work.apiToken}", work.file.link)
                downloadManagerId = enqueueDownload(unrestrictLinkResponse.download, unrestrictLinkResponse.filename, task.name)
                val filePath = File(
                    getScopedTemporaryDirectory(task.name).absolutePath,
                    unrestrictLinkResponse.filename
                ).absolutePath

                DownloadTracker.updateTask(work.taskId) { task ->
                    task.copy(files = task.files.map {
                        if (it.link == work.file.link) {
                            it.copy(downloadManagerId = downloadManagerId, filePath = filePath, state = LocalDownloadState.PENDING)
                        } else {
                            it
                        }
                    })
                }
            }
            monitorDownload(work.taskId, work.file.link, downloadManagerId)
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
        }
    }

    private suspend fun monitorDownload(taskId: String, fileLink: String, downloadManagerId: Long) {
        val downloadManager = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
        var isDownloading = true

        while (isDownloading) {
            val file = DownloadTracker.findTask(taskId)?.files?.find { it.link == fileLink } ?: return

            val query = DownloadManager.Query().setFilterById(downloadManagerId)
            val cursor: Cursor = downloadManager.query(query)

            if (!cursor.moveToFirst()) {
                DownloadTracker.updateTask(taskId) { task ->
                    task.copy(files = task.files.map {
                        if (it.link == fileLink) {
                            it.copy(state = LocalDownloadState.ERROR, stateDescription = "No longer in DownloadManager", downloadManagerId = null)
                        } else it
                    })
                }
                isDownloading = false
                cursor.close()
                continue
            }

            val bytesDownloaded = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
            val totalBytes = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))

            val currentTime = System.currentTimeMillis()
            val timeDelta = (currentTime - file.lastTimestamp) / 1000.0

            val progress = if (totalBytes > 0) ((bytesDownloaded * 100L) / totalBytes).toInt() else 0
            val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))

            val newState = when (status) {
                DownloadManager.STATUS_SUCCESSFUL -> {
                    isDownloading = false
                    LocalDownloadState.COMPLETED
                }
                DownloadManager.STATUS_FAILED -> {
                    isDownloading = false
                    LocalDownloadState.ERROR
                }
                DownloadManager.STATUS_RUNNING -> LocalDownloadState.DOWNLOADING
                DownloadManager.STATUS_PENDING -> LocalDownloadState.PENDING
                DownloadManager.STATUS_PAUSED -> LocalDownloadState.PAUSED
                else -> LocalDownloadState.UNKNOWN
            }

            val speed = if (newState == LocalDownloadState.COMPLETED) {
                0
            } else if (timeDelta > 3) {
                ((bytesDownloaded - file.lastBytes) / timeDelta).toLong()
            } else {
                file.speed
            }

            DownloadTracker.updateTask(taskId) { task ->
                task.copy(files = task.files.map {
                    if (it.link == fileLink) {
                        it.copy(
                            state = newState,
                            progress = progress,
                            speed = speed,
                            totalBytes = totalBytes,
                            downloadedBytes = bytesDownloaded,
                            lastBytes = if (timeDelta > 3) bytesDownloaded else file.lastBytes,
                            lastTimestamp = if (timeDelta > 3) currentTime else file.lastTimestamp,
                            stateDescription = if(newState == LocalDownloadState.ERROR) cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON)).toString() else null
                        )
                    } else it
                })
            }

            cursor.close()

            if (isDownloading) {
                delay(1000)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundService()
        when (intent?.action) {
            ACTION_RESUME_DOWNLOADS -> resumeDownloadsAfterDelay()
            ACTION_PROCESS_TASK -> serviceScope.launch { processTaskSafely(intent.getStringExtra(EXTRA_TASK_ID)) }
            ACTION_REMOVE_TASK -> serviceScope.launch { removeTask(intent.getStringExtra(EXTRA_TASK_ID)) }
            ACTION_RETRY_FILE -> serviceScope.launch { retryFile(intent.getStringExtra(EXTRA_TASK_ID), intent.getStringExtra(EXTRA_FILE_LINK)) }
            ACTION_ADD_TASK -> handleAddTask(intent)
        }
        return START_STICKY
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

    private fun retryFile(taskId: String?, link: String?) {
        if (taskId == null || link == null) return
        val task = DownloadTracker.findTask(taskId) ?: return
        val file = task.files.find { it.link == link } ?: return

        val sharedPreferences = getSharedPreferences("settings", MODE_PRIVATE)
        val apiToken = sharedPreferences.getString("api_token", "") ?: return

        downloadQueue.add(DownloadWork(taskId, file, apiToken))
    }

    private suspend fun removeTask(taskId: String?) {
        if (taskId == null) return
        val task = DownloadTracker.findTask(taskId) ?: return

        val sharedPreferences = getSharedPreferences("settings", MODE_PRIVATE)
        val apiToken = sharedPreferences.getString("api_token", "") ?: ""

        // Remove pending local downloads
        downloadQueue.removeIf { it.taskId == taskId }

        // Remove any active or already completed local downloads
        val downloadManager = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
        for (file in task.files) {
            file.downloadManagerId?.let { downloadManager.remove(it) }
        }

        // Explicitly make sure to delete all files just in case the download manager does not clean
        // them up for some reason
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
        var task = DownloadTracker.findTask(taskId) ?: return null

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
                            val requestBody = RequestBody.create(MediaType.parse("application/x-bittorrent"), torrentData)
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
                                state = TorrentState.STARTING_LOCAL_DOWNLOADS,
                                rdSpeed = 0,
                            )
                        }
                    } else {
                        // Still downloading on RD, check again later
                        delay(5000)
                    }

                    return task.id
                }

                TorrentState.STARTING_LOCAL_DOWNLOADS -> {
                    task.files.filter { it.state != LocalDownloadState.COMPLETED }.forEach {
                        downloadQueue.add(DownloadWork(task.id, it, apiToken))
                    }

                    // Refresh task state and wait till all downloads have been enqueued
                    task = DownloadTracker.findTask(taskId) ?: return null
                    while (task.files.any { it.downloadManagerId == null }) {
                        delay(1000)
                        task = DownloadTracker.findTask(taskId) ?: return null
                    }

                    // Once all downloads have been queued, change state to wait for all remaining ones
                    DownloadTracker.updateTask(task.id) { it.copy(state = TorrentState.WAITING_FOR_LOCAL_DOWNLOADS) }

                    return task.id
                }

                TorrentState.WAITING_FOR_LOCAL_DOWNLOADS -> {
                    // All done, continue to next state
                    if (task.files.all { it.state == LocalDownloadState.COMPLETED }) {
                        DownloadTracker.updateTask(task.id) { it.copy(state = TorrentState.DELETING_FROM_REAL_DEBRID) }
                    } else {
                        delay(1000)
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
                    // Refill the download (monitoring) queue with tasks that are not yet completed and not yet in the queue
                    task.files.filter { it.state != LocalDownloadState.COMPLETED }.forEach {
                        val notInQueueYet = downloadQueue.none { work ->  work.taskId == task.id && work.file.link == it.link }
                        val notProcessingYet = inProgressWork.none { work -> work.taskId == task.id && work.file.link == it.link }
                        if (notInQueueYet && notProcessingYet) {
                            downloadQueue.add(DownloadWork(task.id, it, apiToken))
                        }
                    }
                }

                continueProcessing(task.id)
            }
        }
    }

    private fun enqueueDownload(url: String, fileName: String, taskName: String): Long {
        val downloadManager = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
        val request = DownloadManager.Request(url.toUri())
            .setTitle(fileName)
            .setDescription("Downloading")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            .setDestinationInExternalPublicDir(
                Environment.DIRECTORY_DOWNLOADS,
                "tmp/$taskName/$fileName"
            )
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)
        return downloadManager.enqueue(request)
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
        const val ACTION_RESUME_DOWNLOADS = "ACTION_RESUME_DOWNLOADS"
        const val ACTION_PROCESS_TASK = "ACTION_PROCESS_TASK"
        const val ACTION_REMOVE_TASK = "ACTION_REMOVE_TASK"
        const val ACTION_RETRY_FILE = "ACTION_RETRY_FILE"
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

package com.felixbrucker.torrenthttpdownloader

import android.app.BackgroundServiceStartNotAllowedException
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import com.felixbrucker.torrenthttpdownloader.container.Container
import com.felixbrucker.torrenthttpdownloader.models.*
import com.felixbrucker.torrenthttpdownloader.network.BandwidthLimitExceededException
import com.felixbrucker.torrenthttpdownloader.network.RateLimitExceededException
import com.felixbrucker.torrenthttpdownloader.network.ResourceNotFoundException
import com.felixbrucker.torrenthttpdownloader.providers.LibTorrentProvider
import com.felixbrucker.torrenthttpdownloader.providers.ProviderFactory
import com.felixbrucker.torrenthttpdownloader.providers.ProviderTorrentInfo
import com.felixbrucker.torrenthttpdownloader.providers.ProviderTorrentState
import com.felixbrucker.torrenthttpdownloader.providers.RealDebridProvider
import com.felixbrucker.torrenthttpdownloader.providers.TorrentProvider
import com.felixbrucker.torrenthttpdownloader.storage.PathFactory
import com.github.junrar.Junrar
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.BufferedSink
import okio.BufferedSource
import okio.buffer
import okio.sink
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException

private data class DownloadWork(val taskId: String, val file: DownloadFile)

class DownloadService : Service() {
    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)
    private val downloadQueue = ConcurrentLinkedQueue<DownloadWork>()
    private val inProgressWork: MutableList<DownloadWork> = mutableListOf()
    private val processingTasks: MutableSet<String> = mutableSetOf()
    private val activeDownloads = ConcurrentHashMap<String, Job>()
    private val taskIdsToProcess = ConcurrentLinkedQueue<String>()
    private var notificationUpdateJob: Job? = null
    private lateinit var provider: TorrentProvider

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    override fun onCreate() {
        super.onCreate()
        Container
            .registerService("SharedPreferences", getSharedPreferences("settings", MODE_PRIVATE))
            .registerService("ConnectivityManager", getSystemService(ConnectivityManager::class.java))
            .registerServiceBuilder(RealDebridProvider)
            .registerServiceBuilder(LibTorrentProvider)
            .registerService("TorrentProvider", ProviderFactory.getProvider())

        provider = Container.getService("TorrentProvider")

        createServiceNotificationChannel()
        createGeneralNotificationChannel()
        startForegroundService()

        val sharedPreferences = getSharedPreferences("settings", MODE_PRIVATE)
        val limit = sharedPreferences.getInt("local_parallel_downloads", 2)
        repeat(limit) {
            launchWorker()
        }
        startNotificationUpdates()

        resumeDownloads()

        processTaskQueue()
    }

    private fun processTaskQueue() = serviceScope.launch(Dispatchers.IO) {
        while (isActive) {
            val taskId = taskIdsToProcess.poll()
            if (taskId == null) {
                delay(500) // Wait before polling again
                continue
            }
            serviceScope.launch(Dispatchers.IO) {
                val newId = processTaskSafely(taskId)
                if (newId != null) {
                    taskIdsToProcess.add(newId)
                }
            }
        }
    }

    private fun createServiceNotificationChannel() {
        val name = "Download Service"
        val descriptionText = "Notifications for background downloads"
        val importance = NotificationManager.IMPORTANCE_LOW
        val channel = NotificationChannel(SERVICE_NOTIFICATION_CHANNEL_ID, name, importance).apply {
            description = descriptionText
        }
        val notificationManager: NotificationManager =
            getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    private fun createGeneralNotificationChannel() {
        val channel = NotificationChannel(
            GENERAL_NOTIFICATION_CHANNEL_ID,
            "General notifications",
            NotificationManager.IMPORTANCE_DEFAULT
        )
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

        val stopIntent = Intent(this, DownloadService::class.java).apply {
            action = ACTION_STOP_SERVICE
        }
        val stopPendingIntent: PendingIntent = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val tasks = DownloadTracker.getTasks()

        var totalSpeed = 0L
        var totalProgress = 0
        var totalDownloadedBytes = 0L
        var totalBytes = 0L
        var runningLocalDownloads = 0
        var totalLocalDownloads = 0
        var completedLocalDownloads = 0

        for (task in tasks) {
            totalSpeed += task.overallDownloadSpeed
            totalProgress += task.overallProgress
            totalDownloadedBytes += task.downloadedBytes
            totalBytes += task.totalBytes
            runningLocalDownloads += task.files.filter { it.state == LocalDownloadState.DOWNLOADING }.size
            completedLocalDownloads += task.files.filter { it.state == LocalDownloadState.COMPLETED }.size
            totalLocalDownloads += task.files.size
        }

        val avgProgress = if (tasks.isNotEmpty()) totalProgress / tasks.size else 0
        val builder = NotificationCompat.Builder(this, SERVICE_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Idle")

        if (tasks.isNotEmpty()) {
            val hasRunningProviderTasks = tasks.any {
                it.location == TaskLocation.PROVIDER
                        && it.providerTorrentInfo?.state != ProviderTorrentState.PAUSED
                        && it.providerTorrentInfo?.state != ProviderTorrentState.COMPLETED
            }
            var title = "Downloading: ${Formatter.formatSpeed(totalSpeed)}"
            builder.setSmallIcon(android.R.drawable.stat_sys_download)

            if (totalSpeed > 0) {
                val remainingBytes = totalBytes - totalDownloadedBytes
                val remainingTime = remainingBytes / totalSpeed
                title += " • ${Formatter.formatTime(remainingTime)} left"
            } else if (runningLocalDownloads == 0) {
                if (hasRunningProviderTasks) {
                    title = "Working"
                } else {
                    title = "Idle"
                    builder.setSmallIcon(android.R.drawable.stat_sys_download_done)
                }
            }

            val style = NotificationCompat
                .InboxStyle()
                .addLine("Tasks: ${tasks.size} remaining")
            if (totalLocalDownloads > 0) {
                style.addLine("Downloads: $runningLocalDownloads active, $completedLocalDownloads/$totalLocalDownloads completed")
            }
            style.addLine("Size: ${Formatter.formatBytes(totalDownloadedBytes)} / ${Formatter.formatBytes(totalBytes)} downloaded")

            builder
                .setStyle(style)
                .setContentTitle(title)
        }

        builder
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setProgress(100, avgProgress, tasks.isEmpty())
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Exit", stopPendingIntent)

        val anyDownloading = tasks.any { task -> task.files.any { it.state == LocalDownloadState.DOWNLOADING || it.state == LocalDownloadState.PENDING } }
        val anyPaused = tasks.any { task -> task.files.any { it.state == LocalDownloadState.PAUSED } }
        if (anyDownloading) {
            val pauseAllIntent = Intent(this, DownloadService::class.java).apply {
                action = ACTION_PAUSE_ALL
            }
            val pauseAllPendingIntent: PendingIntent = PendingIntent.getService(
                this, 3, pauseAllIntent,
                PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(android.R.drawable.ic_media_pause, "Pause all", pauseAllPendingIntent)
        }
        if (anyPaused) {
            val resumeAllIntent = Intent(this, DownloadService::class.java).apply {
                action = ACTION_RESUME_ALL
            }
            val resumeAllPendingIntent: PendingIntent = PendingIntent.getService(
                this, 4, resumeAllIntent,
                PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(android.R.drawable.ic_media_play, "Resume all", resumeAllPendingIntent)
        }
        if (provider.supportsPauseResume) {
            val anyDownloadingOnProvider = tasks.any { it.providerTorrentInfo?.state == ProviderTorrentState.DOWNLOADING }
            val anyPausedOnProvider = tasks.any { it.providerTorrentInfo?.state == ProviderTorrentState.PAUSED }
            if (anyDownloadingOnProvider) {
                val pauseAllOnProviderIntent = Intent(this, DownloadService::class.java).apply {
                    action = ACTION_PAUSE_ALL_ON_PROVIDER
                }
                val pauseAllOnProviderPendingIntent: PendingIntent = PendingIntent.getService(
                    this, 5, pauseAllOnProviderIntent,
                    PendingIntent.FLAG_IMMUTABLE
                )
                builder.addAction(
                    android.R.drawable.ic_media_pause,
                    "Pause all (on provider)",
                    pauseAllOnProviderPendingIntent
                )
            }
            if (anyPausedOnProvider) {
                val resumeAllOnProviderIntent = Intent(this, DownloadService::class.java).apply {
                    action = ACTION_RESUME_ALL_ON_PROVIDER
                }
                val resumeAllOnProviderPendingIntent: PendingIntent = PendingIntent.getService(
                    this, 6, resumeAllOnProviderIntent,
                    PendingIntent.FLAG_IMMUTABLE
                )
                builder.addAction(
                    android.R.drawable.ic_media_play,
                    "Resume all (on provider)",
                    resumeAllOnProviderPendingIntent
                )
            }
        }

        return builder.build()
    }

    private fun startForegroundService() {
        startForeground(
            SERVICE_NOTIFICATION_ID,
            getNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )
    }

    private fun startNotificationUpdates() {
        notificationUpdateJob?.cancel()
        notificationUpdateJob = serviceScope.launch {
            while (isActive) {
                updateNotification()
                delay(2000)
            }
        }
    }

    private fun updateNotification() {
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(SERVICE_NOTIFICATION_ID, getNotification())
    }

    private fun launchWorker() = serviceScope.launch(Dispatchers.IO) {
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
            // Support regenerating the link if the existing one expires (
            // TODO: how to detect expired links?
            if (file.unrestrictedLink == null) {
                updateFileInfo(task, file)
                file = DownloadTracker.findTask(work.taskId)?.files?.find { it.link == file.link } ?: return
            }
            val downloadUrl = file.unrestrictedLink ?: return

            val job = serviceScope.launch(Dispatchers.IO) {
                performDownload(work, downloadUrl, file)
            }
            activeDownloads[file.link] = job
            job.join()
        } catch (e: CancellationException) {
            throw e // Let the coroutine be cancelled
        } catch (e: Exception) {
            DownloadTracker.updateTaskFile(work.taskId, work.file.link) {
                it.copy(
                    state = LocalDownloadState.ERROR,
                    stateDescription = "Error: ${e.message}",
                    speed = 0,
                )
            }
        } finally {
            activeDownloads.remove(file.link)
        }
    }

    private suspend fun performDownload(work: DownloadWork, unrestrictedLink: String, downloadFile: DownloadFile) {
        val taskId = work.taskId
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

    private fun updateFileState(taskId: String, fileLink: String, state: LocalDownloadState, error: String? = null) {
        DownloadTracker.updateTaskFile(taskId, fileLink) { file ->
            file.copy(state = state, stateDescription = error, speed = 0)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_REMOVE_TASK -> serviceScope.launch(Dispatchers.IO) { removeTask(intent.getStringExtra(EXTRA_TASK_ID)) }
            ACTION_PAUSE_FILE -> pauseFile(intent.getStringExtra(EXTRA_TASK_ID), intent.getStringExtra(EXTRA_FILE_LINK))
            ACTION_RESUME_FILE -> resumeFile(intent.getStringExtra(EXTRA_TASK_ID), intent.getStringExtra(EXTRA_FILE_LINK))
            ACTION_PAUSE_TASK -> pauseTask(intent.getStringExtra(EXTRA_TASK_ID))
            ACTION_PAUSE_TASK_ON_PROVIDER -> serviceScope.launch { pauseTaskOnProvider(intent.getStringExtra(EXTRA_TASK_ID)) }
            ACTION_RESUME_TASK -> resumeTask(intent.getStringExtra(EXTRA_TASK_ID))
            ACTION_RESUME_TASK_ON_PROVIDER -> serviceScope.launch { resumeTaskOnProvider(intent.getStringExtra(EXTRA_TASK_ID)) }
            ACTION_RESTART_TASK -> serviceScope.launch(Dispatchers.IO) { restartTask(intent.getStringExtra(EXTRA_TASK_ID)) }
            ACTION_PAUSE_ALL -> pauseAll()
            ACTION_PAUSE_ALL_ON_PROVIDER -> serviceScope.launch { pauseAllOnProvider() }
            ACTION_RESUME_ALL -> resumeAll()
            ACTION_RESUME_ALL_ON_PROVIDER -> serviceScope.launch { resumeAllOnProvider() }
            ACTION_ADD_TASK -> handleAddTask(intent)
            ACTION_STOP_SERVICE -> stopAllDownloadsAndExit()
        }

        return START_STICKY
    }

    private fun stopAllDownloadsAndExit() {
        downloadQueue.clear()
        activeDownloads.values.forEach { it.cancel() }
        activeDownloads.clear()

        stopSelf()
    }

    private fun stopSelfIfIdle() {
        if (!DownloadTracker.hasTasksWhichNeedProcessing()) {
            stopSelf()
        }
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
        enqueueDownload(DownloadWork(taskId, file))
    }

    private fun pauseTask(taskId: String?) {
        if (taskId == null) return
        val task = DownloadTracker.findTask(taskId) ?: return
        task.files.forEach { file ->
            if (file.state == LocalDownloadState.DOWNLOADING || file.state == LocalDownloadState.PENDING) {
                pauseFile(taskId, file.link)
            }
        }
    }

    private suspend fun pauseTaskOnProvider(taskId: String?) {
        if (taskId == null) return
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

    private fun resumeTask(taskId: String?) {
        if (taskId == null) return
        val task = DownloadTracker.findTask(taskId) ?: return
        task.files.forEach { file ->
            if (file.state == LocalDownloadState.PAUSED) {
                resumeFile(taskId, file.link)
            }
        }
    }

    private suspend fun resumeTaskOnProvider(taskId: String?) {
        if (taskId == null) return
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

    private suspend fun restartTask(taskId: String?) {
        if (taskId == null) return
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

    private fun pauseAll() {
        DownloadTracker.getTasks().forEach { task ->
            pauseTask(task.id)
        }
        updateNotification()
    }

    private suspend fun pauseAllOnProvider() {
        DownloadTracker
            .getTasks()
            .filter { it.providerTorrentInfo?.state == ProviderTorrentState.DOWNLOADING }
            .forEach { task -> pauseTaskOnProvider(task.id) }
        updateNotification()
    }

    private fun resumeAll() {
        DownloadTracker.getTasks().forEach { task ->
            resumeTask(task.id)
        }
        updateNotification()
    }

    private suspend fun resumeAllOnProvider() {
        DownloadTracker
            .getTasks()
            .filter { it.providerTorrentInfo?.state == ProviderTorrentState.PAUSED }
            .forEach { task -> resumeTaskOnProvider(task.id) }
        updateNotification()
    }

    private fun handleAddTask(intent: Intent) {
        val path = intent.getStringExtra(EXTRA_TORRENT_PATH) ?: return
        val type = TorrentType.valueOf(intent.getStringExtra(EXTRA_TORRENT_TYPE) ?: TorrentType.MAGNET.name)
        val destinationSubdirectory = intent.getStringExtra(EXTRA_DESTINATION_SUBDIRECTORY)
        val createSubfolderByName = intent.getBooleanExtra(EXTRA_CREATE_SUBFOLDER_BY_NAME, true)
        val notifyOnCompletion = intent.getBooleanExtra(EXTRA_NOTIFY_ON_COMPLETION, false)
        val onlyDownloadBiggestFile = intent.getBooleanExtra(EXTRA_ONLY_DOWNLOAD_BIGGEST_FILE, false)
        val torrentName = intent.getStringExtra(EXTRA_TORRENT_NAME)

        if (DownloadTracker.getTasks().any { it.torrent.path == path }) return

        val task = DownloadTask(
            id = path,
            name = torrentName ?: path,
            torrent = TorrentDescriptor(type, path),
            destinationSubdirectory = destinationSubdirectory,
            createSubfolderByName = createSubfolderByName,
            notifyOnCompletion = notifyOnCompletion,
            onlyDownloadBiggestFile = onlyDownloadBiggestFile,
            state = TorrentState.ADDING_TO_PROVIDER
        )
        DownloadTracker.addTask(task)
        taskIdsToProcess.add(task.id)
    }

    private suspend fun removeTask(taskId: String?) {
        if (taskId == null) return
        val task = DownloadTracker.findTask(taskId) ?: return

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
        if (task.torrent.type == TorrentType.TORRENT_FILE) {
            val fileRef = File(task.torrent.path)
            if (fileRef.exists()) {
                fileRef.delete()
            }
        }
        if (PathFactory.getResumeDataPath(task.id).exists()) {
            PathFactory.getResumeDataPath(task.id).delete()
        }

        // Remove scoped temp directory if available
        val tempDir = PathFactory.getScopedTemporaryDirectory(task.name)
        if (tempDir.exists()) {
            tempDir.deleteRecursively()
        }

        // If the task is on Provider, delete it there
        if (task.state.ordinal < TorrentState.DELETING_FROM_PROVIDER.ordinal && task.providerId != null) {
            provider.deleteTorrent(task.providerId, deleteFiles = true)
        }

        DownloadTracker.removeTask(taskId)

        stopSelfIfIdle()
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
                        provider.addMagnet(task.torrent.path, task.name)
                    } else {
                        contentResolver.openInputStream(task.torrent.path.toUri())?.use {
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
                        delay(2000)
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
                        delay(5000)
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
                        enqueueDownload(DownloadWork(task.id, it))
                    }

                    var files = task.files
                    while (files.any { it.state != LocalDownloadState.COMPLETED }) {
                        delay(2000)
                        files = DownloadTracker.findTask(task.id)?.files ?: return null
                    }

                    // All done, continue to next state
                    DownloadTracker.updateTask(task.id) { it.copy(state = TorrentState.DELETING_FROM_PROVIDER) }

                    return task.id
                }

                TorrentState.DELETING_FROM_PROVIDER -> {
                    val isTorrentDeleted = provider.deleteTorrent(task.id)
                    if (isTorrentDeleted) {
                        // Also delete local torrent file
                        if (task.torrent.type == TorrentType.TORRENT_FILE) {
                            val fileRef = File(task.torrent.path)
                            if (fileRef.exists()) {
                                fileRef.delete()
                            }
                        }
                        // Also delete resume data
                        if (PathFactory.getResumeDataPath(task.id).exists()) {
                            PathFactory.getResumeDataPath(task.id).delete()
                        }
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
                    val source = PathFactory.getScopedTemporaryDirectory(task.name)

                    ensureTorrentIsFlattened(source)

                    val destination = PathFactory.getScopedDestinationDirectory(task)

                    val parentDestinationDir = destination.parentFile
                    if (parentDestinationDir != null && !parentDestinationDir.exists()) {
                        parentDestinationDir.mkdirs()
                    }

                    if (destination.exists()) {
                        // Directory merge, move files individually
                        mergeDirectory(source, destination)
                    } else {
                        // No conflict, just move the whole directory
                        source.renameTo(destination)
                    }

                    DownloadTracker.updateTask(task.id) { it.copy(state = TorrentState.COMPLETED) }

                    return task.id
                }

                TorrentState.COMPLETED -> {
                    if (task.notifyOnCompletion) {
                        postNotification(
                            "Download finished",
                            "${task.name} finished downloading",
                        )
                    }

                    DownloadTracker.removeTask(taskId)
                    stopSelfIfIdle()
                }
                else -> { /* No action needed for COMPLETED or ERROR */ }
            }
        } catch (e: CancellationException) {
            throw e // Let the coroutine be cancelled
        } catch (e: BackgroundServiceStartNotAllowedException) {
            throw e // Ignore
        } catch (e: RateLimitExceededException) {
            e.printStackTrace()

            // Retry after 15 sec
            delay(15_000)

            return task.id
        } catch (e: BandwidthLimitExceededException) {
            e.printStackTrace()

            // Retry after 1 min
            delay(60_000)

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
        postNotification(
            "Torrent has been restarted",
            "Torrent ${task.name} encountered an error on provider and has been restarted"
        )
    }

    private fun ensureTorrentIsFlattened(directory: File) {
        var filesInRoot = directory.listFiles() ?: arrayOf()
        while (filesInRoot.size == 1 && filesInRoot[0].isDirectory) {
            mergeDirectory(filesInRoot[0], directory)
            filesInRoot = directory.listFiles() ?: arrayOf()
        }
    }

    private fun mergeDirectory(sourceDirectory: File, destinationDirectory: File) {
        sourceDirectory.listFiles()?.forEach { file ->
            if (!file.exists()) {
                return@forEach
            }
            val destFile = File(destinationDirectory, file.name)
            if (destFile.exists()) {
                if (file.isDirectory && destFile.isDirectory) {
                    return@forEach mergeDirectory(file, destFile)
                } else {
                    destFile.delete()
                }
            }
            file.renameTo(destFile)
        }
        if (sourceDirectory.listFiles()?.size == 0) {
            sourceDirectory.delete()
        }
    }

    private suspend fun resetTorrent(task: DownloadTask) {
        // Remove pending local downloads
        downloadQueue.removeIf { it.taskId == task.id }

        // Cancel active downloads
        for (file in task.files) {
            activeDownloads[file.link]?.cancel()
            activeDownloads.remove(file.link)
        }
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

    private fun handleFailedLocalDownload(work: DownloadWork, file: DownloadFile, errorMessage: String) {
        updateFileState(work.taskId, file.link, LocalDownloadState.ERROR, errorMessage)
        postNotification(
            "Torrent download encountered an error",
            "Torrent file ${file.fileName} encountered an error while downloading: $errorMessage"
        )
        serviceScope.launch(Dispatchers.IO) {
            delay(5000)
            enqueueDownload(work.copy(file = file))
        }
    }

    private fun extractFile(filePath: String): Job {
        return serviceScope.launch(Dispatchers.IO) {
            try {
                val file = File(filePath)
                val destination = file.parentFile
                Junrar.extract(file, destination)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private suspend fun updateFileInfo(task: DownloadTask, file: DownloadFile) {
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

    private fun enqueueDownload(work: DownloadWork) {
        updateFileState(work.taskId, work.file.link, LocalDownloadState.PENDING)
        downloadQueue.add(work)
    }

    private fun updateTaskWithTorrentInfo(taskId: String, torrentInfo: ProviderTorrentInfo) {
        DownloadTracker.updateTask(taskId) {
            it.copy(
                name = if (it.name == it.torrent.path) torrentInfo.name else it.name,
                providerTorrentInfo = torrentInfo,
            )
        }
    }

    private fun postNotification(title: String, message: String) {
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        val notificationBuilder = NotificationCompat.Builder(this, GENERAL_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentTitle(title)
            .setContentText(message)

        notificationManager.notify(0, notificationBuilder.build())
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        provider.stop()
        Container.clear()
        serviceJob.cancel()
    }

    companion object {
        private const val SERVICE_NOTIFICATION_ID = 1
        private const val SERVICE_NOTIFICATION_CHANNEL_ID = "download_service"
        private const val GENERAL_NOTIFICATION_CHANNEL_ID = "general"
        const val ACTION_REMOVE_TASK = "ACTION_REMOVE_TASK"
        const val ACTION_PAUSE_FILE = "ACTION_PAUSE_FILE"
        const val ACTION_RESUME_FILE = "ACTION_RESUME_FILE"
        const val ACTION_PAUSE_TASK = "ACTION_PAUSE_TASK"
        const val ACTION_PAUSE_TASK_ON_PROVIDER = "ACTION_PAUSE_TASK_ON_PROVIDER"
        const val ACTION_RESUME_TASK = "ACTION_RESUME_TASK"
        const val ACTION_RESUME_TASK_ON_PROVIDER = "ACTION_RESUME_TASK_ON_PROVIDER"
        const val ACTION_PAUSE_ALL = "ACTION_PAUSE_ALL"
        const val ACTION_PAUSE_ALL_ON_PROVIDER = "ACTION_PAUSE_ALL_ON_PROVIDER"
        const val ACTION_RESUME_ALL = "ACTION_RESUME_ALL"
        const val ACTION_RESUME_ALL_ON_PROVIDER = "ACTION_RESUME_ALL_ON_PROVIDER"
        const val ACTION_ADD_TASK = "ACTION_ADD_TASK"
        const val ACTION_STOP_SERVICE = "ACTION_STOP_SERVICE"
        const val ACTION_RESTART_TASK = "ACTION_RESTART_TASK"
        const val EXTRA_TASK_ID = "EXTRA_TASK_ID"
        const val EXTRA_FILE_LINK = "EXTRA_FILE_LINK"
        const val EXTRA_TORRENT_PATH = "EXTRA_TORRENT_PATH"
        const val EXTRA_TORRENT_TYPE = "EXTRA_TORRENT_TYPE"
        const val EXTRA_DESTINATION_SUBDIRECTORY = "EXTRA_DESTINATION_SUBDIRECTORY"
        const val EXTRA_CREATE_SUBFOLDER_BY_NAME = "EXTRA_CREATE_SUBFOLDER_BY_NAME"
        const val EXTRA_NOTIFY_ON_COMPLETION = "EXTRA_NOTIFY_ON_COMPLETION"
        const val EXTRA_ONLY_DOWNLOAD_BIGGEST_FILE = "EXTRA_ONLY_DOWNLOAD_BIGGEST_FILE"
        const val EXTRA_TORRENT_NAME = "EXTRA_TORRENT_NAME"
    }
}

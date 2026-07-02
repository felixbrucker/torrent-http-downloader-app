package com.felixbrucker.torrenthttpdownloader

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
import com.felixbrucker.torrenthttpdownloader.container.Container
import com.felixbrucker.torrenthttpdownloader.models.*
import com.felixbrucker.torrenthttpdownloader.providers.LibTorrentProvider
import com.felixbrucker.torrenthttpdownloader.providers.ProviderFactory
import com.felixbrucker.torrenthttpdownloader.providers.ProviderTorrentState
import com.felixbrucker.torrenthttpdownloader.providers.RealDebridProvider
import com.felixbrucker.torrenthttpdownloader.providers.TorrentProvider
import kotlinx.coroutines.*
import kotlin.time.Duration.Companion.seconds

class DownloadService : Service() {
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)
    private var notificationUpdateJob: Job? = null
    private lateinit var provider: TorrentProvider
    private lateinit var localDownloadManager: LocalDownloadManager
    private lateinit var torrentStateMachine: TorrentStateMachine

    override fun onCreate() {
        super.onCreate()
        Container
            .registerService("SharedPreferences", getSharedPreferences("settings", MODE_PRIVATE))
            .registerService("ConnectivityManager", getSystemService(ConnectivityManager::class.java))
            .registerServiceBuilder(RealDebridProvider)
            .registerServiceBuilder(LibTorrentProvider)
            .registerService("TorrentProvider", ProviderFactory.getProvider())

        provider = Container.getService("TorrentProvider")

        localDownloadManager = LocalDownloadManager(
            scope = serviceScope,
            sharedPreferences = Container.getService("SharedPreferences"),
            onLinkExpired = { task, file ->
                torrentStateMachine.updateFileInfo(task, file)
            },
            onPostNotification = ::postNotification,
        )

        torrentStateMachine = TorrentStateMachine(
            scope = serviceScope,
            provider = provider,
            localDownloadManager = localDownloadManager,
            contentResolver = contentResolver,
            onTaskCompleted = { _ ->
                updateNotification()
                stopSelfIfIdle()
            },
            onPostNotification = ::postNotification,
        )

        createServiceNotificationChannel()
        createGeneralNotificationChannel()
        startForegroundService()

        startNotificationUpdates()

        localDownloadManager.start()
        torrentStateMachine.start()
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
                action = ACTION_PAUSE_ALL_LOCAL_DOWNLOADS
            }
            val pauseAllPendingIntent: PendingIntent = PendingIntent.getService(
                this, 3, pauseAllIntent,
                PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(android.R.drawable.ic_media_pause, "Pause all", pauseAllPendingIntent)
        }
        if (anyPaused) {
            val resumeAllIntent = Intent(this, DownloadService::class.java).apply {
                action = ACTION_RESUME_ALL_LOCAL_DOWNLOADS
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
                delay(2.seconds)
            }
        }
    }

    private fun updateNotification() {
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(SERVICE_NOTIFICATION_ID, getNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_REMOVE_TASK -> serviceScope.launch(Dispatchers.IO) {
                removeTask(
                    intent.getStringExtra(EXTRA_TASK_ID),
                    deleteFiles = intent.getBooleanExtra(EXTRA_DELETE_FILES, true),
                    deleteTorrentFile = intent.getBooleanExtra(EXTRA_DELETE_TORRENT_FILE, true)
                )
            }
            ACTION_PAUSE_LOCAL_FILE_DOWNLOAD -> pauseLocalFileDownload(intent.getStringExtra(EXTRA_TASK_ID), intent.getStringExtra(EXTRA_FILE_LINK))
            ACTION_RESUME_LOCAL_FILE_DOWNLOAD -> resumeLocalFileDownload(intent.getStringExtra(EXTRA_TASK_ID), intent.getStringExtra(EXTRA_FILE_LINK))
            ACTION_PAUSE_TASK_LOCAL_DOWNLOADS -> pauseTaskLocalDownloads(intent.getStringExtra(EXTRA_TASK_ID))
            ACTION_PAUSE_TASK_ON_PROVIDER -> serviceScope.launch { pauseTaskOnProvider(intent.getStringExtra(EXTRA_TASK_ID)) }
            ACTION_RESUME_TASK_LOCAL_DOWNLOADS -> resumeTaskLocalDownloads(intent.getStringExtra(EXTRA_TASK_ID))
            ACTION_RESUME_TASK_ON_PROVIDER -> serviceScope.launch { resumeTaskOnProvider(intent.getStringExtra(EXTRA_TASK_ID)) }
            ACTION_RESTART_TASK -> serviceScope.launch(Dispatchers.IO) { restartTask(intent.getStringExtra(EXTRA_TASK_ID)) }
            ACTION_RELOAD_SETTINGS -> reloadSettings()
            ACTION_PAUSE_ALL_LOCAL_DOWNLOADS -> pauseAllLocalDownloads()
            ACTION_PAUSE_ALL_ON_PROVIDER -> serviceScope.launch { pauseAllOnProvider() }
            ACTION_RESUME_ALL_LOCAL_DOWNLOADS -> resumeAllLocalDownloads()
            ACTION_RESUME_ALL_ON_PROVIDER -> serviceScope.launch { resumeAllOnProvider() }
            ACTION_ADD_TASK -> handleAddTask(intent)
            ACTION_STOP_SERVICE -> stopSelf()
        }

        return START_STICKY
    }

    private fun reloadSettings() {
        provider.reloadSettings()
    }

    private fun stopSelfIfIdle() {
        if (!DownloadTracker.hasTasksWhichNeedProcessing()) {
            stopSelf()
        }
    }

    private fun pauseLocalFileDownload(taskId: String?, fileLink: String?) {
        if (taskId == null || fileLink == null) return
        localDownloadManager.pauseFile(taskId, fileLink)
        updateNotification()
    }

    private fun resumeLocalFileDownload(taskId: String?, fileLink: String?) {
        if (taskId == null || fileLink == null) return
        localDownloadManager.resumeFile(taskId, fileLink)
        updateNotification()
    }

    private fun pauseTaskLocalDownloads(taskId: String?) {
        if (taskId == null) return
        localDownloadManager.pauseTask(taskId)
        updateNotification()
    }

    private fun resumeTaskLocalDownloads(taskId: String?) {
        if (taskId == null) return
        localDownloadManager.resumeTask(taskId)
        updateNotification()
    }

    private fun pauseAllLocalDownloads() {
        localDownloadManager.pauseAll()
        updateNotification()
    }

    private fun resumeAllLocalDownloads() {
        localDownloadManager.resumeAll()
        updateNotification()
    }

    private suspend fun pauseTaskOnProvider(taskId: String?) {
        if (taskId == null) return
        torrentStateMachine.pauseTaskOnProvider(taskId)
        updateNotification()
    }

    private suspend fun resumeTaskOnProvider(taskId: String?) {
        if (taskId == null) return
        torrentStateMachine.resumeTaskOnProvider(taskId)
        updateNotification()
    }

    private suspend fun restartTask(taskId: String?) {
        if (taskId == null) return
        torrentStateMachine.restartTask(taskId)
        updateNotification()
    }

    private suspend fun pauseAllOnProvider() {
        torrentStateMachine.pauseAllTasksOnProvider()
        updateNotification()
    }

    private suspend fun resumeAllOnProvider() {
        torrentStateMachine.resumeAllTasksOnProvider()
        updateNotification()
    }

    private fun handleAddTask(intent: Intent) {
        val uri = intent.getStringExtra(EXTRA_TORRENT_URI) ?: return
        val type = TorrentType.valueOf(intent.getStringExtra(EXTRA_TORRENT_TYPE) ?: TorrentType.MAGNET.name)
        val destinationSubdirectory = intent.getStringExtra(EXTRA_DESTINATION_SUBDIRECTORY)
        val createSubfolderByName = intent.getBooleanExtra(EXTRA_CREATE_SUBFOLDER_BY_NAME, true)
        val notifyOnCompletion = intent.getBooleanExtra(EXTRA_NOTIFY_ON_COMPLETION, false)
        val onlyDownloadBiggestFile = intent.getBooleanExtra(EXTRA_ONLY_DOWNLOAD_BIGGEST_FILE, false)
        val torrentName = intent.getStringExtra(EXTRA_TORRENT_NAME)

        if (DownloadTracker.getTasks().any { it.torrent.uri == uri }) return

        val task = DownloadTask(
            id = uri,
            name = torrentName ?: uri,
            torrent = TorrentDescriptor(type, uri),
            destinationSubdirectory = destinationSubdirectory,
            createSubfolderByName = createSubfolderByName,
            notifyOnCompletion = notifyOnCompletion,
            onlyDownloadBiggestFile = onlyDownloadBiggestFile,
            state = TorrentState.ADDING_TO_PROVIDER
        )
        torrentStateMachine.addTask(task)
    }

    private suspend fun removeTask(
        taskId: String?,
        deleteFiles: Boolean,
        deleteTorrentFile: Boolean
    ) {
        if (taskId == null) return
        torrentStateMachine.removeTask(
            taskId,
            deleteFiles = deleteFiles,
            deleteTorrentFile = deleteTorrentFile
        )

        stopSelfIfIdle()
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
        torrentStateMachine.stop()
        localDownloadManager.stop()
        provider.stop()
        Container.clear()
        serviceJob.cancel()
    }

    companion object {
        private const val SERVICE_NOTIFICATION_ID = 1
        private const val SERVICE_NOTIFICATION_CHANNEL_ID = "download_service"
        private const val GENERAL_NOTIFICATION_CHANNEL_ID = "general"
        const val ACTION_REMOVE_TASK = "ACTION_REMOVE_TASK"
        const val ACTION_PAUSE_LOCAL_FILE_DOWNLOAD = "ACTION_PAUSE_LOCAL_FILE_DOWNLOAD"
        const val ACTION_RESUME_LOCAL_FILE_DOWNLOAD = "ACTION_RESUME_LOCAL_FILE_DOWNLOAD"
        const val ACTION_PAUSE_TASK_LOCAL_DOWNLOADS = "ACTION_PAUSE_TASK_LOCAL_DOWNLOADS"
        const val ACTION_PAUSE_TASK_ON_PROVIDER = "ACTION_PAUSE_TASK_ON_PROVIDER"
        const val ACTION_RESUME_TASK_LOCAL_DOWNLOADS = "ACTION_RESUME_TASK_LOCAL_DOWNLOADS"
        const val ACTION_RESUME_TASK_ON_PROVIDER = "ACTION_RESUME_TASK_ON_PROVIDER"
        const val ACTION_PAUSE_ALL_LOCAL_DOWNLOADS = "ACTION_PAUSE_ALL_LOCAL_DOWNLOADS"
        const val ACTION_PAUSE_ALL_ON_PROVIDER = "ACTION_PAUSE_ALL_ON_PROVIDER"
        const val ACTION_RESUME_ALL_LOCAL_DOWNLOADS = "ACTION_RESUME_ALL_LOCAL_DOWNLOADS"
        const val ACTION_RESUME_ALL_ON_PROVIDER = "ACTION_RESUME_ALL_ON_PROVIDER"
        const val ACTION_ADD_TASK = "ACTION_ADD_TASK"
        const val ACTION_STOP_SERVICE = "ACTION_STOP_SERVICE"
        const val ACTION_RESTART_TASK = "ACTION_RESTART_TASK"
        const val ACTION_RELOAD_SETTINGS = "ACTION_RELOAD_SETTINGS"
        const val EXTRA_TASK_ID = "EXTRA_TASK_ID"
        const val EXTRA_FILE_LINK = "EXTRA_FILE_LINK"
        const val EXTRA_TORRENT_URI = "EXTRA_TORRENT_URI"
        const val EXTRA_TORRENT_TYPE = "EXTRA_TORRENT_TYPE"
        const val EXTRA_DESTINATION_SUBDIRECTORY = "EXTRA_DESTINATION_SUBDIRECTORY"
        const val EXTRA_CREATE_SUBFOLDER_BY_NAME = "EXTRA_CREATE_SUBFOLDER_BY_NAME"
        const val EXTRA_NOTIFY_ON_COMPLETION = "EXTRA_NOTIFY_ON_COMPLETION"
        const val EXTRA_ONLY_DOWNLOAD_BIGGEST_FILE = "EXTRA_ONLY_DOWNLOAD_BIGGEST_FILE"
        const val EXTRA_TORRENT_NAME = "EXTRA_TORRENT_NAME"
        const val EXTRA_DELETE_FILES = "EXTRA_DELETE_FILES"
        const val EXTRA_DELETE_TORRENT_FILE = "EXTRA_DELETE_TORRENT_FILE"
    }
}

package com.felixbrucker.torrenthttpdownloader

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import com.felixbrucker.torrenthttpdownloader.data.preferences.AppSettingsRepository
import com.felixbrucker.torrenthttpdownloader.data.repository.DownloadRepository
import com.felixbrucker.torrenthttpdownloader.AddTorrentParams
import com.felixbrucker.torrenthttpdownloader.IAddTorrentCallback
import com.felixbrucker.torrenthttpdownloader.ITorrentDownloadService
import com.felixbrucker.torrenthttpdownloader.TorrentProgressStats
import com.felixbrucker.torrenthttpdownloader.models.DownloadTask
import com.felixbrucker.torrenthttpdownloader.models.FileSelectionMode
import com.felixbrucker.torrenthttpdownloader.models.LocalDownloadState
import com.felixbrucker.torrenthttpdownloader.models.TaskLocation
import com.felixbrucker.torrenthttpdownloader.models.TorrentDescriptor
import com.felixbrucker.torrenthttpdownloader.models.TorrentState
import com.felixbrucker.torrenthttpdownloader.models.TorrentType
import com.felixbrucker.torrenthttpdownloader.network.TorrentUriResolver
import com.felixbrucker.torrenthttpdownloader.providers.FilePriority
import com.felixbrucker.torrenthttpdownloader.providers.ProviderFactory
import com.felixbrucker.torrenthttpdownloader.providers.ProviderFeature
import com.felixbrucker.torrenthttpdownloader.providers.ProviderTorrentState
import com.felixbrucker.torrenthttpdownloader.providers.TorrentProvider
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

@AndroidEntryPoint
class DownloadService : Service() {
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)
    private var notificationUpdateJob: Job? = null

    @Inject lateinit var downloadRepository: DownloadRepository
    @Inject lateinit var appSettingsRepository: AppSettingsRepository
    @Inject lateinit var localDownloadManager: LocalDownloadManager
    @Inject lateinit var providerFactory: ProviderFactory
    @Inject lateinit var torrentUriResolver: TorrentUriResolver

    private lateinit var provider: TorrentProvider
    private lateinit var torrentStateMachine: TorrentStateMachine

    private val binder = object : ITorrentDownloadService.Stub() {
        override fun addTorrent(params: AddTorrentParams, callback: IAddTorrentCallback) {
            serviceScope.launch {
                try {
                    val uri = params.uri ?: throw IllegalArgumentException("URI is required")
                    val resolved = torrentUriResolver.resolve(uri.toUri())
                    val id = resolved.id
                    val name = params.name ?: resolved.name ?: uri
                    val type = resolved.type

                    val existingTasks = downloadRepository.tasksFlow.first()
                    if (existingTasks.any { it.id == id }) {
                        callback.onFailure("Torrent already added")
                        return@launch
                    }

                    val task = DownloadTask(
                        id = id,
                        name = name,
                        torrent = TorrentDescriptor(type, uri),
                        destinationSubdirectory = params.destinationSubdirectory,
                        createSubfolderByName = params.createSubfolderByName,
                        notifyOnCompletion = params.notifyOnCompletion,
                        fileSelectionMode = params.fileSelectionMode?.let { FileSelectionMode.valueOf(it) } ?: FileSelectionMode.ALL,
                        onCompletionIntentUri = params.onCompletionIntentUri,
                        state = TorrentState.ADDING_TO_PROVIDER
                    )
                    addTask(task)
                    callback.onSuccess(id)
                } catch (e: Exception) {
                    callback.onFailure(e.message ?: "Unknown error")
                }
            }
        }

        override fun getProgress(taskId: String): TorrentProgressStats? {
            var stats: TorrentProgressStats? = null
            runCatching {
                val task = kotlinx.coroutines.runBlocking { downloadRepository.getTaskById(taskId) } ?: return null
                stats = TorrentProgressStats().apply {
                    bytesDownloaded = task.downloadedBytes
                    totalBytes = task.totalBytes
                    downloadSpeed = task.overallDownloadSpeed.toDouble()
                }
            }
            return stats
        }
    }

    override fun onCreate() {
        super.onCreate()

        createServiceNotificationChannel()
        createGeneralNotificationChannel()
        startForegroundService()

        serviceScope.launch {
            provider = providerFactory.getSelectedProvider()

            localDownloadManager.onLinkExpired = { task, file ->
                torrentStateMachine.updateFileInfo(task, file)
            }
            localDownloadManager.onPostNotification = ::postNotification

            torrentStateMachine = TorrentStateMachine(
                scope = serviceScope,
                provider = provider,
                localDownloadManager = localDownloadManager,
                downloadRepository = downloadRepository,
                contentResolver = contentResolver,
                onTaskCompleted = { task ->
                    task.onCompletionIntentUri?.let { uri ->
                        try {
                            val intent = Intent.parseUri(uri, Intent.URI_INTENT_SCHEME)
                            sendBroadcast(intent)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                    updateNotification()
                    stopSelfIfIdle()
                },
                onPostNotification = ::postNotification
            )

            startNotificationUpdates()

            localDownloadManager.start(serviceScope)
            torrentStateMachine.start()
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
        if (::provider.isInitialized && provider.supports(ProviderFeature.PauseResume)) {
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
            ACTION_ADD_TASK -> addTaskFromIntent(intent)
            ACTION_SET_PROVIDER_FILE_PRIORITY -> serviceScope.launch {
                setProviderFilePriority(
                    intent.getStringExtra(EXTRA_TASK_ID),
                    intent.getIntExtra(EXTRA_FILE_ID, -1),
                    FilePriority.valueOf(intent.getStringExtra(EXTRA_PRIORITY) ?: FilePriority.NORMAL.name)
                )
            }
            ACTION_TOGGLE_PROVIDER_FILE_SELECTION -> {
                toggleProviderFileSelectionLocally(
                    intent.getStringExtra(EXTRA_TASK_ID),
                    intent.getIntExtra(EXTRA_FILE_ID, -1)
                )
            }
            ACTION_TOGGLE_ALL_PROVIDER_FILE_SELECTION -> {
                toggleAllProviderFileSelectionLocally(
                    intent.getStringExtra(EXTRA_TASK_ID),
                    intent.getBooleanExtra(EXTRA_SELECT_ALL, true)
                )
            }
            ACTION_CONFIRM_FILE_SELECTION -> serviceScope.launch {
                confirmFileSelection(intent.getStringExtra(EXTRA_TASK_ID))
            }
            ACTION_STOP_SERVICE -> stopSelf()
        }

        return START_STICKY
    }

    private fun reloadSettings() {
        if (::provider.isInitialized) {
            provider.reloadSettings()
        }
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
        if (taskId == null || !::torrentStateMachine.isInitialized) return
        torrentStateMachine.pauseTaskOnProvider(taskId)
        updateNotification()
    }

    private suspend fun resumeTaskOnProvider(taskId: String?) {
        if (taskId == null || !::torrentStateMachine.isInitialized) return
        torrentStateMachine.resumeTaskOnProvider(taskId)
        updateNotification()
    }

    private suspend fun restartTask(taskId: String?) {
        if (taskId == null || !::torrentStateMachine.isInitialized) return
        torrentStateMachine.restartTask(taskId)
        updateNotification()
    }

    private suspend fun pauseAllOnProvider() {
        if (::torrentStateMachine.isInitialized) {
            torrentStateMachine.pauseAllTasksOnProvider()
            updateNotification()
        }
    }

    private suspend fun resumeAllOnProvider() {
        if (::torrentStateMachine.isInitialized) {
            torrentStateMachine.resumeAllTasksOnProvider()
            updateNotification()
        }
    }

    private suspend fun setProviderFilePriority(taskId: String?, fileId: Int, priority: FilePriority) {
        if (taskId == null || fileId == -1 || !::torrentStateMachine.isInitialized) return
        torrentStateMachine.setFilePriority(taskId, fileId, priority)
    }

    private fun toggleProviderFileSelectionLocally(taskId: String?, fileId: Int) {
        if (taskId == null || fileId == -1 || !::torrentStateMachine.isInitialized) return
        torrentStateMachine.toggleFileSelectionLocally(taskId, fileId)
    }

    private fun toggleAllProviderFileSelectionLocally(taskId: String?, selectAll: Boolean) {
        if (taskId == null || !::torrentStateMachine.isInitialized) return
        torrentStateMachine.toggleAllFilesSelectionLocally(taskId, selectAll)
    }

    private suspend fun confirmFileSelection(taskId: String?) {
        if (taskId == null || !::torrentStateMachine.isInitialized) return
        torrentStateMachine.confirmFileSelection(taskId)
    }

    private fun addTaskFromIntent(intent: Intent) {
        val id = intent.getStringExtra(EXTRA_TORRENT_ID) ?: return
        val uri = intent.getStringExtra(EXTRA_TORRENT_URI) ?: return
        val type = TorrentType.valueOf(intent.getStringExtra(EXTRA_TORRENT_TYPE) ?: TorrentType.MAGNET.name)
        val destinationSubdirectory = intent.getStringExtra(EXTRA_DESTINATION_SUBDIRECTORY)
        val createSubfolderByName = intent.getBooleanExtra(EXTRA_CREATE_SUBFOLDER_BY_NAME, true)
        val notifyOnCompletion = intent.getBooleanExtra(EXTRA_NOTIFY_ON_COMPLETION, false)
        val fileSelectionMode = FileSelectionMode.valueOf(intent.getStringExtra(EXTRA_FILE_SELECTION_MODE) ?: FileSelectionMode.ALL.name)
        val onCompletionIntentUri = intent.getStringExtra(EXTRA_ON_COMPLETION_INTENT_URI)
        val torrentName = intent.getStringExtra(EXTRA_TORRENT_NAME)

        if (DownloadTracker.getTasks().any { it.id == id }) {
            postNotification(
                title = "Torrent already added",
                message = "Torrent $torrentName was not added as it is already in the list of active torrents",
                icon = R.drawable.error_24px
            )
            return
        }

        val task = DownloadTask(
            id = id,
            name = torrentName ?: uri,
            torrent = TorrentDescriptor(type, uri),
            destinationSubdirectory = destinationSubdirectory,
            createSubfolderByName = createSubfolderByName,
            notifyOnCompletion = notifyOnCompletion,
            fileSelectionMode = fileSelectionMode,
            onCompletionIntentUri = onCompletionIntentUri,
            state = TorrentState.ADDING_TO_PROVIDER
        )
        addTask(task)
    }

    private fun addTask(task: DownloadTask) {
        if (::torrentStateMachine.isInitialized) {
            torrentStateMachine.addTask(task)
        } else {
            serviceScope.launch {
                downloadRepository.insertTask(task)
            }
        }
    }

    private suspend fun removeTask(
        taskId: String?,
        deleteFiles: Boolean,
        deleteTorrentFile: Boolean
    ) {
        if (taskId == null) return
        if (::torrentStateMachine.isInitialized) {
            torrentStateMachine.removeTask(
                taskId,
                deleteFiles = deleteFiles,
                deleteTorrentFile = deleteTorrentFile
            )
        } else {
            downloadRepository.deleteTask(taskId)
        }

        stopSelfIfIdle()
    }

    private fun postNotification(
        title: String,
        message: String,
        intent: Intent? = null,
        icon: Int? = null
    ) {
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        val notificationBuilder = NotificationCompat.Builder(this, GENERAL_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(icon ?: android.R.drawable.ic_menu_info_details)
            .setContentTitle(title)
            .setContentText(message)
            .setAutoCancel(true)

        if (intent != null) {
            val pendingIntent = PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            notificationBuilder.setContentIntent(pendingIntent)
        }

        notificationManager.notify(System.currentTimeMillis().toInt(), notificationBuilder.build())
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::torrentStateMachine.isInitialized) {
            torrentStateMachine.stop()
        }
        localDownloadManager.stop()
        if (::provider.isInitialized) {
            provider.stop()
        }
        serviceJob.cancel()
    }

    companion object {
        private const val SERVICE_NOTIFICATION_ID = 1
        private const val SERVICE_NOTIFICATION_CHANNEL_ID = "download_service"
        private const val GENERAL_NOTIFICATION_CHANNEL_ID = "general"
        const val ACTION_BIND_AIDL = "com.felixbrucker.torrenthttpdownloader.ITorrentDownloadService"
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
        const val ACTION_SET_PROVIDER_FILE_PRIORITY = "ACTION_SET_PROVIDER_FILE_PRIORITY"
        const val ACTION_TOGGLE_PROVIDER_FILE_SELECTION = "ACTION_TOGGLE_PROVIDER_FILE_SELECTION"
        const val ACTION_TOGGLE_ALL_PROVIDER_FILE_SELECTION = "ACTION_TOGGLE_ALL_PROVIDER_FILE_SELECTION"
        const val ACTION_CONFIRM_FILE_SELECTION = "ACTION_CONFIRM_FILE_SELECTION"
        const val EXTRA_TASK_ID = "EXTRA_TASK_ID"
        const val EXTRA_FILE_LINK = "EXTRA_FILE_LINK"
        const val EXTRA_FILE_ID = "EXTRA_FILE_ID"
        const val EXTRA_SELECT_ALL = "EXTRA_SELECT_ALL"
        const val EXTRA_PRIORITY = "EXTRA_PRIORITY"
        const val EXTRA_TORRENT_ID = "EXTRA_TORRENT_ID"
        const val EXTRA_TORRENT_URI = "EXTRA_TORRENT_URI"
        const val EXTRA_TORRENT_TYPE = "EXTRA_TORRENT_TYPE"
        const val EXTRA_DESTINATION_SUBDIRECTORY = "EXTRA_DESTINATION_SUBDIRECTORY"
        const val EXTRA_CREATE_SUBFOLDER_BY_NAME = "EXTRA_CREATE_SUBFOLDER_BY_NAME"
        const val EXTRA_NOTIFY_ON_COMPLETION = "EXTRA_NOTIFY_ON_COMPLETION"
        const val EXTRA_FILE_SELECTION_MODE = "EXTRA_FILE_SELECTION_MODE"
        const val EXTRA_ON_COMPLETION_INTENT_URI = "EXTRA_ON_COMPLETION_INTENT_URI"
        const val EXTRA_TORRENT_NAME = "EXTRA_TORRENT_NAME"
        const val EXTRA_DELETE_FILES = "EXTRA_DELETE_FILES"
        const val EXTRA_DELETE_TORRENT_FILE = "EXTRA_DELETE_TORRENT_FILE"
    }
}

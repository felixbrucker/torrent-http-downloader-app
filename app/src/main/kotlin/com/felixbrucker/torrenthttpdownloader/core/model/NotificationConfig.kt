package com.felixbrucker.torrenthttpdownloader.core.model

import com.felixbrucker.torrenthttpdownloader.core.data.providers.ProviderTorrentState

data class NotificationConfig(
    val taskCount: Int,
    val totalSpeed: Long,
    val avgProgress: Int,
    val totalDownloadedBytes: Long,
    val totalBytes: Long,
    val runningLocalDownloads: Int,
    val totalLocalDownloads: Int,
    val completedLocalDownloads: Int,
    val hasRunningProviderTasks: Boolean,
    val anyLocalDownloading: Boolean,
    val anyLocalPaused: Boolean,
    val anyDownloadingOnProvider: Boolean,
    val anyPausedOnProvider: Boolean,
    val providerSupportsPauseResume: Boolean,
)

fun List<DownloadTask>.toNotificationConfig(providerSupportsPauseResume: Boolean = false): NotificationConfig {
    var totalSpeed = 0L
    var totalProgress = 0
    var totalDownloadedBytes = 0L
    var totalBytes = 0L
    var runningLocalDownloads = 0
    var totalLocalDownloads = 0
    var completedLocalDownloads = 0

    for (task in this) {
        totalSpeed += task.overallDownloadSpeed
        totalProgress += task.overallProgress
        totalDownloadedBytes += task.downloadedBytes
        totalBytes += task.totalBytes
        runningLocalDownloads += task.files.count { it.state == LocalDownloadState.DOWNLOADING }
        completedLocalDownloads += task.files.count { it.state == LocalDownloadState.COMPLETED }
        totalLocalDownloads += task.files.size
    }

    val avgProgress = if (isNotEmpty()) totalProgress / size else 0
    val hasRunningProviderTasks = any {
        it.location == TaskLocation.PROVIDER &&
                it.providerTorrentInfo?.state != ProviderTorrentState.PAUSED &&
                it.providerTorrentInfo?.state != ProviderTorrentState.COMPLETED
    }
    val anyLocalDownloading = any { task ->
        task.files.any { it.state == LocalDownloadState.DOWNLOADING || it.state == LocalDownloadState.PENDING }
    }
    val anyLocalPaused = any { task ->
        task.files.any { it.state == LocalDownloadState.PAUSED }
    }
    val anyDownloadingOnProvider = any { it.providerTorrentInfo?.state == ProviderTorrentState.DOWNLOADING }
    val anyPausedOnProvider = any { it.providerTorrentInfo?.state == ProviderTorrentState.PAUSED }

    return NotificationConfig(
        taskCount = size,
        totalSpeed = totalSpeed,
        avgProgress = avgProgress,
        totalDownloadedBytes = totalDownloadedBytes,
        totalBytes = totalBytes,
        runningLocalDownloads = runningLocalDownloads,
        totalLocalDownloads = totalLocalDownloads,
        completedLocalDownloads = completedLocalDownloads,
        hasRunningProviderTasks = hasRunningProviderTasks,
        anyLocalDownloading = anyLocalDownloading,
        anyLocalPaused = anyLocalPaused,
        anyDownloadingOnProvider = anyDownloadingOnProvider,
        anyPausedOnProvider = anyPausedOnProvider,
        providerSupportsPauseResume = providerSupportsPauseResume,
    )
}

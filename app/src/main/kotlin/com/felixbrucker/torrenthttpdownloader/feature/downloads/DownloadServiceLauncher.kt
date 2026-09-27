package com.felixbrucker.torrenthttpdownloader.feature.downloads

import android.content.Context
import android.content.Intent
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import com.felixbrucker.torrenthttpdownloader.DownloadService

class DownloadServiceLauncher @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    fun removeTask(taskId: String, deleteFiles: Boolean, deleteTorrentFile: Boolean) {
        val intent = Intent(context, DownloadService::class.java).apply {
            action = DownloadService.ACTION_REMOVE_TASK
            putExtra(DownloadService.EXTRA_TASK_ID, taskId)
            putExtra(DownloadService.EXTRA_DELETE_FILES, deleteFiles)
            putExtra(DownloadService.EXTRA_DELETE_TORRENT_FILE, deleteTorrentFile)
        }
        context.startService(intent)
    }

    fun resumeAll() {
        context.startService(Intent(context, DownloadService::class.java).apply {
            action = DownloadService.ACTION_RESUME_ALL_LOCAL_DOWNLOADS
        })
        context.startService(Intent(context, DownloadService::class.java).apply {
            action = DownloadService.ACTION_RESUME_ALL_ON_PROVIDER
        })
    }

    fun pauseAll() {
        context.startService(Intent(context, DownloadService::class.java).apply {
            action = DownloadService.ACTION_PAUSE_ALL_LOCAL_DOWNLOADS
        })
        context.startService(Intent(context, DownloadService::class.java).apply {
            action = DownloadService.ACTION_PAUSE_ALL_ON_PROVIDER
        })
    }
}

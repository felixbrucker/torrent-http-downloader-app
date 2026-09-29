package com.felixbrucker.torrenthttpdownloader.feature.downloads

import android.content.Context
import android.content.Intent
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import com.felixbrucker.torrenthttpdownloader.DownloadService
import com.felixbrucker.torrenthttpdownloader.core.data.providers.FilePriority
import com.felixbrucker.torrenthttpdownloader.feature.addtorrent.AddTorrentConfig

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

    fun resumeAllLocalDownloads() {
        context.startService(Intent(context, DownloadService::class.java).apply {
            action = DownloadService.ACTION_RESUME_ALL_LOCAL_DOWNLOADS
        })
    }

    fun resumeAllOnProvider() {
        context.startService(Intent(context, DownloadService::class.java).apply {
            action = DownloadService.ACTION_RESUME_ALL_ON_PROVIDER
        })
    }

    fun pauseAllLocalDownloads() {
        context.startService(Intent(context, DownloadService::class.java).apply {
            action = DownloadService.ACTION_PAUSE_ALL_LOCAL_DOWNLOADS
        })
    }

    fun pauseAllOnProvider() {
        context.startService(Intent(context, DownloadService::class.java).apply {
            action = DownloadService.ACTION_PAUSE_ALL_ON_PROVIDER
        })
    }

    fun toggleAllProviderFileSelection(taskId: String, selectAll: Boolean) {
        val intent = Intent(context, DownloadService::class.java).apply {
            action = DownloadService.ACTION_TOGGLE_ALL_PROVIDER_FILE_SELECTION
            putExtra(DownloadService.EXTRA_TASK_ID, taskId)
            putExtra(DownloadService.EXTRA_SELECT_ALL, selectAll)
        }
        context.startService(intent)
    }

    fun confirmFileSelection(taskId: String) {
        val intent = Intent(context, DownloadService::class.java).apply {
            action = DownloadService.ACTION_CONFIRM_FILE_SELECTION
            putExtra(DownloadService.EXTRA_TASK_ID, taskId)
        }
        context.startService(intent)
    }

    fun pauseTaskOnProvider(taskId: String) {
        val intent = Intent(context, DownloadService::class.java).apply {
            action = DownloadService.ACTION_PAUSE_TASK_ON_PROVIDER
            putExtra(DownloadService.EXTRA_TASK_ID, taskId)
        }
        context.startService(intent)
    }

    fun resumeTaskOnProvider(taskId: String) {
        val intent = Intent(context, DownloadService::class.java).apply {
            action = DownloadService.ACTION_RESUME_TASK_ON_PROVIDER
            putExtra(DownloadService.EXTRA_TASK_ID, taskId)
        }
        context.startService(intent)
    }

    fun pauseTaskLocalDownloads(taskId: String) {
        val intent = Intent(context, DownloadService::class.java).apply {
            action = DownloadService.ACTION_PAUSE_TASK_LOCAL_DOWNLOADS
            putExtra(DownloadService.EXTRA_TASK_ID, taskId)
        }
        context.startService(intent)
    }

    fun resumeTaskLocalDownloads(taskId: String) {
        val intent = Intent(context, DownloadService::class.java).apply {
            action = DownloadService.ACTION_RESUME_TASK_LOCAL_DOWNLOADS
            putExtra(DownloadService.EXTRA_TASK_ID, taskId)
        }
        context.startService(intent)
    }

    fun restartTask(taskId: String) {
        val intent = Intent(context, DownloadService::class.java).apply {
            action = DownloadService.ACTION_RESTART_TASK
            putExtra(DownloadService.EXTRA_TASK_ID, taskId)
        }
        context.startService(intent)
    }

    fun resumeLocalFileDownload(taskId: String, fileLink: String) {
        val intent = Intent(context, DownloadService::class.java).apply {
            action = DownloadService.ACTION_RESUME_LOCAL_FILE_DOWNLOAD
            putExtra(DownloadService.EXTRA_TASK_ID, taskId)
            putExtra(DownloadService.EXTRA_FILE_LINK, fileLink)
        }
        context.startService(intent)
    }

    fun pauseLocalFileDownload(taskId: String, fileLink: String) {
        val intent = Intent(context, DownloadService::class.java).apply {
            action = DownloadService.ACTION_PAUSE_LOCAL_FILE_DOWNLOAD
            putExtra(DownloadService.EXTRA_TASK_ID, taskId)
            putExtra(DownloadService.EXTRA_FILE_LINK, fileLink)
        }
        context.startService(intent)
    }

    fun toggleProviderFileSelection(taskId: String, fileId: Int) {
        val intent = Intent(context, DownloadService::class.java).apply {
            action = DownloadService.ACTION_TOGGLE_PROVIDER_FILE_SELECTION
            putExtra(DownloadService.EXTRA_TASK_ID, taskId)
            putExtra(DownloadService.EXTRA_FILE_ID, fileId)
        }
        context.startService(intent)
    }

    fun setProviderFilePriority(taskId: String, fileId: Int, priority: FilePriority) {
        val intent = Intent(context, DownloadService::class.java).apply {
            action = DownloadService.ACTION_SET_PROVIDER_FILE_PRIORITY
            putExtra(DownloadService.EXTRA_TASK_ID, taskId)
            putExtra(DownloadService.EXTRA_FILE_ID, fileId)
            putExtra(DownloadService.EXTRA_PRIORITY, priority.name)
        }
        context.startService(intent)
    }

    fun addTask(config: AddTorrentConfig) {
        val intent = Intent(context, DownloadService::class.java).apply {
            action = DownloadService.ACTION_ADD_TASK
            putExtra(DownloadService.EXTRA_TORRENT_ID, config.id)
            putExtra(DownloadService.EXTRA_TORRENT_URI, config.uri)
            putExtra(DownloadService.EXTRA_TORRENT_TYPE, config.type.name)
            putExtra(DownloadService.EXTRA_DESTINATION_SUBDIRECTORY, config.destinationSubdirectory)
            putExtra(DownloadService.EXTRA_CREATE_SUBFOLDER_BY_NAME, config.createSubfolderByName)
            putExtra(DownloadService.EXTRA_NOTIFY_ON_COMPLETION, config.notifyOnCompletion)
            putExtra(DownloadService.EXTRA_FILE_SELECTION_MODE, config.fileSelectionMode?.name)
            putExtra(DownloadService.EXTRA_TORRENT_NAME, config.name)
        }
        context.startService(intent)
    }

    fun reloadSettings() {
        val intent = Intent(context, DownloadService::class.java).apply {
            action = DownloadService.ACTION_RELOAD_SETTINGS
        }
        context.startService(intent)
    }
}

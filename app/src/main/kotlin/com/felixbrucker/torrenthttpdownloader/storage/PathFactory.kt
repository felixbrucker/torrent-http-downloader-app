package com.felixbrucker.torrenthttpdownloader.storage

import android.content.Context
import android.os.Environment
import com.felixbrucker.torrenthttpdownloader.TorrentHttpDownloaderApp.Companion.getContext
import com.felixbrucker.torrenthttpdownloader.cleanedForUseAsPath
import com.felixbrucker.torrenthttpdownloader.createDirectoryRecursivelyIfNotExists
import com.felixbrucker.torrenthttpdownloader.hash
import com.felixbrucker.torrenthttpdownloader.models.DownloadTask
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PathFactory @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun getResumeDataPath(providerId: String): File {
        return File(getResumeDataDirectory(), providerId)
    }

    fun getResumeDataDirectory(): File {
        return File(
            context.cacheDir,
            "resume"
        ).apply { createDirectoryRecursivelyIfNotExists() }
    }

    fun getTemporaryTorrentFileDirectory(): File {
        return File(
            context.cacheDir,
            "torrents"
        ).apply { createDirectoryRecursivelyIfNotExists() }
    }

    fun getScopedTemporaryDirectory(taskName: String): File {
        val subDirName = if (taskName.length > 127) {
            taskName.hash()
        } else {
            taskName.cleanedForUseAsPath()
        }

        return File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath,
            "tmp/${subDirName}"
        ).apply { createDirectoryRecursivelyIfNotExists() }
    }

    fun getScopedDestinationDirectory(task: DownloadTask): File {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val baseDir = if (!task.destinationSubdirectory.isNullOrEmpty()) {
            File(downloadsDir, task.destinationSubdirectory)
        } else {
            downloadsDir
        }

        val result = if (task.createSubfolderByName) {
            val subDirName = if (task.name.length > 127) {
                task.name.hash()
            } else {
                task.name.cleanedForUseAsPath()
            }
            File(baseDir, subDirName)
        } else {
            baseDir
        }
        result.createDirectoryRecursivelyIfNotExists()
        return result
    }

    companion object {
        fun getResumeDataPath(providerId: String): File {
            return PathFactory(getContext()).getResumeDataPath(providerId)
        }

        fun getResumeDataDirectory(): File {
            return PathFactory(getContext()).getResumeDataDirectory()
        }

        fun getTemporaryTorrentFileDirectory(): File {
            return PathFactory(getContext()).getTemporaryTorrentFileDirectory()
        }

        fun getScopedTemporaryDirectory(taskName: String): File {
            return PathFactory(getContext()).getScopedTemporaryDirectory(taskName)
        }

        fun getScopedDestinationDirectory(task: DownloadTask): File {
            return PathFactory(getContext()).getScopedDestinationDirectory(task)
        }
    }
}

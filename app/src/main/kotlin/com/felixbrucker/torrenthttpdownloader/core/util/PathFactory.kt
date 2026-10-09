package com.felixbrucker.torrenthttpdownloader.core.util

import android.content.Context
import android.os.Environment
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import com.felixbrucker.torrenthttpdownloader.core.model.DownloadTaskEntity
import com.felixbrucker.torrenthttpdownloader.extensions.cleanedForUseAsPath
import com.felixbrucker.torrenthttpdownloader.extensions.hash

@Singleton
class PathFactory @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    fun getResumeDataPath(providerId: String): File {
        return File(getResumeDataDirectory(), providerId)
    }

    fun getResumeDataDirectory(): File {
        return File(
            context.cacheDir,
            "resume"
        )
    }

    fun getTemporaryTorrentFileDirectory(): File {
        return File(
            context.cacheDir,
            "torrents"
        )
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
        )
    }

    fun getScopedDestinationDirectory(task: DownloadTaskEntity): File {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val baseDir = if (!task.destinationSubdirectory.isNullOrEmpty()) {
            File(downloadsDir, task.destinationSubdirectory)
        } else {
            downloadsDir
        }

        return if (task.createSubfolderByName) {
            val subDirName = if (task.name.length > 127) {
                task.name.hash()
            } else {
                task.name.cleanedForUseAsPath()
            }
            File(baseDir, subDirName)
        } else {
            baseDir
        }
    }
}

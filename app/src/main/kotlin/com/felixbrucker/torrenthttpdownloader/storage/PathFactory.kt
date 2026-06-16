package com.felixbrucker.torrenthttpdownloader.storage

import android.os.Environment
import com.felixbrucker.torrenthttpdownloader.cleanedForUseAsPath
import com.felixbrucker.torrenthttpdownloader.hash
import com.felixbrucker.torrenthttpdownloader.models.DownloadTask
import java.io.File

class PathFactory {
    companion object {
        fun getResumeDataPath(id: String): File {
            return File(getResumeDataDirectory(), id)
        }

        fun getResumeDataDirectory(): File {
            return File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "tmp/resume"
            )
        }
        fun getTemporaryTorrentFileDirectory(): File {
            return File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "tmp/torrents"
            )
        }
        fun getScopedTemporaryDirectory(taskName: String): File {
            val subDirName = if (taskName.length > 64) {
                taskName.hash()
            } else {
                taskName.cleanedForUseAsPath()
            }

            return File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath,
                "tmp/${subDirName}"
            )
        }

        fun getScopedDestinationDirectory(task: DownloadTask): File {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val baseDir = if (!task.destinationSubdirectory.isNullOrEmpty()) {
                File(downloadsDir, task.destinationSubdirectory)
            } else {
                downloadsDir
            }

            return if (task.createSubfolderByName) {
                File(baseDir, task.name.cleanedForUseAsPath())
            } else {
                baseDir
            }
        }
    }
}
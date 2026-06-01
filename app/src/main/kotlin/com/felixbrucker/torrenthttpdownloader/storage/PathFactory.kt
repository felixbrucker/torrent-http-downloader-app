package com.felixbrucker.torrenthttpdownloader.storage

import android.os.Environment
import com.felixbrucker.torrenthttpdownloader.cleanedForUseAsPath
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
        fun getScopedTemporaryDirectory(taskName: String): File {
            return File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath,
                "tmp/${taskName.cleanedForUseAsPath()}"
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
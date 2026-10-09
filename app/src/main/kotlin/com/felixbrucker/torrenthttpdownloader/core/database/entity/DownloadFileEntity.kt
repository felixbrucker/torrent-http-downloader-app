package com.felixbrucker.torrenthttpdownloader.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import com.felixbrucker.torrenthttpdownloader.core.data.FileProgressInfo
import com.felixbrucker.torrenthttpdownloader.core.model.DownloadFile
import com.felixbrucker.torrenthttpdownloader.core.model.LocalDownloadState

@Entity(
    tableName = "download_files",
    primaryKeys = ["taskId", "link"],
    foreignKeys = [
        ForeignKey(
            entity = DownloadTaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["taskId"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE,
        )
    ],
    indices = [
        Index(value = ["taskId"]),
    ]
)
data class DownloadFileEntity(
    val taskId: String,
    val link: String, // The original provider link
    val unrestrictedLink: String? = null, // The download link, might need to be regenerated
    val state: LocalDownloadState = LocalDownloadState.PENDING,
    val stateDescription: String? = null,
    val filePath: String? = null,
    val fileName: String? = null,
    val totalBytes: Long = 0,
) {
    fun toDownloadFile(fileProgressInfo: FileProgressInfo? = null) = DownloadFile(
        link = link,
        unrestrictedLink = unrestrictedLink,
        state = state,
        stateDescription = stateDescription,
        progress = fileProgressInfo?.progress ?: 0,
        filePath = filePath,
        speed = fileProgressInfo?.speed ?: 0,
        totalBytes = totalBytes,
        downloadedBytes = fileProgressInfo?.downloadedBytes ?: 0,
        lastBytes = fileProgressInfo?.lastBytes ?: 0,
        lastTimestamp = fileProgressInfo?.lastTimestamp ?: System.currentTimeMillis(),
    )
}

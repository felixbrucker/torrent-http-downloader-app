package com.felixbrucker.torrenthttpdownloader.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "download_final_files",
    primaryKeys = ["taskId", "filePath"],
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
        Index(value = ["taskId"])
    ]
)
data class DownloadFinalFileEntity(
    val taskId: String,
    val filePath: String,
)

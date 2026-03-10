package com.felixbrucker.torrenthttpdownloader.models

import kotlin.math.max

enum class TorrentState {
    ADDING_TO_REAL_DEBRID,
    WAITING_FOR_FILE_SELECTION,
    SELECTING_FILES,
    WAITING_FOR_REAL_DEBRID_DOWNLOAD,
    DOWNLOADING_LOCALLY,
    DELETING_FROM_REAL_DEBRID,
    CHECKING_FOR_ARCHIVES,
    EXTRACTING_ARCHIVES,
    MOVING_TO_DESTINATION,
    COMPLETED,
    ERROR
}

enum class LocalDownloadState {
    PENDING,
    DOWNLOADING,
    PAUSED,
    COMPLETED,
    ERROR,
}

enum class TorrentType {
    MAGNET,
    TORRENT
}

data class TorrentDescriptor(
    val type: TorrentType,
    val path: String
)

data class DownloadFile(
    val link: String, // The original Real-Debrid link
    val state: LocalDownloadState = LocalDownloadState.PENDING,
    val stateDescription: String? = null,
    val progress: Int = 0,
    val filePath: String? = null,
    val speed: Long = 0, // Current speed in B/s
    val totalBytes: Long = 0,
    val downloadedBytes: Long = 0,
    @Transient val lastBytes: Long = 0,
    @Transient val lastTimestamp: Long = System.currentTimeMillis()
)

data class DownloadTask(
    val id: String, // Can be the initial magnet URI, then becomes the Real-Debrid Torrent ID
    val name: String,
    val torrent: TorrentDescriptor,
    val state: TorrentState = TorrentState.ADDING_TO_REAL_DEBRID,
    val rdState: String? = null,
    val rdProgress: Int = 0, // Progress from Real-Debrid
    val rdSpeed: Long = 0, // Speed from Real-Debrid in B/s
    val rdDownloadedBytes: Long = 0,
    val rdTotalBytes: Long = 0,
    val files: List<DownloadFile> = listOf(),
    val errorMessage: String? = null,
    val destinationSubdirectory: String? = null,
    val createSubfolderByName: Boolean = true
) {
    val overallProgress: Int
        get() {
            return if (state.ordinal < TorrentState.DOWNLOADING_LOCALLY.ordinal) {
                rdProgress
            } else {
                val totalSize = totalBytes
                if (totalSize == 0L) 0 else ((downloadedBytes * 100) / totalSize).toInt()
            }
        }
    val overallSpeed: Long
        get() {
            return if (state.ordinal < TorrentState.DOWNLOADING_LOCALLY.ordinal) {
                rdSpeed
            } else {
                files.sumOf { it.speed }
            }
        }

    val totalBytes: Long
        get() {
            return if (state.ordinal < TorrentState.DOWNLOADING_LOCALLY.ordinal) {
                rdTotalBytes
            } else {
                max(files.sumOf { it.totalBytes }, rdTotalBytes)
            }
        }

    val downloadedBytes: Long
        get() {
            return if (state.ordinal < TorrentState.DOWNLOADING_LOCALLY.ordinal) {
                rdDownloadedBytes
            } else {
                files.sumOf { it.downloadedBytes }
            }
        }
}
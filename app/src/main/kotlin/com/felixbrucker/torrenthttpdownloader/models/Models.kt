package com.felixbrucker.torrenthttpdownloader.models

import kotlin.math.max

enum class TorrentState {
    ADDING_TO_PROVIDER,
    WAITING_FOR_FILE_SELECTION,
    SELECTING_FILES,
    WAITING_FOR_PROVIDER_DOWNLOAD,
    POPULATING_FILE_INFOS,
    DOWNLOADING_LOCALLY,
    DELETING_FROM_PROVIDER,
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

enum class TaskLocation {
    PROVIDER,
    LOCAL
}


data class TorrentDescriptor(
    val type: TorrentType,
    val path: String
)

data class DownloadFile(
    val link: String, // The original provider link
    val unrestrictedLink: String? = null, // The download link, might need to be regenerated
    val state: LocalDownloadState = LocalDownloadState.PENDING,
    val stateDescription: String? = null,
    val progress: Int = 0,
    val filePath: String? = null,
    val speed: Long = 0, // Current speed in B/s
    val totalBytes: Long = 0,
    val downloadedBytes: Long = 0,
    @Transient val lastBytes: Long = 0,
    @Transient val lastTimestamp: Long = System.currentTimeMillis()
) {
    val fileName get() = filePath?.substringAfterLast("/")
}

data class DownloadTask(
    val id: String, // Can be the initial magnet URI, then becomes the provider Torrent ID
    val providerId: String? = null, // Provider Torrent ID
    val name: String,
    val torrent: TorrentDescriptor,
    val state: TorrentState = TorrentState.ADDING_TO_PROVIDER,
    val providerState: String? = null,
    val providerProgress: Int = 0, // Progress from Provider
    val providerSpeed: Long = 0, // Speed from Provider in B/s
    val providerDownloadedBytes: Long = 0,
    val providerTotalBytes: Long = 0,
    val files: List<DownloadFile> = listOf(),
    val errorMessage: String? = null,
    val destinationSubdirectory: String? = null,
    val createSubfolderByName: Boolean = true
) {
    val overallProgress: Int
        get() {
            return if (location == TaskLocation.PROVIDER) {
                providerProgress
            } else {
                val totalSize = totalBytes
                if (totalSize == 0L) 0 else ((downloadedBytes * 100) / totalSize).toInt()
            }
        }
    val overallSpeed: Long
        get() {
            return if (location == TaskLocation.PROVIDER) {
                providerSpeed
            } else {
                files.sumOf { it.speed }
            }
        }

    val totalBytes: Long
        get() {
            return if (location == TaskLocation.PROVIDER) {
                providerTotalBytes
            } else {
                max(files.sumOf { it.totalBytes }, providerTotalBytes)
            }
        }

    val downloadedBytes: Long
        get() {
            return if (location == TaskLocation.PROVIDER) {
                providerDownloadedBytes
            } else {
                files.sumOf { it.downloadedBytes }
            }
        }

    val location: TaskLocation get() {
        return if (state.ordinal < TorrentState.DOWNLOADING_LOCALLY.ordinal) {
            TaskLocation.PROVIDER
        } else {
            TaskLocation.LOCAL
        }
    }
}
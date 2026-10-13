package com.felixbrucker.torrenthttpdownloader.core.database.entity

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.felixbrucker.torrenthttpdownloader.core.data.providers.ProviderTorrentInfo
import com.felixbrucker.torrenthttpdownloader.core.model.DownloadFile
import com.felixbrucker.torrenthttpdownloader.core.model.DownloadTask
import com.felixbrucker.torrenthttpdownloader.core.model.FileSelectionMode
import com.felixbrucker.torrenthttpdownloader.core.model.TorrentDescriptor
import com.felixbrucker.torrenthttpdownloader.core.model.TorrentState
import com.felixbrucker.torrenthttpdownloader.core.model.TorrentType
import com.felixbrucker.torrenthttpdownloader.core.util.PathFactory
import com.felixbrucker.torrenthttpdownloader.extensions.asFile
import com.felixbrucker.torrenthttpdownloader.extensions.deleteIfExists

@Entity(
    tableName = "download_tasks",
    indices = [
        Index(value = ["providerId"]),
        Index(value = ["state"]),
        Index(value = ["displayOrder"]),
    ],
)
data class DownloadTaskEntity(
    @PrimaryKey val id: String,
    val providerId: String? = null, // Provider Torrent ID, may be same as id
    val name: String,
    @Embedded(prefix = "torrent_") val torrent: TorrentDescriptor,
    val state: TorrentState = TorrentState.ADDING_TO_PROVIDER,
    val errorMessage: String? = null,
    val destinationSubdirectory: String? = null,
    val createSubfolderByName: Boolean = true,
    val notifyOnCompletion: Boolean = false,
    val fileSelectionMode: FileSelectionMode = FileSelectionMode.ALL,
    val onCompletionIntentUri: String? = null,
    val displayOrder: Double = 10.0,
) {
    fun toDownloadTask(
        providerTorrentInfo: ProviderTorrentInfo? = null,
        files: List<DownloadFile> = emptyList(),
    ) = DownloadTask(
        id = id,
        providerId = providerId,
        name = name,
        torrent = torrent,
        state = state,
        providerTorrentInfo = providerTorrentInfo,
        files = files,
        errorMessage = errorMessage,
        destinationSubdirectory = destinationSubdirectory,
        createSubfolderByName = createSubfolderByName,
        notifyOnCompletion = notifyOnCompletion,
        fileSelectionMode = fileSelectionMode,
        onCompletionIntentUri = onCompletionIntentUri,
    )

    fun removeTorrentFile() {
        if (torrent.type == TorrentType.TORRENT_FILE) {
            torrent.uri.asFile().deleteIfExists()
        }
    }

    fun removeResumeData(pathFactory: PathFactory) {
        if (providerId != null) {
            pathFactory.getResumeDataPath(providerId).deleteIfExists()
        }
    }

    fun removeScopedTemporaryDirectory(pathFactory: PathFactory) {
        val tempDir = pathFactory.getScopedTemporaryDirectory(name)
        if (tempDir.exists()) {
            tempDir.deleteRecursively()
        }
    }
}

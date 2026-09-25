package com.felixbrucker.torrenthttpdownloader.data.repository

import com.felixbrucker.torrenthttpdownloader.data.database.DownloadDao
import com.felixbrucker.torrenthttpdownloader.data.database.DownloadFileEntity
import com.felixbrucker.torrenthttpdownloader.data.database.DownloadTaskEntity
import com.felixbrucker.torrenthttpdownloader.data.database.ProviderTorrentFileEntity
import com.felixbrucker.torrenthttpdownloader.data.database.ProviderTorrentInfoEntity
import com.felixbrucker.torrenthttpdownloader.data.database.ProviderTorrentLinkEntity
import com.felixbrucker.torrenthttpdownloader.data.database.TorrentDescriptorEntity
import com.felixbrucker.torrenthttpdownloader.models.DownloadTask
import com.felixbrucker.torrenthttpdownloader.models.LocalDownloadState
import com.felixbrucker.torrenthttpdownloader.models.TorrentState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadRepository @Inject constructor(
    private val downloadDao: DownloadDao
) {
    val tasksFlow: Flow<List<DownloadTask>>
        get() = downloadDao.getTasksFlow().map { list ->
            list.map { details -> details.toDomainModel() }
        }

    suspend fun getTaskById(id: String): DownloadTask? {
        val details = downloadDao.getTaskById(id) ?: return null
        return details.toDomainModel()
    }

    suspend fun saveTask(task: DownloadTask) {
        val taskEntity = DownloadTaskEntity(
            id = task.id,
            providerId = task.providerId,
            name = task.name,
            torrent = TorrentDescriptorEntity(
                type = task.torrent.type,
                uri = task.torrent.uri
            ),
            state = task.state,
            errorMessage = task.errorMessage,
            destinationSubdirectory = task.destinationSubdirectory,
            createSubfolderByName = task.createSubfolderByName,
            notifyOnCompletion = task.notifyOnCompletion,
            fileSelectionMode = task.fileSelectionMode,
            onCompletionIntentUri = task.onCompletionIntentUri
        )
        downloadDao.insertTask(taskEntity)

        downloadDao.deleteFilesForTask(task.id)
        val fileEntities = task.files.map { file ->
            DownloadFileEntity(
                taskId = task.id,
                link = file.link,
                unrestrictedLink = file.unrestrictedLink,
                state = file.state,
                stateDescription = file.stateDescription,
                progress = file.progress,
                filePath = file.filePath,
                speed = file.speed,
                totalBytes = file.totalBytes,
                downloadedBytes = file.downloadedBytes
            )
        }
        if (fileEntities.isNotEmpty()) {
            downloadDao.insertFiles(fileEntities)
        }

        downloadDao.deleteProviderInfoForTask(task.id)
        val info = task.providerTorrentInfo
        if (info != null) {
            val infoEntity = ProviderTorrentInfoEntity(
                id = info.id,
                taskId = task.id,
                name = info.name,
                state = info.state,
                status = info.status,
                progress = info.progress,
                totalSizeInBytes = info.totalSizeInBytes,
                downloadedBytes = info.downloadedBytes,
                downloadSpeed = info.downloadSpeed,
                uploadSpeed = info.uploadSpeed,
                seeders = info.seeders,
                leechers = info.leechers,
                peers = info.peers,
                totalPeers = info.totalPeers
            )
            downloadDao.insertProviderInfo(infoEntity)

            downloadDao.deleteProviderFiles(info.id)
            val pFileEntities = info.files.map { file ->
                ProviderTorrentFileEntity(
                    providerInfoId = info.id,
                    fileId = file.id,
                    path = file.path,
                    size = file.size,
                    isSelected = file.isSelected,
                    progress = file.progress,
                    downloadedBytes = file.downloadedBytes,
                    priority = file.priority
                )
            }
            if (pFileEntities.isNotEmpty()) {
                downloadDao.insertProviderFiles(pFileEntities)
            }

            downloadDao.deleteProviderLinks(info.id)
            val pLinkEntities = info.links.map { link ->
                ProviderTorrentLinkEntity(
                    providerInfoId = info.id,
                    link = link
                )
            }
            if (pLinkEntities.isNotEmpty()) {
                downloadDao.insertProviderLinks(pLinkEntities)
            }
        }
    }

    suspend fun saveTasks(tasks: List<DownloadTask>) {
        for (task in tasks) {
            saveTask(task)
        }
    }

    suspend fun updateTaskState(id: String, state: TorrentState, errorMessage: String? = null) {
        downloadDao.updateTaskState(id, state, errorMessage)
    }

    suspend fun updateFileProgress(
        taskId: String,
        link: String,
        state: LocalDownloadState,
        progress: Int,
        speed: Long,
        downloadedBytes: Long
    ) {
        downloadDao.updateFileProgress(taskId, link, state, progress, speed, downloadedBytes)
    }

    suspend fun deleteTask(id: String) {
        downloadDao.deleteTaskById(id)
    }

    suspend fun deleteAllTasks() {
        downloadDao.deleteAllTasks()
    }
}

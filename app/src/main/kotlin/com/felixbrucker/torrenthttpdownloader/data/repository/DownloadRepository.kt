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
import com.felixbrucker.torrenthttpdownloader.providers.ProviderTorrentInfo
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

    suspend fun insertTask(task: DownloadTask) {
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

        val fileEntities = task.files.map { file ->
            DownloadFileEntity(
                taskId = task.id,
                link = file.link,
                unrestrictedLink = file.unrestrictedLink,
                state = file.state,
                stateDescription = file.stateDescription,
                filePath = file.filePath,
                speed = file.speed,
                totalBytes = file.totalBytes,
                downloadedBytes = file.downloadedBytes
            )
        }
        if (fileEntities.isNotEmpty()) {
            downloadDao.insertFiles(fileEntities)
        }

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

    suspend fun updateTaskState(id: String, state: TorrentState, errorMessage: String? = null) {
        downloadDao.updateTaskState(id, state, errorMessage)
    }

    suspend fun updateTaskStateAndProviderId(id: String, state: TorrentState, providerId: String?) {
        downloadDao.updateTaskStateAndProviderId(id, state, providerId)
    }

    suspend fun getTaskIdsByProviderState(state: com.felixbrucker.torrenthttpdownloader.providers.ProviderTorrentState): List<String> {
        return downloadDao.getTaskIdsByProviderState(state)
    }

    suspend fun getFileByLink(taskId: String, link: String): com.felixbrucker.torrenthttpdownloader.models.DownloadFile? {
        val entity = downloadDao.getFileByLink(taskId, link) ?: return null
        return com.felixbrucker.torrenthttpdownloader.models.DownloadFile(
            link = entity.link,
            unrestrictedLink = entity.unrestrictedLink,
            state = entity.state,
            stateDescription = entity.stateDescription,
            filePath = entity.filePath,
            speed = entity.speed,
            totalBytes = entity.totalBytes,
            downloadedBytes = entity.downloadedBytes
        )
    }

    suspend fun updateProviderInfo(taskId: String, info: ProviderTorrentInfo) {
        val infoEntity = ProviderTorrentInfoEntity(
            id = info.id,
            taskId = taskId,
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

    suspend fun insertFiles(taskId: String, files: List<com.felixbrucker.torrenthttpdownloader.models.DownloadFile>) {
        val fileEntities = files.map { file ->
            DownloadFileEntity(
                taskId = taskId,
                link = file.link,
                unrestrictedLink = file.unrestrictedLink,
                state = file.state,
                stateDescription = file.stateDescription,
                filePath = file.filePath,
                speed = file.speed,
                totalBytes = file.totalBytes,
                downloadedBytes = file.downloadedBytes
            )
        }
        if (fileEntities.isNotEmpty()) {
            downloadDao.insertFiles(fileEntities)
        }
    }

    suspend fun deleteProviderInfo(taskId: String) {
        downloadDao.deleteProviderInfoForTask(taskId)
    }

    suspend fun deleteFiles(taskId: String) {
        downloadDao.deleteFilesForTask(taskId)
    }

    suspend fun updateFileProgress(
        taskId: String,
        link: String,
        state: LocalDownloadState,
        speed: Long,
        downloadedBytes: Long
    ) {
        downloadDao.updateFileProgress(taskId, link, state, speed, downloadedBytes)
    }

    suspend fun updateFileState(
        taskId: String,
        link: String,
        state: LocalDownloadState,
        stateDescription: String? = null
    ) {
        downloadDao.updateFileState(taskId, link, state, stateDescription)
    }

    suspend fun deleteTask(id: String) {
        downloadDao.deleteTaskById(id)
    }

    suspend fun deleteAllTasks() {
        downloadDao.deleteAllTasks()
    }
}

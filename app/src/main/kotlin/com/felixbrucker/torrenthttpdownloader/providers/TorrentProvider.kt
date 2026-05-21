package com.felixbrucker.torrenthttpdownloader.providers

import com.felixbrucker.torrenthttpdownloader.models.TorrentType

data class ProviderTorrentInfo(
    val id: String,
    val name: String,
    val state: ProviderTorrentState,
    val status: String,
    val progress: Float, // 0-100
    val totalSizeInBytes: Long,
    val downloadedBytes: Long,
    val speed: Long,
    val links: List<String> = listOf()
)

enum class ProviderTorrentState {
    PROCESSING,
    CONVERTING_MAGNET,
    WAITING_FOR_FILE_SELECTION,
    DOWNLOADING,
    COMPLETED,
    ERROR,
    UNKNOWN,
}

data class UnrestrictedLink(
    val filename: String,
    val downloadUrl: String,
    val size: Long
)

interface TorrentProvider {
    val name: String
    val requiresLocalDownloads: Boolean
    val requiresFileSelection: Boolean

    suspend fun addTorrent(type: TorrentType, content: String, name: String): String
    suspend fun getTorrentInfo(id: String): ProviderTorrentInfo
    suspend fun selectFiles(id: String, files: String): Boolean
    suspend fun deleteTorrent(id: String, deleteFiles: Boolean = false): Boolean
    suspend fun unrestrictLink(id: String, link: String): UnrestrictedLink
    fun stop()
}

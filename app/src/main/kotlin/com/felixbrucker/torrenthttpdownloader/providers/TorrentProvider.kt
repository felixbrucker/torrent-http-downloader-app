package com.felixbrucker.torrenthttpdownloader.providers

import com.felixbrucker.torrenthttpdownloader.models.TorrentType

data class ProviderTorrentInfo(
    val id: String,
    val name: String,
    val status: String,
    val progress: Float, // 0-100
    val totalSizeInBytes: Long,
    val downloadedBytes: Long,
    val speed: Long,
    val links: List<String> = listOf()
)

data class UnrestrictedLink(
    val filename: String,
    val downloadUrl: String,
    val size: Long
)

interface TorrentProvider {
    val name: String
    val requiresLocalDownloads: Boolean

    suspend fun addTorrent(type: TorrentType, content: String): String
    suspend fun getTorrentInfo(id: String): ProviderTorrentInfo
    suspend fun selectFiles(id: String, files: String): Boolean
    suspend fun deleteTorrent(id: String): Boolean
    suspend fun unrestrictLink(link: String): UnrestrictedLink
}

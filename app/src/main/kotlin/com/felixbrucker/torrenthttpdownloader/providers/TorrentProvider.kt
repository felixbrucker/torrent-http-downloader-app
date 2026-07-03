package com.felixbrucker.torrenthttpdownloader.providers

data class ProviderTorrentInfo(
    val id: String,
    val name: String,
    val state: ProviderTorrentState,
    val status: String,
    val progress: Float, // 0-100
    val totalSizeInBytes: Long,
    val downloadedBytes: Long,
    val downloadSpeed: Long,
    val uploadSpeed: Long,
    val seeders: Int?,
    val leechers: Int?,
    val peers: Int?,
    val totalPeers: Int?,
    val links: List<String> = listOf(),
    val files: List<ProviderTorrentFile> = listOf(),
)

data class ProviderTorrentFile(
    val id: Int,
    val path: String,
    val size: Long,
    val isSelected: Boolean,
    val progress: Float?, // 0-100
    val downloadedBytes: Long?,
) {
    val name: String get() {
        return path.substringAfterLast('/')
    }

    val state: ProviderTorrentFileState get() {
        if (progress == null) return ProviderTorrentFileState.PENDING
        if (progress == 100f) return ProviderTorrentFileState.COMPLETED

        return ProviderTorrentFileState.DOWNLOADING
    }
}

enum class ProviderTorrentFileState {
    DOWNLOADING,
    COMPLETED,
    PENDING,
}

enum class ProviderTorrentState {
    PROCESSING,
    CONVERTING_MAGNET,
    WAITING_FOR_FILE_SELECTION,
    DOWNLOADING,
    PAUSED,
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
    val supportsPauseResume: Boolean
    suspend fun restoreTorrent(id: String)
    suspend fun addTorrent(torrentFileBytes: ByteArray, name: String): String
    suspend fun addMagnet(magnetUri: String, name: String): String
    suspend fun getTorrentInfo(id: String): ProviderTorrentInfo
    suspend fun selectFiles(id: String, fileIds: List<Int>): Boolean
    suspend fun deleteTorrent(id: String, deleteFiles: Boolean = false): Boolean
    suspend fun unrestrictLink(id: String, link: String): UnrestrictedLink
    suspend fun pause(id: String)
    suspend fun resume(id: String)
    fun stop()
    fun reloadSettings()
}

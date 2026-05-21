package com.felixbrucker.torrenthttpdownloader.providers

import android.content.ContentResolver
import com.felixbrucker.torrenthttpdownloader.models.TorrentType
import com.felixbrucker.torrenthttpdownloader.network.ResourceNotFoundException
import com.felixbrucker.torrenthttpdownloader.network.RetrofitClient
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import androidx.core.net.toUri
import kotlin.math.min

class RealDebridProvider(
    private val apiToken: String,
    private val contentResolver: ContentResolver
) : TorrentProvider {
    override val name: String = NAME
    override val requiresLocalDownloads: Boolean = true
    override val requiresFileSelection: Boolean = true
    override val supportsPauseResume: Boolean = false

    companion object {
        const val NAME: String = "Real-Debrid"
    }

    private val auth = "Bearer $apiToken"

    override suspend fun addTorrent(type: TorrentType, content: String, name: String): String {
        checkApiToken()

        val response = if (type == TorrentType.MAGNET) {
            RetrofitClient.instance.addMagnet(auth, content)
        } else {
            contentResolver.openInputStream(content.toUri())?.use {
                val torrentData = it.readBytes()
                val requestBody = torrentData.toRequestBody(
                    "application/x-bittorrent".toMediaTypeOrNull(),
                    0,
                    torrentData.size
                )
                RetrofitClient.instance.addTorrentFile(auth, requestBody)
            } ?: throw Exception("Could not open torrent file")
        }
        return response.id
    }

    override suspend fun getTorrentInfo(id: String): ProviderTorrentInfo {
        checkApiToken()

        val info = RetrofitClient.instance.getTorrentInfo(auth, id)
        val totalBytes = info.bytes
        val downloadedBytes = min((totalBytes * (info.progress / 100.0)).toLong(), totalBytes)

        return ProviderTorrentInfo(
            id = info.id,
            name = info.filename,
            state = mapStatusToState(info.status),
            status = info.status,
            progress = info.progress,
            totalSizeInBytes = totalBytes,
            downloadedBytes = downloadedBytes,
            downloadSpeed = info.speed ?: 0,
            uploadSpeed = 0,
            seeders = info.seeders,
            leechers = null,
            peers = null,
            totalPeers = null,
            links = info.links,
        )
    }

    override suspend fun selectFiles(id: String, files: String): Boolean {
        checkApiToken()

        val response = RetrofitClient.instance.selectFiles(auth, id, files)

        return response.isSuccessful
    }

    override suspend fun deleteTorrent(id: String, deleteFiles: Boolean): Boolean {
        checkApiToken()

        try {
            val response = RetrofitClient.instance.deleteTorrent(auth, id)

            return response.isSuccessful
        } catch (_: ResourceNotFoundException) {
            return true
        }
    }

    override suspend fun unrestrictLink(id: String, link: String): UnrestrictedLink {
        checkApiToken()

        val response = RetrofitClient.instance.unrestrictLink(auth, link)
        return UnrestrictedLink(
            filename = response.filename,
            downloadUrl = response.download,
            size = response.filesize
        )
    }

    override suspend fun pause(id: String) {
        throw Exception("Unsupported operation: pause")
    }

    override suspend fun resume(id: String) {
        throw Exception("Unsupported operation: resume")
    }

    override fun stop() {
        // Nothing to do
    }

    private fun checkApiToken() {
        if(apiToken.isEmpty()) {
            throw Exception("API token is empty")
        }
    }

    private fun mapStatusToState(status: String): ProviderTorrentState {
        return when (status) {
            "magnet_conversion" -> ProviderTorrentState.CONVERTING_MAGNET
            "waiting_files_selection" -> ProviderTorrentState.WAITING_FOR_FILE_SELECTION
            "queued", "downloading", "compressing", "uploading" -> ProviderTorrentState.DOWNLOADING
            "downloaded" -> ProviderTorrentState.COMPLETED
            "error", "magnet_error", "virus", "dead" -> ProviderTorrentState.ERROR
            else -> ProviderTorrentState.UNKNOWN
        }
    }
}

package com.felixbrucker.torrenthttpdownloader.providers

import android.content.SharedPreferences
import com.felixbrucker.torrenthttpdownloader.network.ResourceNotFoundException
import com.felixbrucker.torrenthttpdownloader.network.RetrofitClient
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import com.felixbrucker.torrenthttpdownloader.container.Container
import com.felixbrucker.torrenthttpdownloader.container.ServiceBuilder
import kotlin.math.min

class RealDebridProvider(
    private val sharedPreferences: SharedPreferences,
) : TorrentProvider {
    override val name: String = NAME
    override val features: Set<ProviderFeature> = setOf(
        ProviderFeature.LocalDownloads,
    )

    companion object: ServiceBuilder {
        override val NAME: String = "Real-Debrid"

        override fun build(): RealDebridProvider {
            val sharedPreferences = Container.getService<SharedPreferences>("SharedPreferences")

            return RealDebridProvider(sharedPreferences)
        }
    }

    private var apiToken = sharedPreferences.getString("real_debrid_api_token", "") ?: ""
    private val auth: String get() = "Bearer $apiToken"

    override suspend fun restoreTorrent(id: String) {
        // Nothing to do
    }

    override suspend fun addTorrent(torrentFileBytes: ByteArray, name: String): String {
        checkApiToken()

        val requestBody = torrentFileBytes.toRequestBody(
            "application/x-bittorrent".toMediaTypeOrNull(),
            0,
            torrentFileBytes.size
        )
        val response = RetrofitClient.instance.addTorrentFile(auth, requestBody)

        return response.id
    }

    override suspend fun addMagnet(magnetUri: String, name: String): String {
        checkApiToken()

        val response = RetrofitClient.instance.addMagnet(auth, magnetUri)

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
            files = info.files.map {
                ProviderTorrentFile(
                    id = it.id,
                    path = it.path,
                    size = it.bytes,
                    isSelected = it.selected == 1,
                    progress = null,
                    downloadedBytes = null,
                )
            }
        )
    }

    override suspend fun selectFiles(id: String, fileIds: List<Int>): Boolean {
        checkApiToken()

        val response = RetrofitClient.instance.selectFiles(auth, id, fileIds.joinToString(","))

        return response.isSuccessful
    }

    override suspend fun setFilePriority(
        id: String,
        fileId: Int,
        priority: FilePriority
    ) {
        // Nothing to do
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

    override fun reloadSettings() {
        apiToken = sharedPreferences.getString("real_debrid_api_token", "") ?: ""
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

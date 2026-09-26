package com.felixbrucker.torrenthttpdownloader.network

import android.content.ContentResolver
import android.net.Uri
import androidx.core.net.toUri
import com.felixbrucker.torrenthttpdownloader.createDirectoryRecursivelyIfNotExists
import com.felixbrucker.torrenthttpdownloader.models.TorrentType
import com.felixbrucker.torrenthttpdownloader.storage.PathFactory
import com.felixbrucker.torrenthttpdownloader.makeTorrentId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.net.URLDecoder
import java.util.concurrent.TimeUnit


data class ResolvedTorrent(
    val type: TorrentType,
    val uri: Uri,
    val id: String,
    val name: String?,
)

class TorrentUriResolver(
    private val contentResolver: ContentResolver,
    private val pathFactory: PathFactory? = null,
) {
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun resolve(uri: Uri): ResolvedTorrent {
        var newUri: Uri = uri
        if (uri.scheme == "http" || uri.scheme == "https") {
            // Fetch torrent file
            val torrentFileName = uri.toString().substringAfterLast("/")
            val torrentDir = pathFactory?.getTemporaryTorrentFileDirectory() ?: File(
                File(System.getProperty("java.io.tmpdir") ?: "/tmp"),
                "torrents"
            )
            torrentDir.createDirectoryRecursivelyIfNotExists()
            val temporaryTorrentFile = File(torrentDir, torrentFileName)
            val request = Request.Builder().url(uri.toString()).build()
            withContext(Dispatchers.IO) {
                httpClient.newCall(request).execute().use { response ->
                    response.body.byteStream().use { input ->
                        temporaryTorrentFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                }
            }
            newUri = temporaryTorrentFile.toUri()
        }
        val type = when (newUri.scheme) {
            "magnet" -> {
                TorrentType.MAGNET
            } else -> {
                TorrentType.TORRENT_FILE
            }
        }
        val name = tryToGetNameFromUri(contentResolver, newUri.toString(), type)
        val id = newUri.makeTorrentId(contentResolver)

        return ResolvedTorrent(
            type = type,
            uri = newUri,
            id = id,
            name = name,
        )
    }
    private fun tryToGetNameFromUri(contentResolver: ContentResolver, uriString: String, type: TorrentType): String? {
        if (type == TorrentType.MAGNET) {
            return getNameFromMagnetLink(uriString)
        }

        return getNameFromTorrentFile(contentResolver, uriString.toUri())
    }

    private fun getNameFromMagnetLink(link: String): String? {
        val params = link.substringAfter('?').split('&')
        val displayName = params.find { it.startsWith("dn=") }?.substringAfter("dn=")
        val name = params.find { it.startsWith("name=") }?.substringAfter("name=")

        // URL Decode the name as it is often encoded
        return (displayName ?: name)?.let { URLDecoder.decode(it, "UTF-8") }
    }

    private fun getNameFromTorrentFile(contentResolver: ContentResolver, uri: Uri): String? {
        return try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                val bytes = inputStream.readBytes()
                val content = String(bytes, Charsets.ISO_8859_1)
                val infoIndex = content.indexOf("4:info")
                val nameKey = "4:name"
                val nameIndex = if (infoIndex != -1) content.indexOf(nameKey, infoIndex) else content.indexOf(nameKey)
                if (nameIndex != -1) {
                    val lengthStart = nameIndex + nameKey.length
                    val colonIndex = content.indexOf(':', lengthStart)
                    if (colonIndex != -1) {
                        val length = content.substring(lengthStart, colonIndex).toIntOrNull() ?: return null
                        if (colonIndex + 1 + length <= content.length) {
                            return content.substring(colonIndex + 1, colonIndex + 1 + length)
                        }
                    }
                }
                null
            }
        } catch (_: Exception) {
            null
        }
    }
}

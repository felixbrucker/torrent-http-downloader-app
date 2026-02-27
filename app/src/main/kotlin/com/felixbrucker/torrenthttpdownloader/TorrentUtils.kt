package com.felixbrucker.torrenthttpdownloader

import android.content.ContentResolver
import android.net.Uri
import androidx.core.net.toUri
import com.felixbrucker.torrenthttpdownloader.models.TorrentType
import java.net.URLDecoder

object TorrentUtils {
    fun tryToGetNameFromUri(contentResolver: ContentResolver, uriString: String, type: TorrentType): String? {
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

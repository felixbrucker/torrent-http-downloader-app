package com.felixbrucker.torrenthttpdownloader.util

import androidx.core.net.toUri
import org.libtorrent4j.Sha1Hash
import org.libtorrent4j.swig.add_torrent_params
import org.libtorrent4j.swig.error_code
import org.libtorrent4j.swig.libtorrent
import java.io.File
import java.security.MessageDigest

val INVALID_CHARACTERS_FOR_PATH = listOf(
    ":",
    "|",
)

fun String.cleanedForUseAsPath(): String {
    var result = this
    for (invalidCharacter in INVALID_CHARACTERS_FOR_PATH) {
        result = result.replace(invalidCharacter, " ")
    }

    return result
}

fun String.asStateText(): String {
    return this.replace("_", " ").lowercase()
}

fun String.hash(algorithm: String = "SHA-256"): String {
    val md = MessageDigest.getInstance(algorithm)
    md.update(this.toByteArray())

    return md.digest().joinToString("") { "%02x".format(it) }
}

fun String.asFile(): File {
    val path = this.toUri().path ?: this

    return File(path)
}

fun String.capitalized(): String {
    return replaceFirstChar { it.uppercase() }
}

fun String.makeAddTorrentParams(): add_torrent_params {
    val errorCode = error_code()
    val addTorrentParams = libtorrent.parse_magnet_uri(this, errorCode)
    if (errorCode.failed()) {
        throw Exception(errorCode.message())
    }

    return addTorrentParams
}

fun String.sha1Hash(): Sha1Hash {
    return Sha1Hash.parseHex(this)
}
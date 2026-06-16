package com.felixbrucker.torrenthttpdownloader

import androidx.core.net.toUri
import java.io.File
import java.security.MessageDigest

fun String.cleanedForUseAsPath(): String {
    return this.replace(":", " ")
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
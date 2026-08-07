package com.felixbrucker.torrenthttpdownloader

import android.content.ContentResolver
import android.net.Uri
import androidx.core.net.toUri

fun Uri.makeTorrentId(contentResolver: ContentResolver): String {
    return when (scheme) {
        "magnet" -> {
            toString().makeAddTorrentParams().torrentId()
        } else -> {
            val inputStream = try {
                contentResolver.openInputStream(this)
            } catch (_: Exception) {
                contentResolver.openInputStream(toString().asFile().toUri())
            }
            inputStream?.use {
                it.readBytes().torrentInfo().infoHash().toHex()
            } ?: throw Exception("Could not open torrent file")
        }
    }
}
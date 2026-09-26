package com.felixbrucker.torrenthttpdownloader.extensions

import org.libtorrent4j.TorrentInfo

fun ByteArray.torrentInfo(): TorrentInfo {
    return TorrentInfo(this)
}

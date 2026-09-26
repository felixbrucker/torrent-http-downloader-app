package com.felixbrucker.torrenthttpdownloader.util

import org.libtorrent4j.TorrentInfo


fun ByteArray.torrentInfo(): TorrentInfo {
    return TorrentInfo(this)
}

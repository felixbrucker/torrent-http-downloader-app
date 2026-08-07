package com.felixbrucker.torrenthttpdownloader

import org.libtorrent4j.TorrentInfo


fun ByteArray.torrentInfo(): TorrentInfo {
    return TorrentInfo(this)
}

package com.felixbrucker.torrenthttpdownloader.util

import org.libtorrent4j.swig.add_torrent_params

fun add_torrent_params.torrentId(): String {
    val infoHash = this.getInfo_hashes()._best

    return infoHash.to_hex()
}
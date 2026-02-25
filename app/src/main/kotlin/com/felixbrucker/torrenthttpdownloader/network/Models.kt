package com.felixbrucker.torrenthttpdownloader.network

data class AddMagnetResponse(
    val id: String,
    val uri: String
)

data class TorrentFile(
    val id: Long,
    val path: String,
    val bytes: Long,
    val selected: Int
)

data class TorrentInfo(
    val id: String,
    val filename: String,
    val bytes: Long,
    val status: String,
    val links: List<String>,
    val progress: Float,
    val files: List<TorrentFile>,
    val speed: Long?
)

data class UnrestrictLinkResponse(
    val id: String,
    val filename: String,
    val mimeType: String,
    val filesize: Long,
    val link: String,
    val host: String,
    val chunks: Int,
    val crc: Int,
    val download: String,
    val streamable: Int
)

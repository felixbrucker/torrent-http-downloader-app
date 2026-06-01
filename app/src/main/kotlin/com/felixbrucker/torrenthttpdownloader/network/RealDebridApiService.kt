package com.felixbrucker.torrenthttpdownloader.network

import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

interface RealDebridApiService {
    @POST("torrents/addMagnet")
    @FormUrlEncoded
    suspend fun addMagnet(
        @Header("Authorization") auth: String,
        @Field("magnet") magnet: String
    ): AddMagnetResponse

    @PUT("torrents/addTorrent")
    suspend fun addTorrentFile(
        @Header("Authorization") auth: String,
        @Body torrent: RequestBody
    ): AddMagnetResponse

    @GET("torrents/info/{id}")
    suspend fun getTorrentInfo(
        @Header("Authorization") auth: String,
        @Path("id") id: String
    ): TorrentInfo

    @POST("torrents/selectFiles/{id}")
    @FormUrlEncoded
    suspend fun selectFiles(
        @Header("Authorization") auth: String,
        @Path("id") id: String,
        @Field("files") files: String
    ): Response<Unit>

    @DELETE("torrents/delete/{id}")
    suspend fun deleteTorrent(
        @Header("Authorization") auth: String,
        @Path("id") id: String
    ): Response<Unit>

    @POST("unrestrict/link")
    @FormUrlEncoded
    suspend fun unrestrictLink(
        @Header("Authorization") auth: String,
        @Field("link") link: String
    ): UnrestrictLinkResponse
}

data class AddMagnetResponse(
    val id: String,
    val uri: String
)

data class TorrentFile(
    val id: Int,
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
    val speed: Long?,
    val seeders: Int?,
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
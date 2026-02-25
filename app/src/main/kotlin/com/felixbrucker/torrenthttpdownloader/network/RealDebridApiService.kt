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
package com.felixbrucker.torrenthttpdownloader.network

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {

    private const val BASE_URL = "https://api.real-debrid.com/rest/1.0/"

    val instance: RealDebridApiService by lazy {
        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(ErrorInterceptor())
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        retrofit.create(RealDebridApiService::class.java)
    }
}
package com.felixbrucker.torrenthttpdownloader.di

import com.felixbrucker.torrenthttpdownloader.network.ErrorInterceptor
import com.felixbrucker.torrenthttpdownloader.network.RealDebridApiService
import com.google.gson.FieldNamingPolicy
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private const val REAL_DEBRID_BASE_URL = "https://api.real-debrid.com/rest/1.0/"

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(ErrorInterceptor())
            .build()
    }

    @Provides
    @Singleton
    fun provideGson(): Gson {
        return GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .create()
    }

    @Provides
    @Singleton
    fun provideRealDebridApiService(okHttpClient: OkHttpClient, gson: Gson): RealDebridApiService {
        return Retrofit.Builder()
            .baseUrl(REAL_DEBRID_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(RealDebridApiService::class.java)
    }
}

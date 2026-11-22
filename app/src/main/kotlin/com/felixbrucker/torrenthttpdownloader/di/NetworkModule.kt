package com.felixbrucker.torrenthttpdownloader.di

import com.felixbrucker.torrenthttpdownloader.core.network.ErrorInterceptor
import com.felixbrucker.torrenthttpdownloader.core.network.RealDebridApiService
import com.squareup.moshi.Moshi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

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
    fun provideMoshi(): Moshi {
        return Moshi.Builder().build()
    }

    @Provides
    @Singleton
    fun provideRealDebridApiService(okHttpClient: OkHttpClient, moshi: Moshi): RealDebridApiService {
        return Retrofit.Builder()
            .baseUrl(REAL_DEBRID_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(RealDebridApiService::class.java)
    }
}

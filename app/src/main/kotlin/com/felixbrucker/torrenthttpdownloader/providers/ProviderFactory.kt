package com.felixbrucker.torrenthttpdownloader.providers

import android.content.Context
import android.content.Context.MODE_PRIVATE

class ProviderFactory {
    companion object {
        fun providerNameList(): List<String> = listOf(RealDebridProvider.NAME)

        fun makeProvider(context: Context): TorrentProvider {
            val sharedPreferences = context.getSharedPreferences("settings", MODE_PRIVATE)
            val providerName = sharedPreferences.getString("provider", RealDebridProvider.NAME)

            return when (providerName) {
                RealDebridProvider.NAME -> {
                    val apiToken = sharedPreferences.getString("real_debrid_api_token", "") ?: ""

                    RealDebridProvider(apiToken, context.contentResolver)
                }
                else -> {
                    throw Exception("Unknown provider: $providerName")
                }
            }
        }
    }
}
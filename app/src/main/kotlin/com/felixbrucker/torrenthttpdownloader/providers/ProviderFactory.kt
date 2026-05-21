package com.felixbrucker.torrenthttpdownloader.providers

import android.content.SharedPreferences
import com.felixbrucker.torrenthttpdownloader.container.Container

class ProviderFactory {
    companion object {
        fun getProvider(): TorrentProvider {
            val sharedPreferences = Container.getService<SharedPreferences>("SharedPreferences")
            val providerName = sharedPreferences.getString("provider", RealDebridProvider.NAME)

            return when (providerName) {
                LibTorrentProvider.NAME -> Container.getService(LibTorrentProvider.NAME)
                RealDebridProvider.NAME -> Container.getService(RealDebridProvider.NAME)
                else -> { throw Exception("Unknown provider: $providerName") }
            }
        }
    }
}
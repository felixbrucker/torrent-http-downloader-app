package com.felixbrucker.torrenthttpdownloader.providers

import android.content.SharedPreferences
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class ProviderFactory @Inject constructor(
    @Named("settings") private val sharedPreferences: SharedPreferences,
    private val libTorrentProvider: LibTorrentProvider,
    private val realDebridProvider: RealDebridProvider,
) {
    fun getProvider(): TorrentProvider {
        val providerName = sharedPreferences.getString("provider", RealDebridProvider.NAME)

        return when (providerName) {
            LibTorrentProvider.NAME -> libTorrentProvider
            RealDebridProvider.NAME -> realDebridProvider
            else -> { throw Exception("Unknown provider: $providerName") }
        }
    }
}

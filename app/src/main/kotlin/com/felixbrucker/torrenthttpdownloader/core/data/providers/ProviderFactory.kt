package com.felixbrucker.torrenthttpdownloader.core.data.providers

import com.felixbrucker.torrenthttpdownloader.core.datastore.AppSettingsRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.runBlocking

@Singleton
class ProviderFactory @Inject constructor(
    private val appSettingsRepository: AppSettingsRepository,
    private val libTorrentProvider: LibTorrentProvider,
    private val realDebridProvider: RealDebridProvider,
) {
    fun getProvider(): TorrentProvider {
        val providerName = runBlocking { appSettingsRepository.getSettings() }.selectedProvider

        return when (providerName) {
            LibTorrentProvider.NAME -> libTorrentProvider
            RealDebridProvider.NAME -> realDebridProvider
            else -> { throw Exception("Unknown provider: $providerName") }
        }
    }
}

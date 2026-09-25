package com.felixbrucker.torrenthttpdownloader.providers

import com.felixbrucker.torrenthttpdownloader.data.preferences.AppSettingsRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProviderFactory @Inject constructor(
    private val realDebridProvider: RealDebridProvider,
    private val libTorrentProvider: LibTorrentProvider,
    private val appSettingsRepository: AppSettingsRepository
) {
    fun getProvider(name: String): TorrentProvider {
        return when (name.lowercase()) {
            "real-debrid", "real_debrid" -> realDebridProvider
            "libtorrent" -> libTorrentProvider
            else -> realDebridProvider
        }
    }

    suspend fun getSelectedProvider(): TorrentProvider {
        val prefs = appSettingsRepository.preferencesFlow.first()
        return getProvider(prefs.selectedProvider)
    }
}

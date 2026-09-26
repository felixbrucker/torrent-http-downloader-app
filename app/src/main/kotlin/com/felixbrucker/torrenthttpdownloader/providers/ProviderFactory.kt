package com.felixbrucker.torrenthttpdownloader.providers

import com.felixbrucker.torrenthttpdownloader.data.preferences.AppSettingsRepository
import com.felixbrucker.torrenthttpdownloader.data.preferences.TorrentProviderType
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProviderFactory @Inject constructor(
    private val realDebridProvider: RealDebridProvider,
    private val libTorrentProvider: LibTorrentProvider,
    private val appSettingsRepository: AppSettingsRepository
) {
    fun getProvider(type: TorrentProviderType): TorrentProvider {
        return when (type) {
            TorrentProviderType.REAL_DEBRID -> realDebridProvider
            TorrentProviderType.LIBTORRENT -> libTorrentProvider
        }
    }

    suspend fun getSelectedProvider(): TorrentProvider {
        val prefs = appSettingsRepository.preferencesFlow.first()
        return getProvider(prefs.selectedProvider)
    }
}

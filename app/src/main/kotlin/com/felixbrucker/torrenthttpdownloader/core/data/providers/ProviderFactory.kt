package com.felixbrucker.torrenthttpdownloader.core.data.providers

import com.felixbrucker.torrenthttpdownloader.core.datastore.AppSettingsRepository
import com.felixbrucker.torrenthttpdownloader.di.ApplicationScope
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking

@Singleton
class ProviderFactory @Inject constructor(
    private val appSettingsRepository: AppSettingsRepository,
    private val libTorrentProvider: LibTorrentProvider,
    private val realDebridProvider: RealDebridProvider,
    @param:ApplicationScope private val scope: CoroutineScope
) {
    private var activeProviderName: String = runBlocking { appSettingsRepository.getSettings() }.selectedProvider

    init {
        appSettingsRepository.settingsFlow
            .onEach { settings ->
                activeProviderName = settings.selectedProvider
            }
            .launchIn(scope)
    }

    fun getProvider(): TorrentProvider {
        return when (activeProviderName) {
            LibTorrentProvider.NAME -> libTorrentProvider
            RealDebridProvider.NAME -> realDebridProvider
            else -> { throw Exception("Unknown provider: $activeProviderName") }
        }
    }
}

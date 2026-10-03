package com.felixbrucker.torrenthttpdownloader

import android.content.Context
import com.felixbrucker.torrenthttpdownloader.core.data.providers.LibTorrentProvider
import com.felixbrucker.torrenthttpdownloader.core.data.providers.ProviderFactory
import com.felixbrucker.torrenthttpdownloader.core.data.providers.RealDebridProvider
import com.felixbrucker.torrenthttpdownloader.core.data.providers.TorrentProvider
import com.felixbrucker.torrenthttpdownloader.core.datastore.AppSettings
import com.felixbrucker.torrenthttpdownloader.core.datastore.AppSettingsRepository
import com.felixbrucker.torrenthttpdownloader.feature.rss.RssSyncLauncher
import com.felixbrucker.torrenthttpdownloader.feature.settings.SettingsViewModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val appSettingsRepository = mockk<AppSettingsRepository>(relaxed = true)
    private val context = mockk<Context>(relaxed = true)
    private val rssSyncLauncher = mockk<RssSyncLauncher>(relaxed = true)
    private val provider = mockk<TorrentProvider>(relaxed = true)
    private val providerFactory = mockk<ProviderFactory> {
        every { getProvider() } returns provider
    }
    private val testDispatcher = UnconfinedTestDispatcher()
    private val settingsFlow = MutableStateFlow(AppSettings())

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        coEvery { appSettingsRepository.settingsFlow } returns settingsFlow
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testInitialUiStateLoadsSavedValues() {
        settingsFlow.value = AppSettings(
            selectedProvider = LibTorrentProvider.NAME,
            realDebridApiToken = "test-token",
            localParallelDownloads = 4,
            libTorrentParallelDownloads = 5,
            libTorrentRequireVpnConnection = true,
            rssSyncEnabled = true,
            rssSyncIntervalHours = 6
        )

        val viewModel = SettingsViewModel(appSettingsRepository, context, rssSyncLauncher, providerFactory)
        val state = viewModel.uiState.value

        assertEquals(LibTorrentProvider.NAME, state.selectedProvider)
        assertEquals("test-token", state.realDebridApiToken)
        assertEquals("4", state.localParallelDownloads)
        assertEquals("5", state.libTorrentParallelDownloads)
        assertEquals(true, state.libTorrentRequireVpnConnection)
        assertEquals(true, state.rssSyncEnabled)
        assertEquals(6, state.rssSyncIntervalHours)
    }

    @Test
    fun testUpdatersModifyStateAndSave() {
        every { context.packageName } returns "com.felixbrucker.torrenthttpdownloader"
        val viewModel = SettingsViewModel(appSettingsRepository, context, rssSyncLauncher, providerFactory)

        viewModel.updateSelectedProvider(RealDebridProvider.NAME)
        viewModel.updateRealDebridApiToken("token-123")
        viewModel.updateLocalParallelDownloads("3abc")
        viewModel.updateLibTorrentParallelDownloads("4def")
        viewModel.updateLibTorrentRequireVpnConnection(true)
        viewModel.updateRssSyncEnabled(false)
        viewModel.updateRssSyncIntervalHours(12)

        val state = viewModel.uiState.value
        assertEquals(RealDebridProvider.NAME, state.selectedProvider)
        assertEquals("token-123", state.realDebridApiToken)
        assertEquals("3", state.localParallelDownloads)
        assertEquals("4", state.libTorrentParallelDownloads)
        assertEquals(true, state.libTorrentRequireVpnConnection)
        assertEquals(false, state.rssSyncEnabled)
        assertEquals(12, state.rssSyncIntervalHours)
        coVerify { appSettingsRepository.updateSelectedProvider(RealDebridProvider.NAME) }
        coVerify { appSettingsRepository.updateRealDebridApiToken("token-123") }
        coVerify { appSettingsRepository.updateLocalParallelDownloads(3) }
        coVerify { appSettingsRepository.updateLibTorrentParallelDownloads(4) }
        coVerify { appSettingsRepository.updateLibTorrentRequireVpnConnection(true) }
        coVerify { appSettingsRepository.updateRssSyncEnabled(false) }
        coVerify { appSettingsRepository.updateRssSyncIntervalHours(12) }
        verify { rssSyncLauncher.updateRssSyncSchedule(enabled = false, intervalHours = 12L) }
        verify(atLeast = 1) { provider.reloadSettings() }
    }

    @Test
    fun testRssSyncScheduleOnlyUpdatesWhenRssSyncSettingsChange() {
        val viewModel = SettingsViewModel(appSettingsRepository, context, rssSyncLauncher, providerFactory)

        viewModel.updateSelectedProvider(RealDebridProvider.NAME)
        viewModel.updateRssSyncEnabled(false)

        verify(exactly = 1) { rssSyncLauncher.updateRssSyncSchedule(enabled = false, intervalHours = 3L) }
        verify(exactly = 1) { provider.reloadSettings() }
    }

    @Test
    fun testProviderReloadOnlyUpdatesWhenProviderSettingsChange() {
        val viewModel = SettingsViewModel(appSettingsRepository, context, rssSyncLauncher, providerFactory)

        viewModel.updateRssSyncIntervalHours(6)
        viewModel.updateRealDebridApiToken("new-token")

        verify(exactly = 1) { rssSyncLauncher.updateRssSyncSchedule(enabled = true, intervalHours = 6L) }
        verify(exactly = 1) { provider.reloadSettings() }
    }
}

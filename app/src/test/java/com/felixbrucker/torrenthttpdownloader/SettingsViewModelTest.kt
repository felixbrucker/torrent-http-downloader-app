package com.felixbrucker.torrenthttpdownloader

import com.felixbrucker.torrenthttpdownloader.core.data.providers.LibTorrentProvider
import com.felixbrucker.torrenthttpdownloader.core.data.providers.RealDebridProvider
import com.felixbrucker.torrenthttpdownloader.core.datastore.AppSettings
import com.felixbrucker.torrenthttpdownloader.core.datastore.AppSettingsRepository
import com.felixbrucker.torrenthttpdownloader.feature.settings.SettingsViewModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
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
    private val testDispatcher = UnconfinedTestDispatcher()
    private val settingsFlow = MutableStateFlow(AppSettings())

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        coEvery { appSettingsRepository.settingsFlow } returns settingsFlow
        coEvery { appSettingsRepository.updateSelectedProvider(any()) } answers {
            settingsFlow.value = settingsFlow.value.copy(selectedProvider = firstArg())
        }
        coEvery { appSettingsRepository.updateRealDebridApiToken(any()) } answers {
            settingsFlow.value = settingsFlow.value.copy(realDebridApiToken = firstArg())
        }
        coEvery { appSettingsRepository.updateLocalParallelDownloads(any()) } answers {
            settingsFlow.value = settingsFlow.value.copy(localParallelDownloads = firstArg())
        }
        coEvery { appSettingsRepository.updateLibTorrentParallelDownloads(any()) } answers {
            settingsFlow.value = settingsFlow.value.copy(libTorrentParallelDownloads = firstArg())
        }
        coEvery { appSettingsRepository.updateLibTorrentRequireVpnConnection(any()) } answers {
            settingsFlow.value = settingsFlow.value.copy(libTorrentRequireVpnConnection = firstArg())
        }
        coEvery { appSettingsRepository.updateRssSyncEnabled(any()) } answers {
            settingsFlow.value = settingsFlow.value.copy(rssSyncEnabled = firstArg())
        }
        coEvery { appSettingsRepository.updateRssSyncIntervalHours(any()) } answers {
            settingsFlow.value = settingsFlow.value.copy(rssSyncIntervalHours = firstArg())
        }
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

        val viewModel = SettingsViewModel(appSettingsRepository)
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
        val viewModel = SettingsViewModel(appSettingsRepository)

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
    }
}

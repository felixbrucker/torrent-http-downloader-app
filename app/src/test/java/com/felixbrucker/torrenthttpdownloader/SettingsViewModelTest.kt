package com.felixbrucker.torrenthttpdownloader

import android.content.Context
import android.content.SharedPreferences
import com.felixbrucker.torrenthttpdownloader.core.data.providers.LibTorrentProvider
import com.felixbrucker.torrenthttpdownloader.core.data.providers.ProviderFactory
import com.felixbrucker.torrenthttpdownloader.core.data.providers.RealDebridProvider
import com.felixbrucker.torrenthttpdownloader.core.data.providers.TorrentProvider
import com.felixbrucker.torrenthttpdownloader.feature.rss.RssSyncLauncher
import com.felixbrucker.torrenthttpdownloader.feature.settings.SettingsViewModel
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val sharedPreferences = mockk<SharedPreferences>()
    private val context = mockk<Context>(relaxed = true)
    private val rssSyncLauncher = mockk<RssSyncLauncher>(relaxed = true)
    private val provider = mockk<TorrentProvider>(relaxed = true)
    private val providerFactory = mockk<ProviderFactory> {
        every { getProvider() } returns provider
    }
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testInitialUiStateLoadsSavedValues() {
        every { sharedPreferences.getString("provider", any()) } returns LibTorrentProvider.NAME
        every { sharedPreferences.getString("real_debrid_api_token", "") } returns "test-token"
        every { sharedPreferences.getInt("local_parallel_downloads", 2) } returns 4
        every { sharedPreferences.getInt("libtorrent_parallel_downloads", 3) } returns 5
        every { sharedPreferences.getBoolean("libtorrent_require_vpn_connection", false) } returns true
        every { sharedPreferences.getBoolean("rss_sync_enabled", true) } returns true
        every { sharedPreferences.getInt("rss_sync_interval_hours", 3) } returns 6

        val viewModel = SettingsViewModel(sharedPreferences, context, rssSyncLauncher, providerFactory)
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
        val editor = mockk<SharedPreferences.Editor>(relaxed = true)
        every { sharedPreferences.edit() } returns editor
        every { sharedPreferences.getString("provider", any()) } returns LibTorrentProvider.NAME
        every { sharedPreferences.getString("real_debrid_api_token", "") } returns ""
        every { sharedPreferences.getInt("local_parallel_downloads", 2) } returns 2
        every { sharedPreferences.getInt("libtorrent_parallel_downloads", 3) } returns 3
        every { sharedPreferences.getBoolean("libtorrent_require_vpn_connection", false) } returns false
        every { sharedPreferences.getBoolean("rss_sync_enabled", true) } returns true
        every { sharedPreferences.getInt("rss_sync_interval_hours", 3) } returns 3
        every { context.packageName } returns "com.felixbrucker.torrenthttpdownloader"
        val viewModel = SettingsViewModel(sharedPreferences, context, rssSyncLauncher, providerFactory)

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
        verify { editor.putString("provider", RealDebridProvider.NAME) }
        verify { editor.putString("real_debrid_api_token", "token-123") }
        verify { editor.putInt("local_parallel_downloads", 3) }
        verify { editor.putInt("libtorrent_parallel_downloads", 4) }
        verify { editor.putBoolean("libtorrent_require_vpn_connection", true) }
        verify { editor.putBoolean("rss_sync_enabled", false) }
        verify { editor.putInt("rss_sync_interval_hours", 12) }
        verify { rssSyncLauncher.updateRssSyncSchedule(enabled = false, intervalHours = 12L) }
        verify(atLeast = 1) { provider.reloadSettings() }
    }

    @Test
    fun testRssSyncScheduleOnlyUpdatesWhenRssSyncSettingsChange() {
        val editor = mockk<SharedPreferences.Editor>(relaxed = true)
        every { sharedPreferences.edit() } returns editor
        every { sharedPreferences.getString("provider", any()) } returns LibTorrentProvider.NAME
        every { sharedPreferences.getString("real_debrid_api_token", "") } returns ""
        every { sharedPreferences.getInt("local_parallel_downloads", 2) } returns 2
        every { sharedPreferences.getInt("libtorrent_parallel_downloads", 3) } returns 3
        every { sharedPreferences.getBoolean("libtorrent_require_vpn_connection", false) } returns false
        every { sharedPreferences.getBoolean("rss_sync_enabled", true) } returns true
        every { sharedPreferences.getInt("rss_sync_interval_hours", 3) } returns 3
        val viewModel = SettingsViewModel(sharedPreferences, context, rssSyncLauncher, providerFactory)

        viewModel.updateSelectedProvider(RealDebridProvider.NAME)
        viewModel.updateRssSyncEnabled(false)

        verify(exactly = 1) { rssSyncLauncher.updateRssSyncSchedule(enabled = false, intervalHours = 3L) }
        verify(exactly = 1) { provider.reloadSettings() }
    }

    @Test
    fun testProviderReloadOnlyUpdatesWhenProviderSettingsChange() {
        val editor = mockk<SharedPreferences.Editor>(relaxed = true)
        every { sharedPreferences.edit() } returns editor
        every { sharedPreferences.getString("provider", any()) } returns LibTorrentProvider.NAME
        every { sharedPreferences.getString("real_debrid_api_token", "") } returns ""
        every { sharedPreferences.getInt("local_parallel_downloads", 2) } returns 2
        every { sharedPreferences.getInt("libtorrent_parallel_downloads", 3) } returns 3
        every { sharedPreferences.getBoolean("libtorrent_require_vpn_connection", false) } returns false
        every { sharedPreferences.getBoolean("rss_sync_enabled", true) } returns true
        every { sharedPreferences.getInt("rss_sync_interval_hours", 3) } returns 3
        val viewModel = SettingsViewModel(sharedPreferences, context, rssSyncLauncher, providerFactory)

        viewModel.updateRssSyncIntervalHours(6)
        viewModel.updateRealDebridApiToken("new-token")

        verify(exactly = 1) { rssSyncLauncher.updateRssSyncSchedule(enabled = true, intervalHours = 6L) }
        verify(exactly = 1) { provider.reloadSettings() }
    }
}

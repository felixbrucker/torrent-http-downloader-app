package com.felixbrucker.torrenthttpdownloader

import android.app.backup.BackupManager
import android.content.Context
import android.content.SharedPreferences
import com.felixbrucker.torrenthttpdownloader.core.data.providers.LibTorrentProvider
import com.felixbrucker.torrenthttpdownloader.feature.settings.SettingsViewModel
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class SettingsViewModelTest {
    private val sharedPreferences = mockk<SharedPreferences>()
    private val context = mockk<Context>(relaxed = true)

    @Before
    fun setUp() {
        mockkStatic(BackupManager::class)
        every { BackupManager.dataChanged(any()) } returns Unit
    }

    @Test
    fun testLoadSettingsReturnsSavedValues() {
        every { sharedPreferences.getString("provider", any()) } returns LibTorrentProvider.NAME
        every { sharedPreferences.getString("real_debrid_api_token", "") } returns "test-token"
        every { sharedPreferences.getInt("local_parallel_downloads", 2) } returns 4
        every { sharedPreferences.getInt("libtorrent_parallel_downloads", 3) } returns 5
        every { sharedPreferences.getBoolean("libtorrent_require_vpn_connection", false) } returns true
        val viewModel = SettingsViewModel(sharedPreferences, context)

        val state = viewModel.loadSettings()

        assertEquals(LibTorrentProvider.NAME, state.selectedProvider)
        assertEquals("test-token", state.realDebridApiToken)
        assertEquals("4", state.localParallelDownloads)
        assertEquals("5", state.libTorrentParallelDownloads)
        assertEquals(true, state.libTorrentRequireVpnConnection)
    }

    @Test
    fun testSaveSettingsEditsSharedPreferences() {
        val editor = mockk<SharedPreferences.Editor>(relaxed = true)
        every { sharedPreferences.edit() } returns editor
        every { context.packageName } returns "com.felixbrucker.torrenthttpdownloader"
        val viewModel = SettingsViewModel(sharedPreferences, context)

        viewModel.saveSettings(
            selectedProvider = LibTorrentProvider.NAME,
            realDebridApiToken = "token-123",
            localParallelDownloads = "3",
            libTorrentParallelDownloads = "4",
            libTorrentRequireVpnConnection = true
        )

        verify { editor.putString("provider", LibTorrentProvider.NAME) }
        verify { editor.putString("real_debrid_api_token", "token-123") }
        verify { editor.putInt("local_parallel_downloads", 3) }
        verify { editor.putInt("libtorrent_parallel_downloads", 4) }
        verify { editor.putBoolean("libtorrent_require_vpn_connection", true) }
        verify { BackupManager.dataChanged("com.felixbrucker.torrenthttpdownloader") }
    }
}

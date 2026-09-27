package com.felixbrucker.torrenthttpdownloader

import android.content.SharedPreferences
import android.net.Uri
import com.felixbrucker.torrenthttpdownloader.core.data.RssRepository
import com.felixbrucker.torrenthttpdownloader.core.model.FileSelectionMode
import com.felixbrucker.torrenthttpdownloader.core.model.TorrentType
import com.felixbrucker.torrenthttpdownloader.core.network.ResolvedTorrent
import com.felixbrucker.torrenthttpdownloader.core.network.TorrentUriResolver
import com.felixbrucker.torrenthttpdownloader.feature.addtorrent.AddTorrentConfig
import com.felixbrucker.torrenthttpdownloader.feature.addtorrent.AddTorrentViewModel
import com.felixbrucker.torrenthttpdownloader.feature.downloads.DownloadServiceLauncher
import io.mockk.coEvery
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
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AddTorrentViewModelTest {
    private val sharedPreferences = mockk<SharedPreferences>()
    private val torrentUriResolver = mockk<TorrentUriResolver>()
    private val serviceLauncher = mockk<DownloadServiceLauncher>(relaxed = true)
    private val rssRepository = mockk<RssRepository>(relaxed = true)
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
    fun testGetDefaultPreferencesReturnsSavedValues() {
        every { sharedPreferences.getString("default_file_selection_mode", FileSelectionMode.ALL.name) } returns FileSelectionMode.BIGGEST.name
        every { sharedPreferences.getString("default_sub_dir", null) } returns "Movies"
        every { sharedPreferences.getBoolean("default_create_subfolder", true) } returns true
        every { sharedPreferences.getBoolean("default_notify_on_completion", false) } returns false
        val viewModel = AddTorrentViewModel(sharedPreferences, torrentUriResolver, serviceLauncher, rssRepository)

        val prefs = viewModel.getDefaultPreferences()

        assertEquals("Movies", prefs.defaultSubDir)
        assertEquals(true, prefs.defaultCreateSubfolder)
        assertEquals(false, prefs.defaultNotifyOnCompletion)
        assertEquals(FileSelectionMode.BIGGEST, prefs.defaultFileSelectionMode)
    }

    @Test
    fun testResolveTorrentUriSuccessUpdatesResolvedConfig() {
        val uri = mockk<Uri>()
        val resolvedTorrent = ResolvedTorrent(type = TorrentType.MAGNET, uri = uri, id = "torrent-1", name = "Test Torrent")
        coEvery { torrentUriResolver.resolve(uri) } returns resolvedTorrent
        val viewModel = AddTorrentViewModel(sharedPreferences, torrentUriResolver, serviceLauncher, rssRepository)

        viewModel.resolveTorrentUri(uri)

        assertEquals("torrent-1", viewModel.resolvedConfig.value?.id)
        assertEquals("Test Torrent", viewModel.resolvedConfig.value?.name)
    }

    @Test
    fun testConfirmAddTorrentLaunchesServiceAndMarksDownloaded() {
        val editor = mockk<SharedPreferences.Editor>(relaxed = true)
        every { sharedPreferences.edit() } returns editor
        val config = AddTorrentConfig(
            id = "torrent-1",
            uri = "http://example.com/test.torrent",
            type = TorrentType.TORRENT_FILE,
            feedId = "feed-1",
            feedItemId = "item-1"
        )
        val viewModel = AddTorrentViewModel(sharedPreferences, torrentUriResolver, serviceLauncher, rssRepository)

        viewModel.confirmAddTorrent(config)

        verify { serviceLauncher.addTask(config) }
        verify { rssRepository.markItemAsDownloaded("feed-1", "item-1") }
        assertNull(viewModel.resolvedConfig.value)
    }
}

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
    private val sharedPreferences = mockk<SharedPreferences>(relaxed = true)
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
    fun testResolveTorrentUriSuccessUpdatesResolvedConfigAndFormState() {
        val uri = mockk<Uri>()
        val resolvedTorrent = ResolvedTorrent(type = TorrentType.MAGNET, uri = uri, id = "torrent-1", name = "Test Torrent")
        coEvery { torrentUriResolver.resolve(uri) } returns resolvedTorrent
        every { sharedPreferences.getString("default_sub_dir", null) } returns "Movies"
        every { sharedPreferences.getBoolean("default_create_subfolder", true) } returns true
        every { sharedPreferences.getBoolean("default_notify_on_completion", false) } returns false
        every { sharedPreferences.getString("default_file_selection_mode", FileSelectionMode.ALL.name) } returns FileSelectionMode.BIGGEST.name
        val viewModel = AddTorrentViewModel(sharedPreferences, torrentUriResolver, serviceLauncher, rssRepository)

        viewModel.resolveTorrentUri(uri)

        assertEquals("torrent-1", viewModel.resolvedConfig.value?.id)
        assertEquals("Test Torrent", viewModel.nameState.value)
        assertEquals("Movies", viewModel.selectedSubDirState.value)
        assertEquals(true, viewModel.createSubfolderByNameState.value)
        assertEquals(false, viewModel.notifyOnCompletionState.value)
        assertEquals(FileSelectionMode.BIGGEST, viewModel.fileSelectionModeState.value)
    }

    @Test
    fun testConfirmAddTorrentLaunchesServiceAndSavesDefaultsWhenNotFeed() {
        val editor = mockk<SharedPreferences.Editor>(relaxed = true)
        every { sharedPreferences.edit() } returns editor
        val config = AddTorrentConfig(
            id = "torrent-1",
            uri = "http://example.com/test.torrent",
            type = TorrentType.TORRENT_FILE,
            name = "Test Torrent"
        )
        val viewModel = AddTorrentViewModel(sharedPreferences, torrentUriResolver, serviceLauncher, rssRepository)
        viewModel.setResolvedConfig(config)
        viewModel.updateName("Updated Torrent")
        viewModel.updateSelectedSubDir("Downloads")

        viewModel.confirmAddTorrent()

        verify { serviceLauncher.addTask(any()) }
        verify { editor.putString("default_sub_dir", "Downloads") }
        assertNull(viewModel.resolvedConfig.value)
    }

    @Test
    fun testDismissAddTorrentClearsConfig() {
        val config = AddTorrentConfig(
            id = "torrent-1",
            uri = "magnet:?xt=urn:btih:123",
            type = TorrentType.MAGNET
        )
        val viewModel = AddTorrentViewModel(sharedPreferences, torrentUriResolver, serviceLauncher, rssRepository)
        viewModel.setResolvedConfig(config)

        viewModel.dismissAddTorrent()

        assertNull(viewModel.resolvedConfig.value)
    }
}

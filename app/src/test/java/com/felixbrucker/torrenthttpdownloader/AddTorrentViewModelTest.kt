package com.felixbrucker.torrenthttpdownloader

import android.net.Uri
import com.felixbrucker.torrenthttpdownloader.core.data.RssRepository
import com.felixbrucker.torrenthttpdownloader.core.datastore.AppSettings
import com.felixbrucker.torrenthttpdownloader.core.datastore.AppSettingsRepository
import com.felixbrucker.torrenthttpdownloader.core.model.FileSelectionMode
import com.felixbrucker.torrenthttpdownloader.core.model.TorrentType
import com.felixbrucker.torrenthttpdownloader.core.network.ResolvedTorrent
import com.felixbrucker.torrenthttpdownloader.core.network.TorrentUriResolver
import com.felixbrucker.torrenthttpdownloader.feature.addtorrent.AddTorrentConfig
import com.felixbrucker.torrenthttpdownloader.feature.addtorrent.AddTorrentViewModel
import com.felixbrucker.torrenthttpdownloader.feature.downloads.DownloadServiceLauncher
import io.mockk.coEvery
import io.mockk.coVerify
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
    private val appSettingsRepository = mockk<AppSettingsRepository>(relaxed = true)
    private val torrentUriResolver = mockk<TorrentUriResolver>()
    private val serviceLauncher = mockk<DownloadServiceLauncher>(relaxed = true)
    private val rssRepository = mockk<RssRepository>(relaxed = true)
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        coEvery { appSettingsRepository.getSettings() } returns AppSettings()
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
        coEvery { appSettingsRepository.getSettings() } returns AppSettings(
            lastUsedSubDir = "Movies",
            lastUsedCreateSubfolder = true,
            lastUsedNotifyOnCompletion = false,
            lastUsedFileSelectionMode = FileSelectionMode.BIGGEST
        )
        val viewModel = AddTorrentViewModel(appSettingsRepository, torrentUriResolver, serviceLauncher, rssRepository)

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
        val config = AddTorrentConfig(
            id = "torrent-1",
            uri = "http://example.com/test.torrent",
            type = TorrentType.TORRENT_FILE,
            name = "Test Torrent"
        )
        val viewModel = AddTorrentViewModel(appSettingsRepository, torrentUriResolver, serviceLauncher, rssRepository)
        viewModel.setResolvedConfig(config)
        viewModel.updateName("Updated Torrent")
        viewModel.updateSelectedSubDir("Downloads")

        viewModel.confirmAddTorrent()

        verify { serviceLauncher.addTask(any()) }
        coVerify {
            appSettingsRepository.updateLastUsedAddTorrentOptions(
                lastUsedSubDir = "Downloads",
                lastUsedCreateSubfolder = true,
                lastUsedNotifyOnCompletion = false,
                lastUsedFileSelectionMode = FileSelectionMode.ALL
            )
        }
        assertNull(viewModel.resolvedConfig.value)
    }

    @Test
    fun testDismissAddTorrentClearsConfig() {
        val config = AddTorrentConfig(
            id = "torrent-1",
            uri = "magnet:?xt=urn:btih:123",
            type = TorrentType.MAGNET
        )
        val viewModel = AddTorrentViewModel(appSettingsRepository, torrentUriResolver, serviceLauncher, rssRepository)
        viewModel.setResolvedConfig(config)

        viewModel.dismissAddTorrent()

        assertNull(viewModel.resolvedConfig.value)
    }
}

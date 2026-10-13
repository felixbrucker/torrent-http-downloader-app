package com.felixbrucker.torrenthttpdownloader

import com.felixbrucker.torrenthttpdownloader.core.data.RssRepository
import com.felixbrucker.torrenthttpdownloader.core.model.RssFeed
import com.felixbrucker.torrenthttpdownloader.feature.rss.RssFeedsViewModel
import com.felixbrucker.torrenthttpdownloader.feature.rss.RssSyncLauncher
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
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RssFeedsViewModelTest {
    private val rssRepository = mockk<RssRepository>()
    private val rssSyncLauncher = mockk<RssSyncLauncher>(relaxed = true)
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
    fun testAddRssFeedDelegatesToRepository() {
        val feed = RssFeed("feed-1", "Name", "http://example.com")
        every { rssRepository.rssFeeds } returns MutableStateFlow(emptyList())
        every { rssRepository.isSyncingAll } returns MutableStateFlow(false)
        every { rssRepository.syncingFeedIds } returns MutableStateFlow(emptySet())
        coEvery { rssRepository.addRssFeed(feed) } returns Unit
        val viewModel = RssFeedsViewModel(rssRepository, rssSyncLauncher)

        viewModel.addRssFeed(feed)

        coVerify { rssRepository.addRssFeed(feed) }
    }

    @Test
    fun testSyncFeedDelegatesToRssSyncLauncher() {
        val feed = RssFeed("feed-1", "Name", "http://example.com")
        every { rssRepository.rssFeeds } returns MutableStateFlow(emptyList())
        every { rssRepository.isSyncingAll } returns MutableStateFlow(false)
        every { rssRepository.syncingFeedIds } returns MutableStateFlow(emptySet())
        val viewModel = RssFeedsViewModel(rssRepository, rssSyncLauncher)

        viewModel.syncFeed(feed)

        verify { rssSyncLauncher.runRssSyncOnce("feed-1") }
    }
}

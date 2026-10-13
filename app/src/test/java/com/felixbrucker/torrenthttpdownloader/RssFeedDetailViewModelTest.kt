package com.felixbrucker.torrenthttpdownloader

import com.felixbrucker.torrenthttpdownloader.core.data.RssRepository
import com.felixbrucker.torrenthttpdownloader.core.model.RssFeed
import com.felixbrucker.torrenthttpdownloader.feature.rss.RssFeedDetailViewModel
import com.felixbrucker.torrenthttpdownloader.feature.rss.RssSyncLauncher
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RssFeedDetailViewModelTest {
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
    fun testGetFeedFlowReturnsMatchingFeed() = runTest {
        val feed = RssFeed("feed-1", "Name", "http://example.com")
        every { rssRepository.getFeedFlow("feed-1") } returns MutableStateFlow(feed)
        every { rssRepository.syncingFeedIds } returns MutableStateFlow(emptySet())
        val viewModel = RssFeedDetailViewModel(rssRepository, rssSyncLauncher)

        val result = viewModel.getFeedFlow("feed-1").first()

        assertEquals(feed, result)
    }

    @Test
    fun testMarkItemAsReadDelegatesToRepository() {
        every { rssRepository.rssFeeds } returns MutableStateFlow(emptyList())
        every { rssRepository.syncingFeedIds } returns MutableStateFlow(emptySet())
        coEvery { rssRepository.markItemAsRead("feed-1", "item-1") } returns Unit
        val viewModel = RssFeedDetailViewModel(rssRepository, rssSyncLauncher)

        viewModel.markItemAsRead("feed-1", "item-1")

        coVerify { rssRepository.markItemAsRead("feed-1", "item-1") }
    }

    @Test
    fun testSyncFeedDelegatesToLauncher() {
        val feed = RssFeed("feed-1", "Name", "http://example.com")
        every { rssRepository.rssFeeds } returns MutableStateFlow(emptyList())
        every { rssRepository.syncingFeedIds } returns MutableStateFlow(emptySet())
        val viewModel = RssFeedDetailViewModel(rssRepository, rssSyncLauncher)

        viewModel.syncFeed(feed)

        verify { rssSyncLauncher.runRssSyncOnce("feed-1") }
    }
}

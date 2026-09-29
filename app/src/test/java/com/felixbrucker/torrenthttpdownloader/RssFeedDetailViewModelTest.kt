package com.felixbrucker.torrenthttpdownloader

import com.felixbrucker.torrenthttpdownloader.core.data.DownloadTracker
import com.felixbrucker.torrenthttpdownloader.core.data.RssRepository
import com.felixbrucker.torrenthttpdownloader.core.model.RssFeed
import com.felixbrucker.torrenthttpdownloader.feature.rss.RssFeedDetailViewModel
import com.felixbrucker.torrenthttpdownloader.feature.rss.RssSyncLauncher
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class RssFeedDetailViewModelTest {
    private val downloadTracker = mockk<DownloadTracker>()
    private val rssRepository = mockk<RssRepository>()
    private val rssSyncLauncher = mockk<RssSyncLauncher>(relaxed = true)

    @Test
    fun testGetFeedFlowReturnsMatchingFeed() = runTest {
        val feed = RssFeed("feed-1", "Name", "http://example.com")
        every { downloadTracker.rssFeeds } returns MutableStateFlow(listOf(feed))
        every { downloadTracker.syncingFeedIds } returns MutableStateFlow(emptySet())
        val viewModel = RssFeedDetailViewModel(downloadTracker, rssRepository, rssSyncLauncher)

        val result = viewModel.getFeedFlow("feed-1").first()

        assertEquals(feed, result)
    }

    @Test
    fun testMarkItemAsReadDelegatesToRepository() {
        every { downloadTracker.rssFeeds } returns MutableStateFlow(emptyList())
        every { downloadTracker.syncingFeedIds } returns MutableStateFlow(emptySet())
        every { rssRepository.markItemAsRead("feed-1", "item-1") } returns Unit
        val viewModel = RssFeedDetailViewModel(downloadTracker, rssRepository, rssSyncLauncher)

        viewModel.markItemAsRead("feed-1", "item-1")

        verify { rssRepository.markItemAsRead("feed-1", "item-1") }
    }

    @Test
    fun testSyncFeedDelegatesToLauncher() {
        val feed = RssFeed("feed-1", "Name", "http://example.com")
        every { downloadTracker.rssFeeds } returns MutableStateFlow(emptyList())
        every { downloadTracker.syncingFeedIds } returns MutableStateFlow(emptySet())
        val viewModel = RssFeedDetailViewModel(downloadTracker, rssRepository, rssSyncLauncher)

        viewModel.syncFeed(feed)

        verify { rssSyncLauncher.runRssSyncOnce("feed-1") }
    }
}

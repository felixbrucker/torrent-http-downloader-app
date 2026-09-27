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
import org.junit.Test

class RssFeedDetailViewModelTest {
    private val downloadTracker = mockk<DownloadTracker>()
    private val rssRepository = mockk<RssRepository>()
    private val rssSyncLauncher = mockk<RssSyncLauncher>(relaxed = true)

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

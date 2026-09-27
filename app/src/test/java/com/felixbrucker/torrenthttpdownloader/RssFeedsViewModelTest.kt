package com.felixbrucker.torrenthttpdownloader

import com.felixbrucker.torrenthttpdownloader.core.data.DownloadTracker
import com.felixbrucker.torrenthttpdownloader.core.data.RssRepository
import com.felixbrucker.torrenthttpdownloader.core.model.RssFeed
import com.felixbrucker.torrenthttpdownloader.feature.rss.RssFeedsViewModel
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Test

class RssFeedsViewModelTest {
    private val downloadTracker = mockk<DownloadTracker>()
    private val rssRepository = mockk<RssRepository>()

    @Test
    fun testAddRssFeedDelegatesToTracker() {
        val feed = RssFeed("feed-1", "Name", "http://example.com")
        every { downloadTracker.rssFeeds } returns MutableStateFlow(emptyList())
        every { downloadTracker.isSyncingAll } returns MutableStateFlow(false)
        every { downloadTracker.syncingFeedIds } returns MutableStateFlow(emptySet())
        every { downloadTracker.addRssFeed(feed) } returns Unit
        val viewModel = RssFeedsViewModel(downloadTracker, rssRepository)

        viewModel.addRssFeed(feed)

        verify { downloadTracker.addRssFeed(feed) }
    }

    @Test
    fun testRemoveRssFeedDelegatesToTracker() {
        every { downloadTracker.rssFeeds } returns MutableStateFlow(emptyList())
        every { downloadTracker.isSyncingAll } returns MutableStateFlow(false)
        every { downloadTracker.syncingFeedIds } returns MutableStateFlow(emptySet())
        every { downloadTracker.removeRssFeed("feed-1") } returns Unit
        val viewModel = RssFeedsViewModel(downloadTracker, rssRepository)

        viewModel.removeRssFeed("feed-1")

        verify { downloadTracker.removeRssFeed("feed-1") }
    }

    @Test
    fun testUpdateFeedConfigDelegatesToRepository() {
        val feed = RssFeed("feed-1", "Name", "http://example.com")
        every { downloadTracker.rssFeeds } returns MutableStateFlow(emptyList())
        every { downloadTracker.isSyncingAll } returns MutableStateFlow(false)
        every { downloadTracker.syncingFeedIds } returns MutableStateFlow(emptySet())
        every { rssRepository.updateFeedConfig(feed) } returns Unit
        val viewModel = RssFeedsViewModel(downloadTracker, rssRepository)

        viewModel.updateFeedConfig(feed)

        verify { rssRepository.updateFeedConfig(feed) }
    }
}

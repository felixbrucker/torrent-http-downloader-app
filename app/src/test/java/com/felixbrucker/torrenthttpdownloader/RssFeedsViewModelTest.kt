package com.felixbrucker.torrenthttpdownloader

import com.felixbrucker.torrenthttpdownloader.core.data.RssRepository
import com.felixbrucker.torrenthttpdownloader.core.model.RssFeed
import com.felixbrucker.torrenthttpdownloader.feature.rss.RssFeedsViewModel
import com.felixbrucker.torrenthttpdownloader.feature.rss.RssSyncLauncher
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Test

class RssFeedsViewModelTest {
    private val rssRepository = mockk<RssRepository>()
    private val rssSyncLauncher = mockk<RssSyncLauncher>(relaxed = true)

    @Test
    fun testAddRssFeedDelegatesToRepository() {
        val feed = RssFeed("feed-1", "Name", "http://example.com")
        every { rssRepository.rssFeeds } returns MutableStateFlow(emptyList())
        every { rssRepository.isSyncingAll } returns MutableStateFlow(false)
        every { rssRepository.syncingFeedIds } returns MutableStateFlow(emptySet())
        every { rssRepository.addRssFeed(feed) } returns Unit
        val viewModel = RssFeedsViewModel(rssRepository, rssSyncLauncher)

        viewModel.addRssFeed(feed)

        verify { rssRepository.addRssFeed(feed) }
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

package com.felixbrucker.torrenthttpdownloader

import com.felixbrucker.torrenthttpdownloader.core.data.DownloadTracker
import com.felixbrucker.torrenthttpdownloader.core.data.RssRepository
import com.felixbrucker.torrenthttpdownloader.feature.rss.RssFeedDetailViewModel
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Test

class RssFeedDetailViewModelTest {
    private val downloadTracker = mockk<DownloadTracker>()
    private val rssRepository = mockk<RssRepository>()

    @Test
    fun testMarkItemAsReadDelegatesToRepository() {
        every { downloadTracker.rssFeeds } returns MutableStateFlow(emptyList())
        every { downloadTracker.syncingFeedIds } returns MutableStateFlow(emptySet())
        every { rssRepository.markItemAsRead("feed-1", "item-1") } returns Unit
        val viewModel = RssFeedDetailViewModel(downloadTracker, rssRepository)

        viewModel.markItemAsRead("feed-1", "item-1")

        verify { rssRepository.markItemAsRead("feed-1", "item-1") }
    }

    @Test
    fun testMarkAllItemsAsReadDelegatesToRepository() {
        every { downloadTracker.rssFeeds } returns MutableStateFlow(emptyList())
        every { downloadTracker.syncingFeedIds } returns MutableStateFlow(emptySet())
        every { rssRepository.markAllItemsAsRead("feed-1") } returns Unit
        val viewModel = RssFeedDetailViewModel(downloadTracker, rssRepository)

        viewModel.markAllItemsAsRead("feed-1")

        verify { rssRepository.markAllItemsAsRead("feed-1") }
    }
}

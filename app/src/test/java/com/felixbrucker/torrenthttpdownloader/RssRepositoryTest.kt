package com.felixbrucker.torrenthttpdownloader

import com.felixbrucker.torrenthttpdownloader.core.data.DownloadTracker
import com.felixbrucker.torrenthttpdownloader.core.data.RssRepository
import com.felixbrucker.torrenthttpdownloader.core.model.RssFeed
import com.felixbrucker.torrenthttpdownloader.core.model.RssItem
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RssRepositoryTest {
    private val downloadTracker = mockk<DownloadTracker>(relaxed = true)
    private val repository = RssRepository(downloadTracker)

    @Test
    fun testRssFeedsFlowDelegatesToTracker() {
        val flow = MutableStateFlow<List<RssFeed>>(emptyList())
        every { downloadTracker.rssFeeds } returns flow

        val result = repository.rssFeeds

        assertEquals(flow, result)
        verify { downloadTracker.rssFeeds }
    }

    @Test
    fun testSyncingFeedIdsFlowDelegatesToTracker() {
        val flow = MutableStateFlow<Set<String>>(emptySet())
        every { downloadTracker.syncingFeedIds } returns flow

        val result = repository.syncingFeedIds

        assertEquals(flow, result)
        verify { downloadTracker.syncingFeedIds }
    }

    @Test
    fun testIsSyncingAllFlowDelegatesToTracker() {
        val flow = MutableStateFlow(false)
        every { downloadTracker.isSyncingAll } returns flow

        val result = repository.isSyncingAll

        assertEquals(flow, result)
        verify { downloadTracker.isSyncingAll }
    }

    @Test
    fun testTotalUnreadRssCountFlowDelegatesToTracker() {
        val flow = MutableStateFlow(0)
        every { downloadTracker.totalUnreadRssCount } returns flow

        val result = repository.totalUnreadRssCount

        assertEquals(flow, result)
        verify { downloadTracker.totalUnreadRssCount }
    }

    @Test
    fun testAddRssFeedDelegatesToTracker() {
        val feed = RssFeed(id = "feed-1", name = "Feed 1", url = "http://example.com")
        every { downloadTracker.addRssFeed(feed) } returns Unit

        repository.addRssFeed(feed)

        verify { downloadTracker.addRssFeed(feed) }
    }

    @Test
    fun testUpdateRssFeedDelegatesToTracker() {
        val updateLambda: (RssFeed) -> RssFeed = { it }
        every { downloadTracker.updateRssFeed("feed-1", updateLambda) } returns Unit

        repository.updateRssFeed("feed-1", updateLambda)

        verify { downloadTracker.updateRssFeed("feed-1", updateLambda) }
    }

    @Test
    fun testRemoveRssFeedDelegatesToTracker() {
        every { downloadTracker.removeRssFeed("feed-1") } returns Unit

        repository.removeRssFeed("feed-1")

        verify { downloadTracker.removeRssFeed("feed-1") }
    }

    @Test
    fun testSetFeedSyncingDelegatesToTracker() {
        every { downloadTracker.setFeedSyncing("feed-1", true) } returns Unit

        repository.setFeedSyncing("feed-1", true)

        verify { downloadTracker.setFeedSyncing("feed-1", true) }
    }

    @Test
    fun testSetAllFeedsSyncingDelegatesToTracker() {
        every { downloadTracker.setAllFeedsSyncing(true) } returns Unit

        repository.setAllFeedsSyncing(true)

        verify { downloadTracker.setAllFeedsSyncing(true) }
    }

    @Test
    fun testMarkAllItemsAsReadSuccess() {
        val feedId = "feed-1"
        val feedSlot = slot<(RssFeed) -> RssFeed>()
        every { downloadTracker.updateRssFeed(feedId, capture(feedSlot)) } returns Unit

        repository.markAllItemsAsRead(feedId)

        verify { downloadTracker.updateRssFeed(feedId, any()) }
        val initialFeed = RssFeed(
            id = feedId,
            name = "Test Feed",
            url = "http://example.com/rss",
            items = listOf(
                RssItem(id = "item-1", title = "Item 1", link = "link1", isRead = false),
                RssItem(id = "item-2", title = "Item 2", link = "link2", isRead = false)
            )
        )
        val updatedFeed = feedSlot.captured(initialFeed)
        assertTrue(updatedFeed.items.all { it.isRead })
    }

    @Test
    fun testMarkItemAsReadSuccess() {
        val feedId = "feed-1"
        val feedSlot = slot<(RssFeed) -> RssFeed>()
        every { downloadTracker.updateRssFeed(feedId, capture(feedSlot)) } returns Unit

        repository.markItemAsRead(feedId, "item-1")

        verify { downloadTracker.updateRssFeed(feedId, any()) }
        val initialFeed = RssFeed(
            id = feedId,
            name = "Test Feed",
            url = "http://example.com/rss",
            items = listOf(
                RssItem(id = "item-1", title = "Item 1", link = "link1", isRead = false),
                RssItem(id = "item-2", title = "Item 2", link = "link2", isRead = false)
            )
        )
        val updatedFeed = feedSlot.captured(initialFeed)
        assertTrue(updatedFeed.items.find { it.id == "item-1" }?.isRead == true)
        assertTrue(updatedFeed.items.find { it.id == "item-2" }?.isRead == false)
    }

    @Test
    fun testMarkItemAsDownloadedSuccess() {
        val feedId = "feed-1"
        val feedSlot = slot<(RssFeed) -> RssFeed>()
        every { downloadTracker.updateRssFeed(feedId, capture(feedSlot)) } returns Unit

        repository.markItemAsDownloaded(feedId, "item-1")

        verify { downloadTracker.updateRssFeed(feedId, any()) }
        val initialFeed = RssFeed(
            id = feedId,
            name = "Test Feed",
            url = "http://example.com/rss",
            items = listOf(
                RssItem(id = "item-1", title = "Item 1", link = "link1", isDownloaded = false)
            )
        )
        val updatedFeed = feedSlot.captured(initialFeed)
        assertTrue(updatedFeed.items.first().isDownloaded)
    }

    @Test
    fun testUpdateFeedConfigResetsWhenUrlChanged() {
        val feedId = "feed-1"
        val feedSlot = slot<(RssFeed) -> RssFeed>()
        every { downloadTracker.updateRssFeed(feedId, capture(feedSlot)) } returns Unit
        val newFeedConfig = RssFeed(
            id = feedId,
            name = "Test Feed Updated",
            url = "http://example.com/new-rss",
            lastCheck = 1000L,
            items = listOf(RssItem(id = "old-item", title = "Old", link = "link"))
        )

        repository.updateFeedConfig(newFeedConfig)

        verify { downloadTracker.updateRssFeed(feedId, any()) }
        val existingFeed = RssFeed(
            id = feedId,
            name = "Test Feed",
            url = "http://example.com/old-rss",
            lastCheck = 500L,
            items = listOf(RssItem(id = "old-item", title = "Old", link = "link"))
        )
        val updatedFeed = feedSlot.captured(existingFeed)
        assertEquals(0L, updatedFeed.lastCheck)
        assertTrue(updatedFeed.items.isEmpty())
    }

    @Test
    fun testUpdateDiscoveredItemsSuccess() {
        val feedId = "feed-1"
        val feedSlot = slot<(RssFeed) -> RssFeed>()
        every { downloadTracker.updateRssFeed(feedId, capture(feedSlot)) } returns Unit
        val newItem = RssItem(id = "new-item", title = "New", link = "new-link")

        repository.updateDiscoveredItems(feedId, listOf(newItem), 1, 2000L)

        verify { downloadTracker.updateRssFeed(feedId, any()) }
        val existingFeed = RssFeed(
            id = feedId,
            name = "Test Feed",
            url = "http://example.com/rss",
            lastCheck = 1000L,
            items = listOf(RssItem(id = "old-item", title = "Old", link = "old-link"))
        )
        val updatedFeed = feedSlot.captured(existingFeed)
        assertEquals(2000L, updatedFeed.lastCheck)
        assertEquals(2, updatedFeed.items.size)
        assertEquals("new-item", updatedFeed.items.first().id)
    }
}

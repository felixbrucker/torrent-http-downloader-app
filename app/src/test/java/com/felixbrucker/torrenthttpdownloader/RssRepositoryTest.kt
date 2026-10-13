package com.felixbrucker.torrenthttpdownloader

import com.felixbrucker.torrenthttpdownloader.core.data.RssRepository
import com.felixbrucker.torrenthttpdownloader.core.database.dao.RssDao
import com.felixbrucker.torrenthttpdownloader.core.database.entity.RssFeedEntity
import com.felixbrucker.torrenthttpdownloader.core.database.entity.RssFeedWithItems
import com.felixbrucker.torrenthttpdownloader.core.database.entity.RssItemEntity
import com.felixbrucker.torrenthttpdownloader.core.model.RssFeed
import com.felixbrucker.torrenthttpdownloader.core.model.RssItem
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RssRepositoryTest {
    private val rssDao = mockk<RssDao>(relaxed = true)
    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private val feedsWithItemsFlow = MutableStateFlow<List<RssFeedWithItems>>(emptyList())
    private val unreadCountFlow = MutableStateFlow(0)

    private fun createRepository(): RssRepository {
        coEvery { rssDao.getFeedsWithItemsFlow() } returns feedsWithItemsFlow
        coEvery { rssDao.getTotalUnreadCountFlow() } returns unreadCountFlow
        return RssRepository(
            rssDao = rssDao,
            scope = testScope,
        )
    }

    @Test
    fun testRssFeedsFlowEmitsFromDao() = runTest(testDispatcher) {
        val feedEntity = RssFeedEntity(id = "feed-1", name = "Feed 1", url = "http://example.com")
        feedsWithItemsFlow.value = listOf(RssFeedWithItems(feedEntity, emptyList()))
        val repository = createRepository()

        val result = repository.rssFeeds.first()

        assertEquals(1, result.size)
        assertEquals("feed-1", result[0].id)
    }

    @Test
    fun testTotalUnreadRssCountFlowDelegatesToDao() {
        unreadCountFlow.value = 5
        val repository = createRepository()

        val result = repository.totalUnreadRssCount

        assertEquals(unreadCountFlow, result)
    }

    @Test
    fun testAddRssFeedDelegatesToDao() = runTest(testDispatcher) {
        val feed = RssFeed(id = "feed-1", name = "Feed 1", url = "http://example.com")
        val repository = createRepository()

        repository.addRssFeed(feed)

        coVerify { rssDao.insertFeed(match { it.id == "feed-1" }) }
    }

    @Test
    fun testFindRssFeedReturnsFeedWhenFound() = runTest(testDispatcher) {
        val feedEntity = RssFeedEntity(id = "feed-1", name = "Feed 1", url = "http://example.com")
        coEvery { rssDao.getFeedWithItems("feed-1") } returns RssFeedWithItems(feedEntity, emptyList())
        val repository = createRepository()

        val result = repository.findRssFeed("feed-1")

        assertEquals("feed-1", result?.id)
    }

    @Test
    fun testGetFeedFlowEmitsFeed() = runTest(testDispatcher) {
        val feedEntity = RssFeedEntity(id = "feed-1", name = "Feed 1", url = "http://example.com")
        coEvery { rssDao.getFeedWithItemsFlow("feed-1") } returns MutableStateFlow(RssFeedWithItems(feedEntity, emptyList()))
        val repository = createRepository()

        val emittedFeed = repository.getFeedFlow("feed-1").first()

        assertEquals("feed-1", emittedFeed?.id)
    }

    @Test
    fun testRemoveRssFeedDelegatesToDao() = runTest(testDispatcher) {
        val repository = createRepository()

        repository.removeRssFeed("feed-1")

        coVerify { rssDao.deleteFeed("feed-1") }
    }

    @Test
    fun testSyncStateFlags() {
        val repository = createRepository()

        repository.setFeedSyncing("feed-1", true)
        repository.setAllFeedsSyncing(true)

        assertTrue(repository.syncingFeedIds.value.contains("feed-1"))
        assertTrue(repository.isSyncingAll.value)

        repository.setFeedSyncing("feed-1", false)
        repository.setAllFeedsSyncing(false)

        assertFalse(repository.syncingFeedIds.value.contains("feed-1"))
        assertFalse(repository.isSyncingAll.value)
    }

    @Test
    fun testMarkAllItemsAsReadDelegatesToDao() = runTest(testDispatcher) {
        val repository = createRepository()

        repository.markAllItemsAsRead("feed-1")

        coVerify { rssDao.markAllItemsAsRead("feed-1") }
    }

    @Test
    fun testMarkItemAsReadDelegatesToDao() = runTest(testDispatcher) {
        val repository = createRepository()

        repository.markItemAsRead("feed-1", "item-1")

        coVerify { rssDao.markItemAsRead("feed-1", "item-1") }
    }

    @Test
    fun testMarkItemAsDownloadedDelegatesToDao() = runTest(testDispatcher) {
        val repository = createRepository()

        repository.markItemAsDownloaded("feed-1", "item-1")

        coVerify { rssDao.markItemAsDownloaded("feed-1", "item-1") }
    }

    @Test
    fun testMarkItemAsDownloadedAndReadDelegatesToDao() = runTest(testDispatcher) {
        val repository = createRepository()

        repository.markItemAsDownloadedAndRead("feed-1", "item-1")

        coVerify { rssDao.markItemAsDownloadedAndRead("feed-1", "item-1") }
    }

    @Test
    fun testUpdateFeedConfigResetsWhenUrlChanged() = runTest(testDispatcher) {
        val feedEntity = RssFeedEntity(id = "feed-1", name = "Old Feed", url = "http://example.com/old")
        coEvery { rssDao.getFeed("feed-1") } returns feedEntity
        val repository = createRepository()
        val newFeed = RssFeed(id = "feed-1", name = "New Feed", url = "http://example.com/new")

        repository.updateFeedConfig(newFeed)

        coVerify { rssDao.deleteItemsForFeed("feed-1") }
        coVerify { rssDao.updateFeed(match { it.id == "feed-1" && it.name == "New Feed" && it.url == "http://example.com/new" }) }
    }

    @Test
    fun testUpdateFeedConfigSameUrlDoesNotReset() = runTest(testDispatcher) {
        val feedEntity = RssFeedEntity(id = "feed-1", name = "Old Feed", url = "http://example.com/same")
        coEvery { rssDao.getFeed("feed-1") } returns feedEntity
        val repository = createRepository()
        val newFeed = RssFeed(id = "feed-1", name = "New Feed", url = "http://example.com/same")

        repository.updateFeedConfig(newFeed)

        coVerify(exactly = 0) { rssDao.deleteItemsForFeed("feed-1") }
        coVerify { rssDao.updateFeed(match { it.id == "feed-1" && it.name == "New Feed" && it.url == "http://example.com/same" }) }
    }

    @Test
    fun testUpdateDiscoveredItemsDelegatesToDaoAndPrunes() = runTest(testDispatcher) {
        val feedEntity = RssFeedEntity(id = "feed-1", name = "Feed 1", url = "http://example.com")
        val existingItems = (1..30).map { RssItemEntity(feedId = "feed-1", id = "item-$it", title = "T$it", link = "l$it", pubDate = it.toLong()) }
        coEvery { rssDao.getFeedWithItems("feed-1") } returns RssFeedWithItems(feedEntity, existingItems)
        val repository = createRepository()
        val newItem = RssItem(id = "new-item", title = "New", link = "http://example.com/item-new", pubDate = 1000L)

        repository.updateDiscoveredItems("feed-1", listOf(newItem), 1, 2000L)

        coVerify { rssDao.insertItems(match { it.size == 1 && it[0].id == "new-item" }) }
        coVerify { rssDao.updateLastCheck("feed-1", 2000L) }
        coVerify { rssDao.deleteItems("feed-1", any()) }
    }

    @Test
    fun testUpdateLastCheckDelegatesToDao() = runTest(testDispatcher) {
        val repository = createRepository()

        repository.updateLastCheck("feed-1", 5000L)

        coVerify { rssDao.updateLastCheck("feed-1", 5000L) }
    }
}

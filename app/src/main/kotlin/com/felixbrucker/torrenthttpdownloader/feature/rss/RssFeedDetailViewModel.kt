package com.felixbrucker.torrenthttpdownloader.feature.rss

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import com.felixbrucker.torrenthttpdownloader.core.data.DownloadTracker
import com.felixbrucker.torrenthttpdownloader.core.data.RssRepository
import com.felixbrucker.torrenthttpdownloader.core.model.RssFeed

@HiltViewModel
class RssFeedDetailViewModel @Inject constructor(
    private val downloadTracker: DownloadTracker,
    private val rssRepository: RssRepository,
    private val rssSyncLauncher: RssSyncLauncher
) : ViewModel() {

    val syncingFeedIds: StateFlow<Set<String>> = downloadTracker.syncingFeedIds

    fun getFeedFlow(feedId: String): Flow<RssFeed?> {
        return downloadTracker.rssFeeds.map { feeds -> feeds.find { it.id == feedId } }
    }

    fun markItemAsRead(feedId: String, itemId: String) {
        rssRepository.markItemAsRead(feedId, itemId)
    }

    fun markAllItemsAsRead(feedId: String) {
        rssRepository.markAllItemsAsRead(feedId)
    }

    fun syncFeed(feed: RssFeed) {
        rssSyncLauncher.runRssSyncOnce(feed.id)
    }
}

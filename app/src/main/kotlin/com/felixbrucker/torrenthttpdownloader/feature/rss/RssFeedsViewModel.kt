package com.felixbrucker.torrenthttpdownloader.feature.rss

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow
import com.felixbrucker.torrenthttpdownloader.core.data.DownloadTracker
import com.felixbrucker.torrenthttpdownloader.core.data.RssRepository
import com.felixbrucker.torrenthttpdownloader.core.model.RssFeed

@HiltViewModel
class RssFeedsViewModel @Inject constructor(
    private val downloadTracker: DownloadTracker,
    private val rssRepository: RssRepository,
    private val rssSyncLauncher: RssSyncLauncher
) : ViewModel() {

    val feeds: StateFlow<List<RssFeed>> = downloadTracker.rssFeeds
    val isSyncingAll: StateFlow<Boolean> = downloadTracker.isSyncingAll
    val syncingFeedIds: StateFlow<Set<String>> = downloadTracker.syncingFeedIds

    fun addRssFeed(feed: RssFeed) {
        downloadTracker.addRssFeed(feed)
    }

    fun removeRssFeed(feedId: String) {
        downloadTracker.removeRssFeed(feedId)
    }

    fun updateFeedConfig(feed: RssFeed) {
        rssRepository.updateFeedConfig(feed)
    }

    fun syncFeed(feed: RssFeed) {
        rssSyncLauncher.runRssSyncOnce(feed.id)
    }

    fun syncFeeds() {
        rssSyncLauncher.runRssSyncOnce()
    }
}

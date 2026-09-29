package com.felixbrucker.torrenthttpdownloader.feature.rss

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow
import com.felixbrucker.torrenthttpdownloader.core.data.RssRepository
import com.felixbrucker.torrenthttpdownloader.core.model.RssFeed

@HiltViewModel
class RssFeedsViewModel @Inject constructor(
    private val rssRepository: RssRepository,
    private val rssSyncLauncher: RssSyncLauncher
) : ViewModel() {

    val feeds: StateFlow<List<RssFeed>> = rssRepository.rssFeeds
    val isSyncingAll: StateFlow<Boolean> = rssRepository.isSyncingAll
    val syncingFeedIds: StateFlow<Set<String>> = rssRepository.syncingFeedIds

    fun addRssFeed(feed: RssFeed) {
        rssRepository.addRssFeed(feed)
    }

    fun removeRssFeed(feedId: String) {
        rssRepository.removeRssFeed(feedId)
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

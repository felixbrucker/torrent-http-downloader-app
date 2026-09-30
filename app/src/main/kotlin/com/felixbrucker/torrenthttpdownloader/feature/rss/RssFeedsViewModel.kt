package com.felixbrucker.torrenthttpdownloader.feature.rss

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow
import com.felixbrucker.torrenthttpdownloader.core.data.RssRepository
import com.felixbrucker.torrenthttpdownloader.core.model.RssFeed
import timber.log.Timber

@HiltViewModel
class RssFeedsViewModel @Inject constructor(
    private val rssRepository: RssRepository,
    private val rssSyncLauncher: RssSyncLauncher
) : ViewModel() {

    val feeds: StateFlow<List<RssFeed>> = rssRepository.rssFeeds
    val isSyncingAll: StateFlow<Boolean> = rssRepository.isSyncingAll
    val syncingFeedIds: StateFlow<Set<String>> = rssRepository.syncingFeedIds

    fun addRssFeed(feed: RssFeed) {
        Timber.i("Adding RSS feed name=%s", feed.name)
        rssRepository.addRssFeed(feed)
    }

    fun removeRssFeed(feedId: String) {
        Timber.i("Removing RSS feed id=%s", feedId)
        rssRepository.removeRssFeed(feedId)
    }

    fun updateFeedConfig(feed: RssFeed) {
        Timber.i("Updating RSS feed config id=%s", feed.id)
        rssRepository.updateFeedConfig(feed)
    }

    fun syncFeed(feed: RssFeed) {
        Timber.d("Syncing RSS feed id=%s", feed.id)
        rssSyncLauncher.runRssSyncOnce(feed.id)
    }

    fun syncFeeds() {
        Timber.d("Syncing all RSS feeds")
        rssSyncLauncher.runRssSyncOnce()
    }
}

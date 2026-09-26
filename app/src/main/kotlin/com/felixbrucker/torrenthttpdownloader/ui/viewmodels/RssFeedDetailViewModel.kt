package com.felixbrucker.torrenthttpdownloader.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.felixbrucker.torrenthttpdownloader.data.repository.RssRepository
import com.felixbrucker.torrenthttpdownloader.models.RssFeed
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RssFeedDetailViewModel @Inject constructor(
    private val rssRepository: RssRepository
) : ViewModel() {

    private val _feed = MutableStateFlow<RssFeed?>(null)
    val feed: StateFlow<RssFeed?> = _feed.asStateFlow()

    fun loadFeed(feedId: String) {
        viewModelScope.launch {
            _feed.value = rssRepository.getFeedById(feedId)
        }
    }

    fun updateFeed(feed: RssFeed) {
        viewModelScope.launch {
            rssRepository.insertFeed(feed)
            _feed.value = feed
        }
    }
}

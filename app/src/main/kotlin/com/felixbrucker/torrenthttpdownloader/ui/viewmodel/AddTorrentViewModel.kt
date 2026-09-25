package com.felixbrucker.torrenthttpdownloader.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.felixbrucker.torrenthttpdownloader.data.repository.DownloadRepository
import com.felixbrucker.torrenthttpdownloader.models.DownloadTask
import com.felixbrucker.torrenthttpdownloader.network.ResolvedTorrent
import com.felixbrucker.torrenthttpdownloader.network.TorrentUriResolver
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AddTorrentViewModel @Inject constructor(
    private val torrentUriResolver: TorrentUriResolver,
    private val downloadRepository: DownloadRepository
) : ViewModel() {

    private val _isResolving = MutableStateFlow(false)
    val isResolving: StateFlow<Boolean> = _isResolving.asStateFlow()

    private val _resolvedTorrent = MutableStateFlow<ResolvedTorrent?>(null)
    val resolvedTorrent: StateFlow<ResolvedTorrent?> = _resolvedTorrent.asStateFlow()

    fun resolveUri(uri: Uri) {
        viewModelScope.launch {
            _isResolving.value = true
            try {
                _resolvedTorrent.value = torrentUriResolver.resolve(uri)
            } catch (e: Exception) {
                _resolvedTorrent.value = null
            } finally {
                _isResolving.value = false
            }
        }
    }

    fun addDownloadTask(task: DownloadTask) {
        viewModelScope.launch {
            downloadRepository.insertTask(task)
        }
    }

    fun clearResolvedTorrent() {
        _resolvedTorrent.value = null
    }
}

package com.felixbrucker.torrenthttpdownloader.feature.addtorrent

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.felixbrucker.torrenthttpdownloader.core.data.RssRepository
import com.felixbrucker.torrenthttpdownloader.core.datastore.AppSettingsRepository
import com.felixbrucker.torrenthttpdownloader.core.model.FileSelectionMode
import com.felixbrucker.torrenthttpdownloader.core.network.TorrentUriResolver
import com.felixbrucker.torrenthttpdownloader.feature.downloads.DownloadServiceLauncher
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

@HiltViewModel
class AddTorrentViewModel @Inject constructor(
    private val appSettingsRepository: AppSettingsRepository,
    private val torrentUriResolver: TorrentUriResolver,
    private val serviceLauncher: DownloadServiceLauncher,
    private val rssRepository: RssRepository
) : ViewModel() {

    private val _isResolvingTorrent = MutableStateFlow(false)
    val isResolvingTorrent: StateFlow<Boolean> = _isResolvingTorrent.asStateFlow()

    private val _resolvedConfig = MutableStateFlow<AddTorrentConfig?>(null)
    val resolvedConfig: StateFlow<AddTorrentConfig?> = _resolvedConfig.asStateFlow()

    private val _nameState = MutableStateFlow("")
    val nameState: StateFlow<String> = _nameState.asStateFlow()

    private val _selectedSubDirState = MutableStateFlow<String?>(null)
    val selectedSubDirState: StateFlow<String?> = _selectedSubDirState.asStateFlow()

    private val _createSubfolderByNameState = MutableStateFlow(true)
    val createSubfolderByNameState: StateFlow<Boolean> = _createSubfolderByNameState.asStateFlow()

    private val _notifyOnCompletionState = MutableStateFlow(false)
    val notifyOnCompletionState: StateFlow<Boolean> = _notifyOnCompletionState.asStateFlow()

    private val _fileSelectionModeState = MutableStateFlow(FileSelectionMode.ALL)
    val fileSelectionModeState: StateFlow<FileSelectionMode> = _fileSelectionModeState.asStateFlow()

    fun updateName(name: String) {
        _nameState.value = name
    }

    fun updateSelectedSubDir(subDir: String?) {
        _selectedSubDirState.value = subDir
    }

    fun updateCreateSubfolderByName(create: Boolean) {
        _createSubfolderByNameState.value = create
    }

    fun updateNotifyOnCompletion(notify: Boolean) {
        _notifyOnCompletionState.value = notify
    }

    fun updateFileSelectionMode(mode: FileSelectionMode) {
        _fileSelectionModeState.value = mode
    }

    private fun initFormState(config: AddTorrentConfig) {
        _nameState.value = config.name ?: ""
        viewModelScope.launch {
            val settings = appSettingsRepository.getSettings()
            _selectedSubDirState.value = config.destinationSubdirectory ?: settings.lastUsedSubDir
            _createSubfolderByNameState.value = config.createSubfolderByName ?: settings.lastUsedCreateSubfolder
            _notifyOnCompletionState.value = config.notifyOnCompletion ?: settings.lastUsedNotifyOnCompletion
            _fileSelectionModeState.value = config.fileSelectionMode ?: settings.lastUsedFileSelectionMode
        }
    }

    fun setResolvedConfig(config: AddTorrentConfig) {
        _resolvedConfig.value = config
        initFormState(config)
    }

    fun resolveTorrentUri(
        uri: Uri,
        createSubfolderByName: Boolean? = null,
        destinationSubdirectory: String? = null,
        feedId: String? = null,
        feedItemId: String? = null,
        onFailure: () -> Unit = {}
    ) {
        viewModelScope.launch {
            _isResolvingTorrent.value = true
            try {
                val resolvedTorrent = torrentUriResolver.resolve(uri)
                val config = AddTorrentConfig(
                    id = resolvedTorrent.id,
                    uri = resolvedTorrent.uri.toString(),
                    type = resolvedTorrent.type,
                    name = resolvedTorrent.name,
                    createSubfolderByName = createSubfolderByName,
                    destinationSubdirectory = destinationSubdirectory,
                    feedId = feedId,
                    feedItemId = feedItemId
                )
                setResolvedConfig(config)
            } catch (e: Exception) {
                Timber.e(e, "Failed to resolve torrent URI=%s", uri)
                onFailure()
            } finally {
                _isResolvingTorrent.value = false
            }
        }
    }

    fun confirmAddTorrent() {
        val config = _resolvedConfig.value ?: return
        val updatedConfig = config.copy(
            name = _nameState.value.takeIf { it.isNotBlank() },
            destinationSubdirectory = _selectedSubDirState.value,
            createSubfolderByName = _createSubfolderByNameState.value,
            notifyOnCompletion = _notifyOnCompletionState.value,
            fileSelectionMode = _fileSelectionModeState.value
        )
        Timber.i("Confirming add torrent id=%s, name=%s", updatedConfig.id, updatedConfig.name)

        if (config.feedId == null) {
            viewModelScope.launch {
                appSettingsRepository.updateLastUsedAddTorrentOptions(
                    lastUsedSubDir = _selectedSubDirState.value,
                    lastUsedCreateSubfolder = _createSubfolderByNameState.value,
                    lastUsedNotifyOnCompletion = _notifyOnCompletionState.value,
                    lastUsedFileSelectionMode = _fileSelectionModeState.value
                )
            }
        }

        serviceLauncher.addTask(updatedConfig)

        if (config.feedId != null && config.feedItemId != null) {
            rssRepository.markItemAsDownloaded(config.feedId, config.feedItemId)
        }

        clearResolvedConfig()
    }

    fun dismissAddTorrent() {
        _resolvedConfig.value?.cleanupTemporaryTorrentFile()
        clearResolvedConfig()
    }

    fun clearResolvedConfig() {
        _resolvedConfig.value = null
    }
}

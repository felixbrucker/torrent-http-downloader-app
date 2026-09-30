package com.felixbrucker.torrenthttpdownloader.feature.addtorrent

import android.content.SharedPreferences
import android.net.Uri
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import javax.inject.Named
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.felixbrucker.torrenthttpdownloader.core.data.RssRepository
import com.felixbrucker.torrenthttpdownloader.core.model.FileSelectionMode
import com.felixbrucker.torrenthttpdownloader.core.network.TorrentUriResolver
import com.felixbrucker.torrenthttpdownloader.feature.downloads.DownloadServiceLauncher
import timber.log.Timber

@HiltViewModel
class AddTorrentViewModel @Inject constructor(
    @param:Named("settings") private val sharedPreferences: SharedPreferences,
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
        val defaultSubDir = sharedPreferences.getString("default_sub_dir", null)
        val defaultCreateSubfolder = sharedPreferences.getBoolean("default_create_subfolder", true)
        val defaultNotifyOnCompletion = sharedPreferences.getBoolean("default_notify_on_completion", false)
        val defaultFileSelectionModeName = sharedPreferences.getString("default_file_selection_mode", FileSelectionMode.ALL.name) ?: FileSelectionMode.ALL.name
        val defaultFileSelectionMode = try {
            FileSelectionMode.valueOf(defaultFileSelectionModeName)
        } catch (e: Exception) {
            FileSelectionMode.ALL
        }

        _nameState.value = config.name ?: ""
        _selectedSubDirState.value = config.destinationSubdirectory ?: defaultSubDir
        _createSubfolderByNameState.value = config.createSubfolderByName ?: defaultCreateSubfolder
        _notifyOnCompletionState.value = config.notifyOnCompletion ?: defaultNotifyOnCompletion
        _fileSelectionModeState.value = config.fileSelectionMode ?: defaultFileSelectionMode
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
            sharedPreferences.edit {
                putString("default_sub_dir", _selectedSubDirState.value)
                putBoolean("default_create_subfolder", _createSubfolderByNameState.value)
                putBoolean("default_notify_on_completion", _notifyOnCompletionState.value)
                putString("default_file_selection_mode", _fileSelectionModeState.value.name)
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

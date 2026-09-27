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

data class DefaultAddTorrentPreferences(
    val defaultSubDir: String?,
    val defaultCreateSubfolder: Boolean,
    val defaultNotifyOnCompletion: Boolean,
    val defaultFileSelectionMode: FileSelectionMode
)

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

    fun getDefaultPreferences(): DefaultAddTorrentPreferences {
        val selectionModeName = sharedPreferences.getString("default_file_selection_mode", FileSelectionMode.ALL.name) ?: FileSelectionMode.ALL.name
        val selectionMode = try {
            FileSelectionMode.valueOf(selectionModeName)
        } catch (e: Exception) {
            FileSelectionMode.ALL
        }
        return DefaultAddTorrentPreferences(
            defaultSubDir = sharedPreferences.getString("default_sub_dir", null),
            defaultCreateSubfolder = sharedPreferences.getBoolean("default_create_subfolder", true),
            defaultNotifyOnCompletion = sharedPreferences.getBoolean("default_notify_on_completion", false),
            defaultFileSelectionMode = selectionMode
        )
    }

    fun saveDefaultPreferences(
        subDir: String?,
        createSubfolder: Boolean,
        notifyOnCompletion: Boolean,
        fileSelectionMode: FileSelectionMode
    ) {
        sharedPreferences.edit {
            putString("default_sub_dir", subDir)
            putBoolean("default_create_subfolder", createSubfolder)
            putBoolean("default_notify_on_completion", notifyOnCompletion)
            putString("default_file_selection_mode", fileSelectionMode.name)
        }
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
                _resolvedConfig.value = AddTorrentConfig(
                    id = resolvedTorrent.id,
                    uri = resolvedTorrent.uri.toString(),
                    type = resolvedTorrent.type,
                    name = resolvedTorrent.name,
                    createSubfolderByName = createSubfolderByName,
                    destinationSubdirectory = destinationSubdirectory,
                    feedId = feedId,
                    feedItemId = feedItemId
                )
            } catch (e: Exception) {
                e.printStackTrace()
                onFailure()
            } finally {
                _isResolvingTorrent.value = false
            }
        }
    }

    fun confirmAddTorrent(config: AddTorrentConfig) {
        if (config.feedId == null) {
            saveDefaultPreferences(
                subDir = config.destinationSubdirectory,
                createSubfolder = config.createSubfolderByName ?: true,
                notifyOnCompletion = config.notifyOnCompletion ?: false,
                fileSelectionMode = config.fileSelectionMode ?: FileSelectionMode.ALL
            )
        }
        serviceLauncher.addTask(config)
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

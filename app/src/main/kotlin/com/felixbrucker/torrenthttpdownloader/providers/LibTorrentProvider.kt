package com.felixbrucker.torrenthttpdownloader.providers

import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkCapabilities.TRANSPORT_VPN
import com.felixbrucker.torrenthttpdownloader.container.Container
import com.felixbrucker.torrenthttpdownloader.container.ServiceBuilder
import com.felixbrucker.torrenthttpdownloader.storage.PathFactory
import org.libtorrent4j.AlertListener
import org.libtorrent4j.FileStorage
import org.libtorrent4j.SessionHandle
import org.libtorrent4j.SessionManager
import org.libtorrent4j.SessionParams
import org.libtorrent4j.SettingsPack
import org.libtorrent4j.Sha1Hash
import org.libtorrent4j.TorrentFlags
import org.libtorrent4j.TorrentHandle
import org.libtorrent4j.TorrentInfo
import org.libtorrent4j.Vectors
import org.libtorrent4j.alerts.Alert
import org.libtorrent4j.alerts.AlertType
import org.libtorrent4j.alerts.SaveResumeDataAlert
import org.libtorrent4j.alerts.TorrentAlert
import org.libtorrent4j.swig.bdecode_node
import org.libtorrent4j.swig.error_code
import org.libtorrent4j.swig.libtorrent
import org.libtorrent4j.swig.settings_pack
import org.libtorrent4j.swig.torrent_flags_t
import org.libtorrent4j.swig.torrent_handle
import java.io.File
import java.util.Random


class LibTorrentProvider(
    private val sharedPreferences: SharedPreferences,
    private val connectivityManager: ConnectivityManager,
) : TorrentProvider {
    override val name: String = NAME
    override val requiresLocalDownloads: Boolean = false
    override val requiresFileSelection: Boolean = false
    override val supportsPauseResume: Boolean = true

    companion object: ServiceBuilder {
        override val NAME: String = "libtorrent"

        override fun build(): LibTorrentProvider {
            val sharedPreferences = Container.getService<SharedPreferences>("SharedPreferences")
            val connectivityManager = Container.getService<ConnectivityManager>("ConnectivityManager")

            return LibTorrentProvider(sharedPreferences, connectivityManager)
        }
    }

    private val sessionManager = SessionManager()
    private val defaultSessionSettings = SessionSettings()

    private val requireVpnConnection = sharedPreferences.getBoolean("libtorrent_require_vpn_connection", false)
    private val networkCallback: ConnectivityManager.NetworkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network : Network, networkCapabilities : NetworkCapabilities) {
            if (sessionManager.isPaused && isAllowedToRun(networkCapabilities)) {
                sessionManager.resume()
            } else if (!sessionManager.isPaused && !isAllowedToRun(networkCapabilities)) {
                sessionManager.pause()
            }
        }
    }
    private val lastResumeDataSavedAt = mutableMapOf<String, Long>()
    private val minimumTimeBetweenResumeDataSavesInMs = 10_000L
    private val libTorrentListener = object : AlertListener {
        override fun types(): IntArray {
            return intArrayOf(
                AlertType.SAVE_RESUME_DATA.swig(),
                AlertType.PIECE_FINISHED.swig(),
                AlertType.METADATA_RECEIVED.swig(),
                AlertType.TORRENT_PAUSED.swig(),
            )
        }

        override fun alert(alert: Alert<*>?) {
            if (alert !is TorrentAlert<*>) return

            val type: AlertType = alert.type()
            val handle = alert.handle()
            val id = handle.infoHash().toHex()
            when (type) {
                AlertType.SAVE_RESUME_DATA -> {
                    val data = libtorrent
                        .write_resume_data((alert as SaveResumeDataAlert).params().swig())
                        .bencode()
                    saveResumeData(id, data.toByteArray())
                }
                AlertType.PIECE_FINISHED -> {
                    triggerResumeDataSaveIfNecessary(id, handle)
                }
                AlertType.METADATA_RECEIVED -> {
                    triggerResumeDataSaveIfNecessary(id, handle, forceSave = true)
                }
                AlertType.TORRENT_PAUSED -> {
                    triggerResumeDataSaveIfNecessary(id, handle, forceSave = true)
                }
                else -> return
            }
        }
    }

    init {
        if (!PathFactory.getResumeDataDirectory().exists()) {
            PathFactory.getResumeDataDirectory().mkdirs()
        }

        if (defaultSessionSettings.useRandomPort) {
            val range = SessionSettings.randomRangePort
            defaultSessionSettings.portRangeFirst = range.first
            defaultSessionSettings.portRangeSecond = range.second
        }
        defaultSessionSettings.activeDownloads = sharedPreferences.getInt("libtorrent_parallel_downloads", 2)

        val params = loadSessionParams()
        params.settings = settingsToSettingsPack(defaultSessionSettings)
        sessionManager.addListener(libTorrentListener)
        sessionManager.start(params)
        connectivityManager.registerDefaultNetworkCallback(networkCallback)
    }

    private fun isAllowedToRun(capabilities: NetworkCapabilities): Boolean {
        if (!requireVpnConnection) {
            return true
        }
        return capabilities.hasTransport(TRANSPORT_VPN)
    }

    override fun stop() {
        saveSessionParams()
        sessionManager.stop()
        sessionManager.removeListener(libTorrentListener)
        connectivityManager.unregisterNetworkCallback(networkCallback)
    }

    override fun restoreTorrent(id: String) {
        val resumeData = readResumeData(id) ?: return
        val ec = error_code()
        val buffer = Vectors.bytes2byte_vector(resumeData)

        val n = bdecode_node()
        val ret = bdecode_node.bdecode(buffer, n, ec)
        require(ret == 0) { "Can't decode data: " + ec.message() }
        ec.clear()

        val p = libtorrent.read_resume_data(n, ec)
        require(ec.value() == 0) { "Unable to read the resume data: " + ec.message() }

        // Disable force saving resume data on add
        p.flags = p.getFlags().and_(TorrentFlags.NEED_SAVE_RESUME.inv())

        sessionManager.swig().async_add_torrent(p)
    }

    override suspend fun addTorrent(torrentFileBytes: ByteArray, name: String): String {
        val info = TorrentInfo(torrentFileBytes)
        val torrentId = info.infoHash().toHex()

        sessionManager.download(
            info,
            PathFactory.getScopedTemporaryDirectory(name),
            null,
            null,
            null,
            makeDefaultTorrentFlags(),
        )
        setPerTorrentSettings(torrentId)

        return torrentId
    }

    override suspend fun addMagnet(magnetUri: String, name: String): String {
        val errorCode = error_code()
        val addTorrentParams = libtorrent.parse_magnet_uri(magnetUri, errorCode)
        val infoHash = addTorrentParams.getInfo_hashes()._best
        val torrentId = infoHash.to_hex()

        sessionManager.download(
            magnetUri,
            PathFactory.getScopedTemporaryDirectory(name),
            makeDefaultTorrentFlags(),
        )
        setPerTorrentSettings(torrentId)

        return torrentId
    }

    private fun setPerTorrentSettings(torrentId: String) {
        val torrentHandle = sessionManager.find(Sha1Hash.parseHex(torrentId)) ?: throw Exception("Torrent not found")
        torrentHandle.swig().set_max_connections(defaultSessionSettings.connectionsLimitPerTorrent)
        torrentHandle.swig().set_max_uploads(defaultSessionSettings.uploadsLimitPerTorrent)
    }

    override suspend fun getTorrentInfo(id: String): ProviderTorrentInfo {
        val torrentHandle = sessionManager.find(Sha1Hash.parseHex(id)) ?: throw Exception("Torrent not found")
        val torrentStatus = torrentHandle.status()
        val totalBytes = torrentStatus.totalWanted()
        val downloadedBytes = torrentStatus.totalWantedDone()
        val torrentFileInfo = torrentHandle.torrentFile()
        val files = if (torrentFileInfo == null) {
            listOf()
        } else {
            getFileList(torrentFileInfo.files())
        }
        var torrentState = torrentStatus.state().name.lowercase()
        val isPaused = torrentStatus.flags().and_(TorrentFlags.PAUSED).non_zero()
        if (isPaused || sessionManager.isPaused) {
            torrentState = "paused"
        }
        val totalPeers = torrentStatus.numComplete() + torrentStatus.numIncomplete()
        val fileProgress = torrentHandle.fileProgress(torrent_handle.piece_granularity)
        val overallProgress = torrentStatus.progress() * 100
        val hasAllFileProgress = fileProgress.size == files.size

        return ProviderTorrentInfo(
            id = id,
            name = torrentHandle.name,
            state = mapTorrentStateToProviderTorrentState(torrentState),
            status = torrentState,
            progress = overallProgress,
            totalSizeInBytes = totalBytes,
            downloadedBytes = downloadedBytes,
            downloadSpeed = torrentStatus.downloadRate().toLong(),
            uploadSpeed = torrentStatus.uploadRate().toLong(),
            seeders = torrentStatus.numSeeds(),
            leechers = torrentStatus.numPeers() - torrentStatus.numSeeds(),
            peers = torrentStatus.numPeers(),
            totalPeers = if (totalPeers > 0) totalPeers else torrentStatus.listPeers(),
            links = files.map { it.first },
            files = files.mapIndexed { index, (path, size) ->
                if (hasAllFileProgress) {
                    val downloadedBytes = fileProgress[index]
                    val progress: Float = if (size == 0L) {
                        100F
                    } else {
                        downloadedBytes / size.toFloat() * 100
                    }

                    ProviderTorrentFile(
                        path = path,
                        size = size,
                        progress = progress,
                        downloadedBytes = downloadedBytes,
                    )
                } else {
                    ProviderTorrentFile(
                        path = path,
                        size = size,
                        progress = null,
                        downloadedBytes = null,
                    )
                }
            },
        )
    }

    override suspend fun selectFiles(id: String, files: String): Boolean {
        // NOOP
        return true
    }

    override suspend fun deleteTorrent(id: String, deleteFiles: Boolean): Boolean {
        val torrentHandle = sessionManager.find(Sha1Hash.parseHex(id)) ?: return true

        sessionManager.remove(
            torrentHandle,
            if (deleteFiles) SessionHandle.DELETE_FILES else SessionHandle.DELETE_PARTFILE
        )

        return true
    }

    override suspend fun unrestrictLink(id: String, link: String): UnrestrictedLink {
        val filePath = link
        val torrentHandle = sessionManager.find(Sha1Hash.parseHex(id)) ?: throw Exception("Torrent not found")
        val torrentFileInfo = torrentHandle.torrentFile() ?: throw Exception("Torrent file not found")
        val filesStorage = torrentFileInfo.files()

        return UnrestrictedLink(
            filename = filePath.substringAfterLast("/"),
            downloadUrl = "",
            size = getFileSize(filesStorage, filePath)
        )
    }

    override suspend fun pause(id: String) {
        val torrentHandle = sessionManager.find(Sha1Hash.parseHex(id)) ?: throw Exception("Torrent not found")
        torrentHandle.unsetFlags(TorrentFlags.AUTO_MANAGED)
        torrentHandle.pause()
    }

    override suspend fun resume(id: String) {
        val torrentHandle = sessionManager.find(Sha1Hash.parseHex(id)) ?: throw Exception("Torrent not found")
        torrentHandle.flags = TorrentFlags.AUTO_MANAGED
        torrentHandle.resume()
    }

    private fun getFileList(storage: FileStorage): List<Pair<String, Long>> {
        // relative paths in the torrent
        val files: MutableList<Pair<String, Long>> = mutableListOf()
        for (i in 0..<storage.numFiles()) {
            files.add(storage.filePath(i) to storage.fileSize(i))
        }

        return files
    }

    private fun getFileSize(storage: FileStorage, filePath: String): Long {
        for (i in 0..<storage.numFiles()) {
            if (storage.filePath(i) == filePath) {
                return storage.fileSize(i)
            }
        }

        return 0
    }

    private fun mapTorrentStateToProviderTorrentState(status: String): ProviderTorrentState {
        return when (status) {
            "checking_files" -> ProviderTorrentState.PROCESSING
            "downloading_metadata" -> ProviderTorrentState.CONVERTING_MAGNET
            "paused" -> ProviderTorrentState.PAUSED
            "downloading" -> ProviderTorrentState.DOWNLOADING
            "seeding", "finished" -> ProviderTorrentState.COMPLETED
            "error" -> ProviderTorrentState.ERROR
            else -> ProviderTorrentState.UNKNOWN
        }
    }

    private fun loadSessionParams(): SessionParams {
        val sessionFile = File(PathFactory.getResumeDataDirectory(), "session")
        if (!sessionFile.exists()) {
            return SessionParams()
        }

        return SessionParams(sessionFile.readBytes())
    }

    private fun saveSessionParams() {
        val params = sessionManager.saveState() ?: return
        val sessionFile = File(PathFactory.getResumeDataDirectory(), "session")
        sessionFile.writeBytes(params)
    }

    private fun readResumeData(id: String): ByteArray? {
        val path = PathFactory.getResumeDataPath(id)
        if (!path.exists()) {
            return null
        }

        return path.readBytes()
    }

    private fun saveResumeData(id: String, data: ByteArray) {
        val path = PathFactory.getResumeDataPath(id)
        path.writeBytes(data)
    }

    private fun triggerResumeDataSaveIfNecessary(id: String, handle: TorrentHandle, forceSave: Boolean = false) {
        val now = System.currentTimeMillis()
        val lastSaveAt = lastResumeDataSavedAt[id]
        if (!forceSave && lastSaveAt !== null && (now - lastSaveAt) < minimumTimeBetweenResumeDataSavesInMs) {
            return
        }
        if (handle.isValid && handle.needSaveResumeData()) {
            handle.saveResumeData(TorrentHandle.SAVE_INFO_DICT)
            lastResumeDataSavedAt[id] = now
        }
    }

    private fun makeDefaultTorrentFlags(): torrent_flags_t {
        return torrent_flags_t()
            .or_(TorrentFlags.NEED_SAVE_RESUME)
            .or_(TorrentFlags.AUTO_MANAGED)
    }

    private fun settingsToSettingsPack(settings: SessionSettings): SettingsPack {
        val sp = SettingsPack()
        sp.activeDownloads(settings.activeDownloads)
        sp.activeSeeds(settings.activeSeeds)
        sp.activeLimit(settings.activeLimit)
        sp.maxPeerlistSize(settings.maxPeerListSize)
        sp.tickInterval(settings.tickInterval)
        sp.inactivityTimeout(settings.inactivityTimeout)
        sp.connectionsLimit(settings.connectionsLimit)
        sp.listenInterfaces(getIface(settings.inetAddress, settings.portRangeFirst))
        sp.setInteger(
            settings_pack.int_types.max_retry_port_bind.swigValue(),
            settings.portRangeSecond - settings.portRangeFirst
        )
        sp.isEnableDht = settings.dhtEnabled
        sp.setBoolean(settings_pack.bool_types.enable_lsd.swigValue(), settings.lsdEnabled)
        sp.setBoolean(settings_pack.bool_types.enable_incoming_utp.swigValue(), settings.utpEnabled)
        sp.setBoolean(settings_pack.bool_types.enable_outgoing_utp.swigValue(), settings.utpEnabled)
        sp.setBoolean(settings_pack.bool_types.enable_upnp.swigValue(), settings.upnpEnabled)
        sp.setBoolean(settings_pack.bool_types.enable_natpmp.swigValue(), settings.natPmpEnabled)
        val encryptModeOutcoming: Int = convertEncryptMode(settings.encryptModeOutcoming)
        val encryptModeIncoming: Int = convertEncryptMode(settings.encryptModeIncoming)
        val encLevel: Int =
            getAllowedEncryptLevel(settings.encryptModeOutcoming, settings.encryptModeIncoming)
        sp.setInteger(settings_pack.int_types.in_enc_policy.swigValue(), encryptModeIncoming)
        sp.setInteger(settings_pack.int_types.out_enc_policy.swigValue(), encryptModeOutcoming)
        sp.setInteger(settings_pack.int_types.allowed_enc_level.swigValue(), encLevel)
        sp.uploadRateLimit(settings.uploadRateLimit)
        sp.downloadRateLimit(settings.downloadRateLimit)
        sp.anonymousMode(settings.anonymousMode)
        sp.seedingOutgoingConnections(settings.seedingOutgoingConnections)
        sp.setBoolean(
            settings_pack.bool_types.validate_https_trackers.swigValue(),
            settings.validateHttpsTrackers
        )

        return sp
    }

    private fun getIface(inetAddress: String, portRangeFirst: Int): String {
        var iface: String?
        if (inetAddress == SessionSettings.DEFAULT_INETADDRESS) {
            iface = $$"0.0.0.0:%1$d,[::]:%1$d"
        } else {
            /* IPv6 test */
            if (inetAddress.contains(":")) iface = "[$inetAddress]"
            else iface = inetAddress

            iface = $$"$$iface:%1$d"
        }

        return String.format(iface, portRangeFirst)
    }

    private fun convertEncryptMode(mode: SessionSettings.EncryptMode): Int {
        return when (mode) {
            SessionSettings.EncryptMode.ENABLED -> settings_pack.enc_policy.pe_enabled.swigValue()
            SessionSettings.EncryptMode.FORCED -> settings_pack.enc_policy.pe_forced.swigValue()
        }
    }

    private fun getAllowedEncryptLevel(
        modeOutcoming: SessionSettings.EncryptMode,
        modeIncoming: SessionSettings.EncryptMode
    ): Int {
        if (modeOutcoming === SessionSettings.EncryptMode.FORCED
            || modeIncoming === SessionSettings.EncryptMode.FORCED
        ) {
            return settings_pack.enc_level.pe_rc4.swigValue()
        } else {
            return settings_pack.enc_level.pe_both.swigValue()
        }
    }
}

class SessionSettings {
    var activeDownloads: Int = DEFAULT_ACTIVE_DOWNLOADS
    var activeSeeds: Int = DEFAULT_ACTIVE_SEEDS
    var maxPeerListSize: Int = DEFAULT_MAX_PEER_LIST_SIZE
    var tickInterval: Int = DEFAULT_TICK_INTERVAL
    var inactivityTimeout: Int = DEFAULT_INACTIVITY_TIMEOUT
    var connectionsLimit: Int = DEFAULT_CONNECTIONS_LIMIT
    var connectionsLimitPerTorrent: Int = DEFAULT_CONNECTIONS_LIMIT_PER_TORRENT
    var uploadsLimitPerTorrent: Int = DEFAULT_UPLOADS_LIMIT_PER_TORRENT
    var activeLimit: Int = DEFAULT_ACTIVE_LIMIT
    var portRangeFirst: Int = DEFAULT_PORT_RANGE_FIRST
    var portRangeSecond: Int = DEFAULT_PORT_RANGE_SECOND
    var downloadRateLimit: Int = DEFAULT_DOWNLOAD_RATE_LIMIT
    var uploadRateLimit: Int = DEFAULT_UPLOAD_RATE_LIMIT
    var dhtEnabled: Boolean = DEFAULT_DHT_ENABLED
    var lsdEnabled: Boolean = DEFAULT_LSD_ENABLED
    var utpEnabled: Boolean = DEFAULT_UTP_ENABLED
    var upnpEnabled: Boolean = DEFAULT_UPNP_ENABLED
    var natPmpEnabled: Boolean = DEFAULT_NATPMP_ENABLED
    var encryptModeOutcoming: EncryptMode = DEFAULT_ENCRYPT_MODE
    var encryptModeIncoming: EncryptMode = DEFAULT_ENCRYPT_MODE
    var inetAddress: String = DEFAULT_INETADDRESS
    var anonymousMode: Boolean = DEFAULT_ANONYMOUS_MODE
    var seedingOutgoingConnections: Boolean = DEFAULT_SEEDING_OUTGOING_CONNECTIONS
    var useRandomPort: Boolean = DEFAULT_USE_RANDOM_PORT
    var validateHttpsTrackers: Boolean = DEFAULT_VALIDATE_HTTPS_TRACKERS

    enum class EncryptMode {
        ENABLED,

        FORCED,
    }

    companion object {
        const val DEFAULT_ACTIVE_DOWNLOADS: Int = 4
        const val DEFAULT_ACTIVE_SEEDS: Int = 4
        const val DEFAULT_MAX_PEER_LIST_SIZE: Int = 200
        const val DEFAULT_TICK_INTERVAL: Int = 1000
        const val DEFAULT_INACTIVITY_TIMEOUT: Int = 60
        const val DEFAULT_CONNECTIONS_LIMIT: Int = 200
        const val DEFAULT_CONNECTIONS_LIMIT_PER_TORRENT: Int = 40
        const val DEFAULT_UPLOADS_LIMIT_PER_TORRENT: Int = 4
        const val DEFAULT_ACTIVE_LIMIT: Int = 6
        const val DEFAULT_DOWNLOAD_RATE_LIMIT: Int = 0
        const val DEFAULT_UPLOAD_RATE_LIMIT: Int = 0
        const val DEFAULT_DHT_ENABLED: Boolean = true
        const val DEFAULT_LSD_ENABLED: Boolean = true
        const val DEFAULT_UTP_ENABLED: Boolean = true
        const val DEFAULT_UPNP_ENABLED: Boolean = true
        const val DEFAULT_NATPMP_ENABLED: Boolean = true
        val DEFAULT_ENCRYPT_MODE: EncryptMode = EncryptMode.ENABLED
        const val DEFAULT_INETADDRESS: String = "0.0.0.0"
        const val DEFAULT_PORT_RANGE_FIRST: Int = 37000
        const val DEFAULT_PORT_RANGE_SECOND: Int = 57010
        const val DEFAULT_ANONYMOUS_MODE: Boolean = false
        const val DEFAULT_SEEDING_OUTGOING_CONNECTIONS: Boolean = false
        const val DEFAULT_USE_RANDOM_PORT: Boolean = true
        const val DEFAULT_VALIDATE_HTTPS_TRACKERS: Boolean = true

        val randomRangePort: Pair<Int, Int>
            /*
            * Get the first port in range [37000, 57000] and the second `first` + 10
            */
            get() {
                val port = DEFAULT_PORT_RANGE_FIRST + Random().nextInt(
                    DEFAULT_PORT_RANGE_SECOND - 10 - DEFAULT_PORT_RANGE_FIRST
                )

                return Pair(port, port + 10)
            }
    }
}
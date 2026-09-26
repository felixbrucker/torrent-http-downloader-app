package com.felixbrucker.torrenthttpdownloader

import android.app.Application
import android.content.Context
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.felixbrucker.torrenthttpdownloader.data.logging.AppLogTree
import com.felixbrucker.torrenthttpdownloader.data.logging.LogRepository
import com.felixbrucker.torrenthttpdownloader.download.DownloadTracker
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
import java.lang.ref.WeakReference
import javax.inject.Inject

@HiltAndroidApp
class TorrentHttpDownloaderApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var logRepository: LogRepository

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    companion object {
        private var contextRef: WeakReference<Context> = WeakReference(null)

        fun getContext(): Context {
            return contextRef.get() ?: throw IllegalStateException("Application context is null")
        }
    }

    override fun onCreate() {
        super.onCreate()
        contextRef = WeakReference(applicationContext)
        logRepository.init(this)
        Timber.plant(AppLogTree(logRepository))
        DownloadTracker.init()
    }
}

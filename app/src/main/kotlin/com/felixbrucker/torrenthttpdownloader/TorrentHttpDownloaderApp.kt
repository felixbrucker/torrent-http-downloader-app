package com.felixbrucker.torrenthttpdownloader

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.felixbrucker.torrenthttpdownloader.core.logging.AppLogTree
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class TorrentHttpDownloaderApp : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var appLogTree: AppLogTree

    override fun onCreate() {
        super.onCreate()
        Timber.plant(appLogTree)
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}

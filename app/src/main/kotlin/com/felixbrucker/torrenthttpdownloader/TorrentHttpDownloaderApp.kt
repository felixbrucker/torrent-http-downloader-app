package com.felixbrucker.torrenthttpdownloader

import android.app.Application
import android.content.Context
import com.felixbrucker.torrenthttpdownloader.data.logging.AppLogTree
import com.felixbrucker.torrenthttpdownloader.data.logging.LogRepository
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
import java.lang.ref.WeakReference

@HiltAndroidApp
class TorrentHttpDownloaderApp : Application() {
    companion object {
        private var contextRef: WeakReference<Context> = WeakReference(null)

        fun getContext(): Context {
            return contextRef.get() ?: throw IllegalStateException("Application context is null")
        }
    }

    override fun onCreate() {
        super.onCreate()
        contextRef = WeakReference(applicationContext)
        LogRepository.init(this)
        Timber.plant(AppLogTree())
        DownloadTracker.init()
    }
}
package com.felixbrucker.torrenthttpdownloader

import android.app.Application
import android.content.Context
import timber.log.Timber
import java.lang.ref.WeakReference

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
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        DownloadTracker.init()
    }
}
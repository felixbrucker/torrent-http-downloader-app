package com.felixbrucker.torrenthttpdownloader

import android.app.Application

class TorrentHttpDownloaderApp : Application() {

    override fun onCreate() {
        super.onCreate()
        DownloadTracker.init(this)
    }
}
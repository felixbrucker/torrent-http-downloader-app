package com.felixbrucker.torrenthttpdownloader.feature.rss

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import com.felixbrucker.torrenthttpdownloader.worker.RssSyncWorker

@Singleton
class RssSyncLauncher @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    fun ensureRssSyncIsScheduled() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val rssSyncRequest = PeriodicWorkRequestBuilder<RssSyncWorker>(3, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "RssSyncRequest",
            ExistingPeriodicWorkPolicy.UPDATE,
            rssSyncRequest,
        )
    }

    fun runRssSyncOnce(feedId: String? = null) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val rssSyncRequest = OneTimeWorkRequestBuilder<RssSyncWorker>()
            .setConstraints(constraints)
            .setInputData(workDataOf("feedId" to feedId))
            .build()
        WorkManager.getInstance(context).enqueue(rssSyncRequest)
    }
}

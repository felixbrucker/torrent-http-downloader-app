package com.felixbrucker.torrenthttpdownloader.feature.rss

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.felixbrucker.torrenthttpdownloader.core.datastore.AppSettingsRepository
import com.felixbrucker.torrenthttpdownloader.di.ApplicationScope
import com.felixbrucker.torrenthttpdownloader.worker.RssSyncWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@Singleton
class RssSyncLauncher @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val appSettingsRepository: AppSettingsRepository,
    @param:ApplicationScope private val scope: CoroutineScope
) {
    init {
        appSettingsRepository.settingsFlow
            .onEach { settings ->
                updateRssSyncSchedule(
                    enabled = settings.rssSyncEnabled,
                    intervalHours = settings.rssSyncIntervalHours.toLong()
                )
            }
            .launchIn(scope)
    }

    fun ensureRssSyncIsScheduled() {
        // Handled automatically via settingsFlow subscription in init
    }

    fun updateRssSyncSchedule(enabled: Boolean, intervalHours: Long) {
        if (!enabled) {
            WorkManager.getInstance(context).cancelUniqueWork("RssSyncRequest")
            return
        }
        val safeInterval = maxOf(1L, intervalHours)
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val rssSyncRequest = PeriodicWorkRequestBuilder<RssSyncWorker>(safeInterval, TimeUnit.HOURS)
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

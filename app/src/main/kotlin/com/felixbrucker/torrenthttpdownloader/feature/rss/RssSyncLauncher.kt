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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import timber.log.Timber

@Singleton
class RssSyncLauncher @Inject constructor(
    @param:ApplicationContext private val context: Context,
    appSettingsRepository: AppSettingsRepository,
    @param:ApplicationScope private val scope: CoroutineScope
) {
    init {
        appSettingsRepository.settingsFlow
            .map { it.rssSyncEnabled to it.rssSyncIntervalHours }
            .distinctUntilChanged()
            .onEach { (enabled, intervalHours) ->
                updateRssSyncSchedule(
                    enabled = enabled,
                    intervalHours = intervalHours.toLong()
                )
            }
            .launchIn(scope)
    }

    fun updateRssSyncSchedule(enabled: Boolean, intervalHours: Long) {
        if (!enabled) {
            Timber.d("RSS sync disabled, canceling unique periodic work 'RssSyncRequest'")
            WorkManager.getInstance(context).cancelUniqueWork("RssSyncRequest")
            return
        }
        val safeInterval = maxOf(1L, intervalHours)
        Timber.d("RSS sync enabled, scheduling 'RssSyncRequest' with intervalHours=%d", safeInterval)
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

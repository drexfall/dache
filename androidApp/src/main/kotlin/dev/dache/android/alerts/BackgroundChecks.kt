package dev.dache.android.alerts

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.dache.android.DacheApplication
import dev.dache.core.alerts.AlertSettings
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/** One background check: refresh, then notify about anything new. */
class BackgroundCheckWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as DacheApplication).container
        if (!container.alertStore.alertSettings
                .first()
                .enabled
        ) {
            return Result.success()
        }
        val alerts = container.backgroundCheck.run()
        val hideDetails =
            container.settingsStore.appLock
                .first()
                .widgetDetailsHidden
        AlertNotifier(applicationContext).show(alerts, hideDetails)
        return Result.success()
    }
}

object BackgroundChecks {
    private const val WORK_NAME = "dache-background-check"

    /** Runs checks every [AlertSettings.intervalMinutes] while there is a network, or stops them. */
    fun schedule(
        context: Context,
        settings: AlertSettings,
    ) {
        val work = WorkManager.getInstance(context)
        if (!settings.enabled) {
            work.cancelUniqueWork(WORK_NAME)
            return
        }
        val interval = settings.intervalMinutes.coerceAtLeast(AlertSettings.MIN_INTERVAL_MINUTES).toLong()
        val request =
            PeriodicWorkRequestBuilder<BackgroundCheckWorker>(interval, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
        work.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }
}

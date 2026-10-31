package dev.dache.android.alerts

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dev.dache.android.MainActivity
import dev.dache.android.R
import dev.dache.core.alerts.Alert
import dev.dache.core.alerts.AlertKind

/**
 * Posts one notification per alert, grouped. With [show]'s hideDetails (the app lock's "hide names" option),
 * notifications say what kind of thing broke but not which, since they show on the lock screen.
 */
class AlertNotifier(
    private val context: Context,
) {
    fun show(
        alerts: List<Alert>,
        hideDetails: Boolean,
    ) {
        if (alerts.isEmpty() || !canNotify()) return
        createChannel(context)
        val manager = NotificationManagerCompat.from(context)
        alerts.forEach { alert ->
            val notification =
                builder()
                    .setContentTitle(if (hideDetails) genericTitle(alert.kind) else alert.title)
                    .apply { if (!hideDetails) setContentText(alert.body) }
                    .setGroup(GROUP)
                    .build()
            notify(manager, alert.key.hashCode(), notification)
        }
        if (alerts.size > 1) {
            val summary =
                builder()
                    .setContentTitle(
                        context.resources.getQuantityString(R.plurals.alerts_summary, alerts.size, alerts.size),
                    ).setGroup(GROUP)
                    .setGroupSummary(true)
                    .build()
            notify(manager, SUMMARY_ID, summary)
        }
    }

    private fun builder(): NotificationCompat.Builder =
        NotificationCompat
            .Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_dache)
            .setContentIntent(openApp())
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setCategory(NotificationCompat.CATEGORY_STATUS)

    private fun genericTitle(kind: AlertKind): String =
        context.getString(
            when (kind) {
                AlertKind.DEPLOY_FAILED -> R.string.alert_deploy_failed
                AlertKind.MONITOR_DOWN -> R.string.alert_monitor_down
                AlertKind.CI_FAILED -> R.string.alert_ci_failed
            },
        )

    private fun openApp(): PendingIntent =
        PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP,
            ),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun canNotify(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    // Checked by canNotify(); lint can't see through it.
    @Suppress("MissingPermission")
    private fun notify(
        manager: NotificationManagerCompat,
        id: Int,
        notification: android.app.Notification,
    ) {
        if (canNotify()) manager.notify(id, notification)
    }

    companion object {
        private const val CHANNEL_ID = "alerts"
        private const val GROUP = "dev.dache.alerts"
        private const val SUMMARY_ID = 0

        fun createChannel(context: Context) {
            val channel =
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.alerts_channel),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply { description = context.getString(R.string.alerts_channel_description) }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }
}

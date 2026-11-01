package dev.dache.app.desktop

import dev.dache.app.alerts.NotificationAccess
import dev.dache.core.alerts.Alert
import dev.dache.core.alerts.AlertKind

/** Shows background check alerts on the desktop, and says whether it can. */
interface DesktopNotifier : NotificationAccess {
    /** With [hideDetails] (the app lock's "hide names"), says what kind of thing broke but not which. */
    fun show(
        alerts: List<Alert>,
        hideDetails: Boolean,
    )
}

/** What a notification says when names are hidden. */
fun genericTitle(kind: AlertKind): String =
    when (kind) {
        AlertKind.DEPLOY_FAILED -> "A deploy failed"
        AlertKind.MONITOR_DOWN -> "A monitor is down"
        AlertKind.CI_FAILED -> "A CI run failed"
    }

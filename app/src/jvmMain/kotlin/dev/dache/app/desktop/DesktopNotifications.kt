package dev.dache.app.desktop

import dev.dache.app.alerts.NotificationAccess
import dev.dache.core.alerts.Alert
import dev.dache.core.alerts.AlertKind

/** Desktop notifications through `notify-send`, which every freedesktop.org notification server understands. */
class DesktopNotifications(
    private val runner: CommandRunner = CommandRunner.System,
) : NotificationAccess {
    override fun isGranted(): Boolean = runner.run(listOf("notify-send", "--version")).first == 0

    override suspend fun request(): Boolean = isGranted()

    // There is no per-app switch to send people to; without notify-send the card says so.
    override fun openSettings() = Unit

    /** With [hideDetails] (the app lock's "hide names"), says what kind of thing broke but not which. */
    fun show(
        alerts: List<Alert>,
        hideDetails: Boolean,
    ) {
        alerts.forEach { alert ->
            val title = if (hideDetails) genericTitle(alert.kind) else alert.title
            val body = if (hideDetails) "" else alert.body
            runner.run(listOf("notify-send", "--app-name=Dache", "--icon=dialog-warning", title, body))
        }
    }

    private fun genericTitle(kind: AlertKind): String =
        when (kind) {
            AlertKind.DEPLOY_FAILED -> "A deploy failed"
            AlertKind.MONITOR_DOWN -> "A monitor is down"
            AlertKind.CI_FAILED -> "A CI run failed"
        }
}

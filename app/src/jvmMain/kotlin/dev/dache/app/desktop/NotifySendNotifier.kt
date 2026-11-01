package dev.dache.app.desktop

import dev.dache.core.alerts.Alert

/** Linux notifications through `notify-send`, which every freedesktop.org notification server understands. */
class NotifySendNotifier(
    private val runner: CommandRunner = CommandRunner.System,
) : DesktopNotifier {
    override fun isGranted(): Boolean = runner.run(listOf("notify-send", "--version")).first == 0

    override suspend fun request(): Boolean = isGranted()

    // There is no per-app switch to send people to; without notify-send the card says so.
    override fun openSettings() = Unit

    override fun show(
        alerts: List<Alert>,
        hideDetails: Boolean,
    ) {
        alerts.forEach { alert ->
            val title = if (hideDetails) genericTitle(alert.kind) else alert.title
            val body = if (hideDetails) "" else alert.body
            runner.run(listOf("notify-send", "--app-name=Dache", "--icon=dialog-warning", title, body))
        }
    }
}

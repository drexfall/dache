package dev.dache.app.desktop

import dev.dache.core.alerts.Alert
import java.awt.SystemTray
import java.awt.TrayIcon
import java.awt.image.BufferedImage

/**
 * Windows notifications through the system tray: a brief tray icon whose messages Windows shows as toasts. The
 * icon is removed again once the messages have had time to show.
 */
class TrayNotifier : DesktopNotifier {
    override fun isGranted(): Boolean = SystemTray.isSupported()

    override suspend fun request(): Boolean = isGranted()

    // Windows' own Settings > System > Notifications is where they're turned off; there's no per-app link.
    override fun openSettings() = Unit

    override fun show(
        alerts: List<Alert>,
        hideDetails: Boolean,
    ) {
        if (alerts.isEmpty() || !isGranted()) return
        val tray = SystemTray.getSystemTray()
        val icon =
            TrayIcon(BufferedImage(ICON_SIZE, ICON_SIZE, BufferedImage.TYPE_INT_ARGB), "Dache").apply {
                isImageAutoSize =
                    true
            }
        tray.add(icon)
        try {
            alerts.forEach { alert ->
                val title = if (hideDetails) genericTitle(alert.kind) else alert.title
                icon.displayMessage(title, if (hideDetails) "" else alert.body, TrayIcon.MessageType.WARNING)
            }
            Thread.sleep(SHOW_MILLIS)
        } finally {
            tray.remove(icon)
        }
    }

    private companion object {
        const val ICON_SIZE = 16
        const val SHOW_MILLIS = 6_000L
    }
}

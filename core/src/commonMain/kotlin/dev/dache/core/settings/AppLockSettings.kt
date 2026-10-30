package dev.dache.core.settings

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/** App lock preferences. Only platforms with a lock screen offer them; elsewhere they stay at the defaults. */
data class AppLockSettings(
    val enabled: Boolean = false,
    val timeout: LockTimeout = LockTimeout.IMMEDIATELY,
    /** While the lock is on, widgets show counts but no site or monitor names. */
    val hideWidgetDetails: Boolean = true,
) {
    /** Whether widgets should leave names out right now. */
    val widgetDetailsHidden: Boolean get() = enabled && hideWidgetDetails
}

/** How long the app may sit in the background before it locks again. */
enum class LockTimeout(
    val after: Duration,
) {
    IMMEDIATELY(Duration.ZERO),
    ONE_MINUTE(1.minutes),
    FIVE_MINUTES(5.minutes),
}

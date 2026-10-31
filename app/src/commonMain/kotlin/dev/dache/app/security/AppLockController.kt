package dev.dache.app.security

import dev.dache.core.settings.AppLockSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

enum class LockState { LOADING, LOCKED, UNLOCKED }

/**
 * Decides when the app shows its lock screen. With the lock on, the app starts locked and locks again when it
 * comes back after at least the chosen timeout in the background. Leaving for the system's own unlock screen
 * doesn't count as going to the background.
 */
class AppLockController(
    settings: Flow<AppLockSettings>,
    scope: CoroutineScope,
    private val clock: Clock = Clock.System,
) {
    private val settings: StateFlow<AppLockSettings?> = settings.stateIn(scope, SharingStarted.Eagerly, null)
    private val unlocked = MutableStateFlow(false)
    private var backgroundedAt: Instant? = null
    private var authenticating = false
    private var authenticatedAt: Instant? = null

    val state: StateFlow<LockState> =
        combine(this.settings, unlocked) { current, isUnlocked ->
            when {
                current == null -> LockState.LOADING
                current.enabled && !isUnlocked -> LockState.LOCKED
                else -> LockState.UNLOCKED
            }
        }.stateIn(scope, SharingStarted.Eagerly, LockState.LOADING)

    fun onBackground() {
        if (!authenticating) backgroundedAt = clock.now()
    }

    fun onForeground() {
        val since = backgroundedAt ?: return
        backgroundedAt = null
        val timeout = settings.value?.timeout ?: return
        if (clock.now() - since >= timeout.after) unlocked.value = false
    }

    /** Runs [authenticate], the platform prompt, and unlocks when it passes. Returns whether it did. */
    suspend fun authenticate(authenticate: suspend () -> Boolean): Boolean {
        authenticating = true
        val passed =
            try {
                authenticate()
            } finally {
                authenticating = false
            }
        if (passed) {
            unlocked.value = true
            authenticatedAt = clock.now()
        }
        return passed
    }

    /**
     * Asks for the device lock before something destructive, unless it was passed within [window], so confirming
     * right after unlocking doesn't ask twice.
     */
    suspend fun confirm(
        authenticate: suspend () -> Boolean,
        window: Duration = CONFIRM_WINDOW,
    ): Boolean {
        val recent = authenticatedAt?.let { clock.now() - it <= window } == true
        return recent || authenticate(authenticate)
    }

    private companion object {
        val CONFIRM_WINDOW = 30.seconds
    }

    /** Opens without asking, for a device that no longer has a screen lock to ask for. */
    fun unlockWithoutPrompt() {
        unlocked.value = true
    }
}

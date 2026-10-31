package dev.dache.app.security

import dev.dache.core.settings.AppLockSettings
import dev.dache.core.settings.LockTimeout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class AppLockControllerTest {
    private class FakeClock : Clock {
        var now = Instant.fromEpochSeconds(1_000_000)

        override fun now() = now

        fun advance(by: Duration) {
            now += by
        }
    }

    private val dispatcher = UnconfinedTestDispatcher()
    private val clock = FakeClock()
    private val settings = MutableStateFlow(AppLockSettings(enabled = true, timeout = LockTimeout.ONE_MINUTE))

    private fun TestScope.controller() =
        AppLockController(
            settings,
            CoroutineScope(backgroundScope.coroutineContext + dispatcher),
            clock,
        )

    @Test
    fun startsLockedWhenTheLockIsOn() =
        runTest(dispatcher) {
            assertEquals(LockState.LOCKED, controller().state.value)
        }

    @Test
    fun startsUnlockedWhenTheLockIsOff() =
        runTest(dispatcher) {
            settings.value = AppLockSettings(enabled = false)
            assertEquals(LockState.UNLOCKED, controller().state.value)
        }

    @Test
    fun failedPromptStaysLocked() =
        runTest(dispatcher) {
            val lock = controller()
            assertFalse(lock.authenticate { false })
            assertEquals(LockState.LOCKED, lock.state.value)
        }

    @Test
    fun locksAgainOnlyAfterTheTimeout() =
        runTest(dispatcher) {
            val lock = controller()
            lock.authenticate { true }

            lock.onBackground()
            clock.advance(59.seconds)
            lock.onForeground()
            assertEquals(LockState.UNLOCKED, lock.state.value)

            lock.onBackground()
            clock.advance(60.seconds)
            lock.onForeground()
            assertEquals(LockState.LOCKED, lock.state.value)
        }

    @Test
    fun immediateTimeoutLocksOnEveryReturn() =
        runTest(dispatcher) {
            settings.value = AppLockSettings(enabled = true, timeout = LockTimeout.IMMEDIATELY)
            val lock = controller()
            lock.authenticate { true }

            lock.onBackground()
            lock.onForeground()
            assertEquals(LockState.LOCKED, lock.state.value)
        }

    @Test
    fun leavingForTheSystemPromptDoesNotLock() =
        runTest(dispatcher) {
            settings.value = AppLockSettings(enabled = true, timeout = LockTimeout.IMMEDIATELY)
            val lock = controller()

            lock.authenticate {
                // The device credential screen is another app's activity, so ours stops and starts again.
                lock.onBackground()
                lock.onForeground()
                true
            }
            assertEquals(LockState.UNLOCKED, lock.state.value)
        }

    @Test
    fun aDestructiveConfirmReusesAnUnlockFromTheLastHalfMinute() =
        runTest(dispatcher) {
            val lock = controller()
            var prompts = 0
            val prompt: suspend () -> Boolean = {
                prompts++
                true
            }

            lock.authenticate(prompt)
            clock.advance(20.seconds)
            assertTrue(lock.confirm(prompt))
            assertEquals(1, prompts)

            clock.advance(20.seconds)
            assertTrue(lock.confirm(prompt))
            assertEquals(2, prompts)
        }

    @Test
    fun aFailedConfirmDoesntCount() =
        runTest(dispatcher) {
            val lock = controller()

            assertFalse(lock.confirm({ false }))
            assertFalse(lock.confirm({ false }))
        }
}

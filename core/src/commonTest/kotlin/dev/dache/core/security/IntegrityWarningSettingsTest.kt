package dev.dache.core.security

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IntegrityWarningSettingsTest {
    private val rooted = setOf(IntegritySignal.SU_BINARY)

    @Test
    fun warnsOnceThereIsAnAccountAtStake() {
        assertFalse(IntegrityWarningSettings().shouldWarn(rooted, hasAccounts = false))
        assertTrue(IntegrityWarningSettings().shouldWarn(rooted, hasAccounts = true))
    }

    @Test
    fun aCleanDeviceIsNeverWarnedAbout() {
        assertFalse(IntegrityWarningSettings().shouldWarn(emptySet(), hasAccounts = true))
    }

    @Test
    fun warnsAgainOnlyWhenTheSignalsChange() {
        val seen = IntegrityWarningSettings(seen = rooted)

        assertFalse(seen.shouldWarn(rooted, hasAccounts = true))
        assertTrue(seen.shouldWarn(rooted + IntegritySignal.BOOTLOADER_UNLOCKED, hasAccounts = true))
    }

    @Test
    fun dontShowAgainHoldsEvenWhenSignalsChange() {
        val never = IntegrityWarningSettings(seen = rooted, neverShow = true)

        assertFalse(never.shouldWarn(setOf(IntegritySignal.BOOTLOADER_UNLOCKED), hasAccounts = true))
    }
}

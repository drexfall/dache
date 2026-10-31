package dev.dache.core.security

/**
 * A sign that the device's own protections are weakened, so another app or someone holding the phone could
 * read tokens more easily. Dache only warns about these; it never refuses to run.
 */
enum class IntegritySignal {
    /** An `su` binary is on the usual paths. */
    SU_BINARY,

    /** The system was built with test keys rather than release keys. */
    TEST_KEYS,

    /** A root manager such as Magisk is installed. */
    ROOT_MANAGER,

    /** Key attestation says the bootloader is unlocked. */
    BOOTLOADER_UNLOCKED,

    /** Key attestation says the boot chain wasn't verified with the manufacturer's key. */
    UNVERIFIED_BOOT,
}

/** Looks for [IntegritySignal]s. A check that can't run reports nothing rather than failing. */
fun interface IntegrityChecker {
    suspend fun check(): Set<IntegritySignal>
}

/**
 * Whether to show the warning about [signals]. It shows once there is an account whose token is at stake, and
 * again whenever the signals differ from the ones last shown, unless the user asked never to see it again.
 */
data class IntegrityWarningSettings(
    /** The signals the user last saw the warning for. */
    val seen: Set<IntegritySignal> = emptySet(),
    val neverShow: Boolean = false,
) {
    fun shouldWarn(
        signals: Set<IntegritySignal>,
        hasAccounts: Boolean,
    ): Boolean = hasAccounts && signals.isNotEmpty() && !neverShow && signals != seen
}

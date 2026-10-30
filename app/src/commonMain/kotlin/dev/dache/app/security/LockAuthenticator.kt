package dev.dache.app.security

/** The platform's unlock prompt: biometrics, with the device PIN, pattern or password as a fallback. */
interface LockAuthenticator {
    /** Whether the device has a screen lock to ask for. */
    fun isAvailable(): Boolean

    /** Shows the prompt. True once the user passes it, false if they cancel or it fails. */
    suspend fun authenticate(): Boolean
}

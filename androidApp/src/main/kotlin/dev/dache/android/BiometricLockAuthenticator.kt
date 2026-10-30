package dev.dache.android

import android.app.KeyguardManager
import android.os.Build
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.withResumed
import dev.dache.app.security.LockAuthenticator
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * BiometricPrompt with the device PIN, pattern or password as a fallback, so a broken sensor never locks anyone
 * out. Android 9 and 10 can't pair class 3 biometrics with the device credential, so they accept class 2.
 */
class BiometricLockAuthenticator(
    private val activity: FragmentActivity,
) : LockAuthenticator {
    override fun isAvailable(): Boolean = activity.getSystemService(KeyguardManager::class.java).isDeviceSecure

    override suspend fun authenticate(): Boolean {
        // The prompt is a fragment, which can't be added while the activity is stopped.
        activity.lifecycle.withResumed {}
        return suspendCancellableCoroutine { continuation ->
            val callback =
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        if (continuation.isActive) continuation.resume(true)
                    }

                    // Cancelled, locked out or no credential. A rejected finger only calls onAuthenticationFailed,
                    // and the prompt stays up for another try.
                    override fun onAuthenticationError(
                        errorCode: Int,
                        errString: CharSequence,
                    ) {
                        if (continuation.isActive) continuation.resume(false)
                    }
                }
            val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), callback)
            prompt.authenticate(
                BiometricPrompt.PromptInfo
                    .Builder()
                    .setTitle(activity.getString(R.string.lock_prompt_title))
                    .setAllowedAuthenticators(authenticators())
                    .build(),
            )
            continuation.invokeOnCancellation { prompt.cancelAuthentication() }
        }
    }

    private fun authenticators(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            BIOMETRIC_STRONG or DEVICE_CREDENTIAL
        } else {
            BIOMETRIC_WEAK or DEVICE_CREDENTIAL
        }
}

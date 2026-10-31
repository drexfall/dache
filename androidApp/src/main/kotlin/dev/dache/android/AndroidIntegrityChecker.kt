package dev.dache.android

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import dev.dache.core.security.IntegrityChecker
import dev.dache.core.security.IntegritySignal
import dev.dache.core.security.KeyAttestation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.ProviderException
import java.security.SecureRandom
import java.security.cert.X509Certificate

/**
 * Local root checks plus key attestation's view of the bootloader. All of it can be hidden by a determined
 * root user; it is there to tell an ordinary user their phone is less safe for tokens, not to stop anyone.
 */
class AndroidIntegrityChecker(
    private val context: Context,
) : IntegrityChecker {
    override suspend fun check(): Set<IntegritySignal> =
        withContext(Dispatchers.IO) {
            buildSet {
                if (SU_PATHS.any { File(it).exists() }) add(IntegritySignal.SU_BINARY)
                if (Build.TAGS?.contains("test-keys") == true) add(IntegritySignal.TEST_KEYS)
                if (ROOT_MANAGERS.any(::isInstalled)) add(IntegritySignal.ROOT_MANAGER)
                addAll(attestedBootSignals())
            }
        }

    // The manifest's <queries> makes these packages visible on Android 11 and later.
    private fun isInstalled(packageName: String): Boolean =
        try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }

    /**
     * Generates a throwaway attested key and reads the boot state from its certificate. Devices that can't attest
     * (emulators, some older keystores) report nothing.
     */
    private fun attestedBootSignals(): Set<IntegritySignal> =
        try {
            val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
            val challenge = ByteArray(CHALLENGE_BYTES).also(SecureRandom()::nextBytes)
            KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, KEYSTORE).run {
                initialize(
                    KeyGenParameterSpec
                        .Builder(ALIAS, KeyProperties.PURPOSE_SIGN)
                        .setDigests(KeyProperties.DIGEST_SHA256)
                        .setAttestationChallenge(challenge)
                        .build(),
                )
                generateKeyPair()
            }
            try {
                val leaf = keyStore.getCertificateChain(ALIAS)?.firstOrNull() as? X509Certificate
                leaf
                    ?.getExtensionValue(KeyAttestation.EXTENSION_OID)
                    ?.let(KeyAttestation::rootOfTrust)
                    ?.signals
                    .orEmpty()
            } finally {
                keyStore.deleteEntry(ALIAS)
            }
        } catch (_: GeneralSecurityException) {
            emptySet()
        } catch (_: ProviderException) {
            emptySet()
        } catch (_: IOException) {
            emptySet()
        }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "dache_integrity_probe"
        const val CHALLENGE_BYTES = 16

        val SU_PATHS =
            listOf(
                "/system/bin/su",
                "/system/xbin/su",
                "/sbin/su",
                "/system/sd/xbin/su",
                "/system/bin/failsafe/su",
                "/data/local/su",
                "/data/local/bin/su",
                "/data/local/xbin/su",
                "/su/bin/su",
                "/vendor/bin/su",
            )

        val ROOT_MANAGERS =
            listOf(
                "com.topjohnwu.magisk",
                "io.github.vvb2060.magisk",
                "me.weishu.kernelsu",
                "me.bmax.apatch",
                "eu.chainfire.supersu",
                "com.koushikdutta.superuser",
                "com.noshufou.android.su",
                "com.kingroot.kinguser",
            )
    }
}

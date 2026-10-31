package dev.dache.plugin.registry

import com.google.crypto.tink.subtle.Ed25519Verify
import java.security.GeneralSecurityException
import java.security.MessageDigest

// Tink, because the platform only has Ed25519 from API 33.
internal actual fun verifyEd25519(
    data: ByteArray,
    signature: ByteArray,
    publicKey: ByteArray,
): Boolean =
    try {
        Ed25519Verify(publicKey).verify(signature, data)
        true
    } catch (_: GeneralSecurityException) {
        false
    } catch (_: IllegalArgumentException) {
        false
    }

internal actual fun sha256(data: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(data)

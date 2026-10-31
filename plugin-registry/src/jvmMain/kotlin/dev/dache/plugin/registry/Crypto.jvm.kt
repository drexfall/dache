package dev.dache.plugin.registry

import java.security.GeneralSecurityException
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.X509EncodedKeySpec

internal actual fun verifyEd25519(
    data: ByteArray,
    signature: ByteArray,
    publicKey: ByteArray,
): Boolean =
    try {
        val key = KeyFactory.getInstance("Ed25519").generatePublic(X509EncodedKeySpec(X509_PREFIX + publicKey))
        Signature.getInstance("Ed25519").run {
            initVerify(key)
            update(data)
            verify(signature)
        }
    } catch (_: GeneralSecurityException) {
        false
    }

internal actual fun sha256(data: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(data)

/** The fixed X.509 header that turns a raw Ed25519 public key into one the JDK reads. */
private val X509_PREFIX =
    byteArrayOf(0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x70, 0x03, 0x21, 0x00)

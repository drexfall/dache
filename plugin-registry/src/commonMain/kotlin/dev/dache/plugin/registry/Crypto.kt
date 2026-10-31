package dev.dache.plugin.registry

/** Checks an Ed25519 [signature] of [data] against a raw 32-byte [publicKey]. Never throws on bad input. */
internal expect fun verifyEd25519(
    data: ByteArray,
    signature: ByteArray,
    publicKey: ByteArray,
): Boolean

internal expect fun sha256(data: ByteArray): ByteArray

internal fun sha256Hex(data: ByteArray): String =
    sha256(data).joinToString("") { (it.toInt() and BYTE_MASK).toString(HEX).padStart(2, '0') }

private const val BYTE_MASK = 0xff
private const val HEX = 16

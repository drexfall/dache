package dev.dache.core.security

/** The boot state a key attestation certificate reports, from its RootOfTrust. */
data class RootOfTrust(
    val deviceLocked: Boolean,
    val verifiedBootState: VerifiedBootState,
) {
    val signals: Set<IntegritySignal>
        get() =
            buildSet {
                if (!deviceLocked) add(IntegritySignal.BOOTLOADER_UNLOCKED)
                if (verifiedBootState != VerifiedBootState.VERIFIED) add(IntegritySignal.UNVERIFIED_BOOT)
            }
}

/** Android's verifiedBootState values, in their ASN.1 order. */
enum class VerifiedBootState {
    VERIFIED,
    SELF_SIGNED,
    UNVERIFIED,
    FAILED,
}

/**
 * Reads the RootOfTrust out of an Android key attestation extension (OID 1.3.6.1.4.1.11129.2.1.17), as
 * `X509Certificate.getExtensionValue` returns it: the KeyDescription wrapped in an OCTET STRING.
 *
 * Only a RootOfTrust enforced by secure hardware counts; a software attestation can say anything. The
 * certificate chain isn't verified against Google's root, so a compromised OS could still forge this. The
 * result is a hint for a warning, never a decision.
 */
object KeyAttestation {
    const val EXTENSION_OID = "1.3.6.1.4.1.11129.2.1.17"

    /** Null when the extension is malformed, software-backed, or has no RootOfTrust. */
    fun rootOfTrust(extensionValue: ByteArray): RootOfTrust? =
        try {
            val description = Der(Der(extensionValue).next().value).next()
            val fields = Der(description.value).all()
            val software = fields[ATTESTATION_SECURITY_LEVEL].int() == SOFTWARE_LEVEL
            val hardwareEnforced = fields[HARDWARE_ENFORCED]
            if (software) null else rootOfTrustIn(hardwareEnforced)
        } catch (_: IllegalArgumentException) {
            null
        } catch (_: IndexOutOfBoundsException) {
            null
        }

    private fun rootOfTrustIn(authorizations: Der.Element): RootOfTrust? {
        val tagged =
            Der(authorizations.value).all().firstOrNull { it.tagClass == Der.CONTEXT && it.tag == ROOT_OF_TRUST_TAG }
        return tagged?.let { explicit ->
            val fields = Der(Der(explicit.value).next().value).all()
            RootOfTrust(
                deviceLocked = fields[DEVICE_LOCKED].value.single().toInt() != 0,
                verifiedBootState = VerifiedBootState.entries[fields[VERIFIED_BOOT_STATE].int()],
            )
        }
    }

    // Positions in KeyDescription and RootOfTrust, and the AuthorizationList tag, from the attestation schema.
    private const val ATTESTATION_SECURITY_LEVEL = 1
    private const val HARDWARE_ENFORCED = 7
    private const val SOFTWARE_LEVEL = 0
    private const val ROOT_OF_TRUST_TAG = 704
    private const val DEVICE_LOCKED = 1
    private const val VERIFIED_BOOT_STATE = 2
}

/** A minimal DER reader, just enough to walk a KeyDescription. Throws IllegalArgumentException when malformed. */
internal class Der(
    private val bytes: ByteArray,
) {
    class Element(
        val tagClass: Int,
        val tag: Int,
        val value: ByteArray,
    ) {
        /** A small INTEGER or ENUMERATED. */
        fun int(): Int {
            require(value.size in 1..Int.SIZE_BYTES) { "integer too long" }
            return value.fold(0) { acc, byte -> (acc shl Byte.SIZE_BITS) or (byte.toInt() and BYTE_MASK) }
        }
    }

    private var position = 0

    fun all(): List<Element> = buildList { while (position < bytes.size) add(next()) }

    fun next(): Element {
        val first = byte()
        var tag = first and LOW_TAG_MASK
        if (tag == LOW_TAG_MASK) {
            tag = 0
            do {
                val part = byte()
                tag = (tag shl HIGH_TAG_BITS) or (part and HIGH_TAG_MASK)
            } while (part and MORE_BIT != 0)
        }
        var length = byte()
        if (length and MORE_BIT != 0) {
            val count = length and HIGH_TAG_MASK
            require(count in 1..Int.SIZE_BYTES - 1) { "length too long" }
            length = 0
            repeat(count) { length = (length shl Byte.SIZE_BITS) or byte() }
        }
        require(length <= bytes.size - position) { "length past the end" }
        val value = bytes.copyOfRange(position, position + length)
        position += length
        return Element(first and CLASS_MASK, tag, value)
    }

    private fun byte(): Int {
        require(position < bytes.size) { "unexpected end" }
        return bytes[position++].toInt() and BYTE_MASK
    }

    companion object {
        const val CONTEXT = 0x80
        private const val CLASS_MASK = 0xC0
        private const val LOW_TAG_MASK = 0x1F
        private const val HIGH_TAG_MASK = 0x7F
        private const val HIGH_TAG_BITS = 7
        private const val MORE_BIT = 0x80
        private const val BYTE_MASK = 0xFF
    }
}

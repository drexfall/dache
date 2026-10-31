package dev.dache.core.security

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Attestation extensions are built here by hand, following the KeyDescription schema. */
class KeyAttestationTest {
    private fun length(size: Int): ByteArray =
        when {
            size < 0x80 -> byteArrayOf(size.toByte())
            size < 0x100 -> byteArrayOf(0x81.toByte(), size.toByte())
            else -> byteArrayOf(0x82.toByte(), (size shr 8).toByte(), size.toByte())
        }

    private fun tlv(
        tag: ByteArray,
        vararg children: ByteArray,
    ): ByteArray {
        val value = children.fold(ByteArray(0)) { acc, child -> acc + child }
        return tag + length(value.size) + value
    }

    private fun tag(vararg bytes: Int) = ByteArray(bytes.size) { bytes[it].toByte() }

    private fun sequence(vararg children: ByteArray) = tlv(tag(0x30), *children)

    private fun integer(value: Int) = tlv(tag(0x02), byteArrayOf(value.toByte()))

    private fun enumerated(value: Int) = tlv(tag(0x0A), byteArrayOf(value.toByte()))

    private fun octets(size: Int) = tlv(tag(0x04), ByteArray(size) { it.toByte() })

    private fun boolean(value: Boolean) = tlv(tag(0x01), byteArrayOf(if (value) 0xFF.toByte() else 0))

    /** [704] EXPLICIT RootOfTrust, a high tag number. */
    private fun rootOfTrust(
        locked: Boolean,
        state: Int,
    ) = tlv(tag(0xBF, 0x85, 0x40), sequence(octets(32), boolean(locked), enumerated(state), octets(32)))

    /** [1] EXPLICIT purpose, an authorization before the RootOfTrust that the reader must skip. */
    private val purpose = tlv(tag(0xA1), tlv(tag(0x31), integer(2)))

    private fun extension(
        securityLevel: Int,
        hardware: ByteArray,
        software: ByteArray = sequence(),
    ) = tlv(
        tag(0x04),
        sequence(
            integer(3),
            enumerated(securityLevel),
            integer(4),
            enumerated(securityLevel),
            octets(16),
            octets(0),
            software,
            hardware,
        ),
    )

    @Test
    fun aLockedVerifiedDeviceHasNoSignals() {
        val root = KeyAttestation.rootOfTrust(extension(1, sequence(purpose, rootOfTrust(locked = true, state = 0))))

        assertEquals(RootOfTrust(deviceLocked = true, verifiedBootState = VerifiedBootState.VERIFIED), root)
        assertEquals(emptySet(), root?.signals)
    }

    @Test
    fun anUnlockedBootloaderIsReported() {
        val root = KeyAttestation.rootOfTrust(extension(2, sequence(purpose, rootOfTrust(locked = false, state = 2))))

        assertEquals(
            setOf(IntegritySignal.BOOTLOADER_UNLOCKED, IntegritySignal.UNVERIFIED_BOOT),
            root?.signals,
        )
    }

    @Test
    fun aSoftwareAttestationIsIgnored() {
        assertNull(KeyAttestation.rootOfTrust(extension(0, sequence(rootOfTrust(locked = false, state = 2)))))
    }

    @Test
    fun aRootOfTrustOnlyInTheSoftwareListIsIgnored() {
        val extension = extension(1, hardware = sequence(purpose), software = sequence(rootOfTrust(false, 2)))

        assertNull(KeyAttestation.rootOfTrust(extension))
    }

    @Test
    fun malformedInputGivesNothing() {
        val good = extension(1, sequence(rootOfTrust(locked = true, state = 0)))

        assertNull(KeyAttestation.rootOfTrust(ByteArray(0)))
        assertNull(KeyAttestation.rootOfTrust(good.copyOf(good.size - 5)))
        assertNull(KeyAttestation.rootOfTrust(byteArrayOf(0x04, 0x84.toByte(), 0x7F, 0x7F, 0x7F, 0x7F)))
    }
}

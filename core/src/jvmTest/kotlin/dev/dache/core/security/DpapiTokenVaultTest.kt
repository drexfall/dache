package dev.dache.core.security

import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DpapiTokenVaultTest {
    private val dir = Files.createTempDirectory("dache-vault").toFile()

    // Stands in for DPAPI: reversible, and visibly not the plain token.
    private val vault =
        DpapiTokenVault(
            dir,
            protect = { bytes -> bytes.reversedArray() + byteArrayOf(42) },
            unprotect = { bytes ->
                require(bytes.last() == 42.toByte()) { "not ours" }
                bytes.dropLast(1).toByteArray().reversedArray()
            },
        )
    private val key = TokenKey("netlify", "acc-1")

    @Test
    fun storesOnlyTheProtectedBytesUnderAHashedName() =
        runTest {
            vault.put(key, "secret-token")

            val file = dir.listFiles()!!.single()
            assertFalse(file.name.contains("netlify") || file.name.contains("acc-1"))
            assertFalse(file.readBytes().decodeToString().contains("secret-token"))
            assertEquals("secret-token", vault.get(key))
        }

    @Test
    fun aFileThatDoesntDecryptReadsAsNoToken() =
        runTest {
            dir.resolve(DpapiTokenVault.fileName(key)).writeBytes(byteArrayOf(1, 2, 3))

            assertNull(vault.get(key))
        }

    @Test
    fun deleteRemovesTheFile() =
        runTest {
            vault.put(key, "secret-token")
            vault.delete(key)

            assertNull(vault.get(key))
            assertTrue(dir.listFiles()!!.isEmpty())
        }
}

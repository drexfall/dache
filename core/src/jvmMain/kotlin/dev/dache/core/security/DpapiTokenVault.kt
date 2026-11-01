package dev.dache.core.security

import com.sun.jna.platform.win32.Crypt32Util
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/**
 * Stores tokens on Windows encrypted with DPAPI for the signed-in user: one file per account under [dir], which
 * only that Windows user can decrypt on this machine. The file name is a hash of the plugin and account ids, so
 * it says nothing about the account. [protect] and [unprotect] are DPAPI by default; tests pass their own.
 */
class DpapiTokenVault(
    private val dir: File,
    private val protect: (ByteArray) -> ByteArray = Crypt32Util::cryptProtectData,
    private val unprotect: (ByteArray) -> ByteArray = Crypt32Util::cryptUnprotectData,
) : TokenVault {
    // DPAPI reports failures as Win32Exception, a RuntimeException; the vault reports them as unavailable.
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    override suspend fun put(
        key: TokenKey,
        token: String,
    ) = withContext(Dispatchers.IO) {
        val sealed =
            try {
                protect(token.encodeToByteArray())
            } catch (e: RuntimeException) {
                throw VaultUnavailableException("Windows couldn't encrypt the token: ${e::class.simpleName}")
            }
        try {
            dir.mkdirs()
            val temp = File(dir, fileName(key) + ".tmp")
            temp.writeBytes(sealed)
            if (!temp.renameTo(file(key))) {
                file(key).writeBytes(sealed)
                temp.delete()
            }
        } catch (e: IOException) {
            throw VaultUnavailableException("The token couldn't be saved: ${e.message}")
        }
    }

    // A file that no longer decrypts (another user's, or a reinstalled profile) means no token, not a crash.
    @Suppress("SwallowedException", "TooGenericExceptionCaught")
    override suspend fun get(key: TokenKey): String? =
        withContext(Dispatchers.IO) {
            val sealed = file(key).takeIf { it.isFile }?.readBytes() ?: return@withContext null
            try {
                unprotect(sealed).decodeToString()
            } catch (e: RuntimeException) {
                null
            }
        }

    override suspend fun delete(key: TokenKey) {
        withContext(Dispatchers.IO) { file(key).delete() }
    }

    private fun file(key: TokenKey) = File(dir, fileName(key))

    companion object {
        fun fileName(key: TokenKey): String =
            MessageDigest
                .getInstance("SHA-256")
                .digest("${key.pluginId}/${key.accountId}".encodeToByteArray())
                .joinToString("") { "%02x".format(it) } + ".token"
    }
}

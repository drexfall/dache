package dev.dache.core.security

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Stores tokens in the desktop keyring (GNOME Keyring, KWallet) through libsecret's `secret-tool`.
 * The token is passed on stdin, never on the command line, so it does not show up in process listings.
 * Running without a keyring is not supported: [put] fails with [VaultUnavailableException].
 */
class SecretServiceTokenVault(
    private val secretTool: String = "secret-tool",
) : TokenVault {
    override suspend fun put(
        key: TokenKey,
        token: String,
    ) {
        val result = run(listOf("store", "--label=Dache token ($key)") + key.attributes(), stdin = token)
        if (result.exitCode != 0) {
            throw VaultUnavailableException(
                "secret-tool store failed with exit code ${result.exitCode}: ${result.stderr.take(MAX_STDERR)}",
            )
        }
    }

    override suspend fun get(key: TokenKey): String? {
        val result = run(listOf("lookup") + key.attributes())
        // secret-tool exits with 1 when nothing matches.
        return if (result.exitCode == 0) result.stdout.ifEmpty { null } else null
    }

    override suspend fun delete(key: TokenKey) {
        run(listOf("clear") + key.attributes())
    }

    private fun TokenKey.attributes() = listOf("application", APPLICATION, "plugin", pluginId, "account", accountId)

    private class ProcessResult(
        val exitCode: Int,
        val stdout: String,
        val stderr: String,
    )

    private suspend fun run(
        args: List<String>,
        stdin: String? = null,
    ): ProcessResult =
        withContext(Dispatchers.IO) {
            val process =
                try {
                    ProcessBuilder(listOf(secretTool) + args).start()
                } catch (e: IOException) {
                    throw VaultUnavailableException("secret-tool not found; install libsecret-tools to store tokens", e)
                }
            try {
                process.outputStream.use { out -> stdin?.let { out.write(it.toByteArray()) } }
            } catch (_: IOException) {
                // secret-tool exited before reading stdin (e.g. no D-Bus session); its exit code says why.
            }
            // Drain both pipes concurrently so a chatty stderr can't block the process.
            coroutineScope {
                val stderr = async { process.errorStream.bufferedReader().use { it.readText() } }
                val stdout = process.inputStream.bufferedReader().use { it.readText() }
                ProcessResult(process.waitFor(), stdout, stderr.await().trim())
            }
        }

    private companion object {
        const val APPLICATION = "dev.dache"
        const val MAX_STDERR = 200
    }
}

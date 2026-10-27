package dev.dache.core.security

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Stores tokens in the desktop keyring (GNOME Keyring, KWallet) through libsecret's `secret-tool`.
 * The token is passed on stdin, never on the command line, so it does not show up in process listings.
 */
class SecretServiceTokenVault(
    private val secretTool: String = "secret-tool",
) : TokenVault {
    override suspend fun put(
        key: TokenKey,
        token: String,
    ) {
        val exit = run(listOf("store", "--label=Dache token ($key)") + key.attributes(), stdin = token).exitCode
        check(exit == 0) { "secret-tool store failed with exit code $exit" }
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
    )

    private suspend fun run(
        args: List<String>,
        stdin: String? = null,
    ): ProcessResult =
        withContext(Dispatchers.IO) {
            val process =
                try {
                    ProcessBuilder(listOf(secretTool) + args).redirectError(ProcessBuilder.Redirect.DISCARD).start()
                } catch (e: IOException) {
                    throw IllegalStateException("secret-tool not found; install libsecret-tools to store tokens", e)
                }
            process.outputStream.use { out -> stdin?.let { out.write(it.toByteArray()) } }
            val stdout = process.inputStream.bufferedReader().use { it.readText() }
            ProcessResult(process.waitFor(), stdout)
        }

    private companion object {
        const val APPLICATION = "dev.dache"
    }
}

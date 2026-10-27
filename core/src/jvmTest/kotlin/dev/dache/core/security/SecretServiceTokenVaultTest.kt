package dev.dache.core.security

import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Runs the vault against a shell script that mimics `secret-tool` with one file per attribute set. */
class SecretServiceTokenVaultTest {
    private val dir: Path = Files.createTempDirectory("dache-secret-tool")
    private val key = TokenKey(pluginId = "netlify", accountId = "acc-1")

    private fun fakeSecretTool(body: String = STORE_IN_FILES): String {
        val script = dir.resolve("secret-tool")
        val header = "#!/bin/sh\nDIR='${dir.absolutePathString()}'\nprintf '%s\\n' \"\$*\" >> \"\$DIR/argv.log\"\n"
        script.writeText(header + body)
        script.toFile().setExecutable(true)
        return script.absolutePathString()
    }

    @Test
    fun storesLooksUpAndClearsByPluginAndAccount() =
        runTest {
            val vault = SecretServiceTokenVault(fakeSecretTool())

            vault.put(key, "fake-token-123")
            assertEquals("fake-token-123", vault.get(key))
            assertNull(vault.get(key.copy(accountId = "acc-2")))
            assertNull(vault.get(key.copy(pluginId = "vercel")))

            vault.delete(key)
            assertNull(vault.get(key))
        }

    @Test
    fun tokenNeverAppearsOnTheCommandLine() =
        runTest {
            val vault = SecretServiceTokenVault(fakeSecretTool())

            vault.put(key, "fake-token-123")
            vault.get(key)

            val argv = dir.resolve("argv.log").readText()
            assertTrue("application dev.dache plugin netlify account acc-1" in argv)
            assertFalse("fake-token-123" in argv)
        }

    @Test
    fun storeFailureWithoutKeyringThrowsVaultUnavailable() =
        runTest {
            val vault = SecretServiceTokenVault(fakeSecretTool("echo 'Cannot autolaunch D-Bus' >&2\nexit 1"))

            val error = assertFailsWith<VaultUnavailableException> { vault.put(key, "fake-token-123") }
            assertTrue("Cannot autolaunch D-Bus" in error.message.orEmpty())
            assertFalse("fake-token-123" in error.message.orEmpty())
        }

    @Test
    fun missingBinaryThrowsVaultUnavailable() =
        runTest {
            val vault = SecretServiceTokenVault(dir.resolve("does-not-exist").absolutePathString())

            assertFailsWith<VaultUnavailableException> { vault.put(key, "fake-token-123") }
        }

    private companion object {
        // Drops `--label=...` on store, then names the file after the remaining attribute pairs.
        val STORE_IN_FILES =
            """
            cmd="${'$'}1"; shift
            [ "${'$'}cmd" = store ] && shift
            file="${'$'}DIR/${'$'}(printf '%s_' "${'$'}@")"
            case "${'$'}cmd" in
              store) cat > "${'$'}file" ;;
              lookup) [ -f "${'$'}file" ] || exit 1; cat "${'$'}file" ;;
              clear) rm -f "${'$'}file" ;;
            esac
            """.trimIndent()
    }
}

package dev.dache.plugin.cli

import dev.dache.plugin.api.RegistryIndex
import java.io.File
import java.nio.file.Files
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RegistryTest {
    private val dir = Files.createTempDirectory("dache-registry").toFile()
    private val netlify = File("../../plugin-registry/manifests/netlify.json")

    private fun registryWith(vararg plugins: Pair<String, File>): File =
        dir.resolve("registry").apply {
            plugins.forEach { (folder, manifest) -> manifest.copyTo(resolve("plugins/$folder/manifest.json")) }
        }

    @Test
    fun indexListsEachPluginWithTheDigestOfItsCopiedBytes() {
        val out = dir.resolve("out")

        val outcome = buildIndex(registryWith("netlify" to netlify), out, serial = 42)

        assertTrue(outcome.ok, outcome.text)
        val index = RegistryIndex.decode(out.resolve(INDEX_FILE).readText())
        val entry = index.plugins.single()
        assertEquals(42, index.serial)
        assertEquals("plugins/netlify/1.1.0.json", entry.path)
        assertEquals(listOf("api.netlify.com"), entry.hosts)
        assertContentEquals(netlify.readBytes(), out.resolve(entry.path).readBytes())
        assertEquals(sha256Hex(netlify.readBytes()), entry.sha256)
    }

    @Test
    fun aManifestOutsideItsOwnFolderFailsTheWholeIndex() {
        val out = dir.resolve("out")

        val outcome = buildIndex(registryWith("vercel" to netlify), out, serial = 1)

        assertFalse(outcome.ok)
        assertTrue("must match its folder" in outcome.text, outcome.text)
        assertFalse(out.resolve(INDEX_FILE).exists())
    }

    @Test
    fun signaturesVerifyWithThePrintedPublicKeyOnly() {
        val keyFile = dir.resolve("key")
        val generated = generateKey(keyFile)
        val publicKey = generated.text.substringAfter("public key: ").trim()
        val file = dir.resolve(INDEX_FILE).apply { writeText("{}") }

        assertTrue(signFile(file, keyFile.readText()).ok)

        val signature = Base64.getDecoder().decode(File(file.path + ".sig").readText().trim())
        assertTrue(verify(file.readBytes(), signature, publicKeyOf(publicKey)))
        assertFalse(verify("{ }".toByteArray(), signature, publicKeyOf(publicKey)))
        assertFalse(generated.text.contains(keyFile.readText().trim()))
    }

    @Test
    fun keygenNeverOverwritesAKey() {
        val keyFile = dir.resolve("key").apply { writeText("existing") }

        assertFalse(generateKey(keyFile).ok)
        assertEquals("existing", keyFile.readText())
    }
}

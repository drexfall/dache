package dev.dache.plugin.cli

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ValidateTest {
    private val dir = Files.createTempDirectory("dache-plugin-cli").toFile()

    @Test
    fun bundledManifestsAreValid() {
        val manifests = File("../../plugin-registry/manifests").listFiles { f -> f.extension == "json" }.orEmpty()

        assertTrue(manifests.isNotEmpty())
        manifests.forEach { assertTrue(validate(it).valid, validate(it).toString()) }
    }

    @Test
    fun reportsEveryProblemWithItsPath() {
        val manifest = File("../../plugin-registry/manifests/netlify.json").readText()
        val broken = dir.resolve("broken.json")
        broken.writeText(manifest.replace("\"api.netlify.com\"]", "\"*.netlify.com\"]").replace("| date", ""))

        val report = validate(broken)

        assertFalse(report.valid)
        assertTrue(report.problems.any { it.startsWith("hosts[0]:") }, report.toString())
        assertTrue(
            report.problems.any { "createdAt: a time field must end with the date pipe" in it },
            report.toString(),
        )
    }

    @Test
    fun missingFile() {
        assertEquals(listOf("file not found"), validate(dir.resolve("nope.json")).problems)
    }
}

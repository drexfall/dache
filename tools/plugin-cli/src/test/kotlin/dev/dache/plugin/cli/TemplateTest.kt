package dev.dache.plugin.cli

import dev.dache.plugin.runtime.http.createPluginHttpClient
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/** The template plugin and the guide that walks through it stay in step. */
class TemplateTest {
    private val template = File("../../docs/plugin-template/example.json")
    private val fixtures = File("../../docs/plugin-template/fixtures")
    private val guide = File("../../docs/writing-a-plugin.md")

    @Test
    fun theTemplateIsValid() {
        val report = validate(template)

        assertTrue(report.valid, report.toString())
    }

    @Test
    fun theGuideShowsWhatTestingTheTemplatePrints() {
        val client = createPluginHttpClient(Fixtures(fixtures).engine())

        val outcome = runBlocking { refresh(template, client, "fixture-token", emptyMap()) }

        assertTrue(outcome.ok, outcome.text)
        assertTrue(
            "```\n${outcome.text}\n```" in guide.readText(),
            "update the guide's sample output:\n${outcome.text}",
        )
    }
}

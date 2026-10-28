package dev.dache.plugin.runtime.mapping

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class TemplateTest {
    private val item = Json.parseToJsonElement("""{"id": 42, "name": "My Site", "owner": {"slug": "acme"}}""")

    @Test
    fun fillsPlaceholdersFromTheItem() {
        val template = Template.parse("/orgs/{owner.slug}/projects/{id}/deploys")

        assertEquals("/orgs/acme/projects/42/deploys", template.fill(item))
        assertEquals(listOf("owner.slug", "id"), template.placeholders.map { it.source })
    }

    @Test
    fun placeholdersCanUsePipes() {
        assertEquals("my site", Template.parse("{name | lower}").fill(item))
    }

    @Test
    fun encodesFilledValues() {
        assertEquals("[My Site]", Template.parse("{name}").fill(item) { "[$it]" })
    }

    @Test
    fun missingValueOrRejectedValueFillsToNull() {
        assertNull(Template.parse("/projects/{missing}").fill(item))
        assertNull(Template.parse("/projects/{owner}").fill(item))
        assertNull(Template.parse("/projects/{id}").fill(item) { null })
        assertNull(Template.parse("/projects/{id}").fill(null))
    }

    @Test
    fun settingPlaceholdersFillFromSettings() {
        val template = Template.parse("https://example.com/{@team}/{name}")

        assertEquals("https://example.com/acme/My Site", template.fill(item, mapOf("team" to "acme")))
        assertEquals(listOf("team"), template.settings)
        assertEquals(listOf("name"), template.placeholders.map { it.source })
        assertNull(template.fill(item, emptyMap()))
        assertNull(template.fill(item, mapOf("team" to " ")))
    }

    @Test
    fun soleSettingIsOnlyForAWholeSettingTemplate() {
        assertEquals("team", Template.parse("{@team}").soleSetting)
        assertNull(Template.parse("t-{@team}").soleSetting)
        assertNull(Template.parse("{id}").soleSetting)
        assertFailsWith<ExpressionException> { Template.parse("{@}") }
        assertFailsWith<ExpressionException> { Template.parse("{@team | lower}") }
    }

    @Test
    fun textWithoutPlaceholdersFillsToItself() {
        val template = Template.parse("https://api.example.com/sites")

        assertEquals("https://api.example.com/sites", template.fill(null))
        assertEquals(emptyList(), template.placeholders)
    }

    @Test
    fun sampleReplacesPlaceholders() {
        assertEquals(
            "https://api.example.com/a/x/b/x",
            Template.parse("https://api.example.com/a/{id}/b/{name}").sample(),
        )
    }

    @Test
    fun rejectsStrayBracesAndBadExpressions() {
        assertFailsWith<ExpressionException> { Template.parse("/projects/{id") }
        assertFailsWith<ExpressionException> { Template.parse("/projects/id}") }
        assertFailsWith<ExpressionException> { Template.parse("/projects/{}") }
        assertFailsWith<ExpressionException> { Template.parse("/projects/{id | nope}") }
    }
}

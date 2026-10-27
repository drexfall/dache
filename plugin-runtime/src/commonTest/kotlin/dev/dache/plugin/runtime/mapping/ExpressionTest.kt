package dev.dache.plugin.runtime.mapping

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.time.Instant

class ExpressionTest {
    private val site: JsonElement =
        Json.parseToJsonElement(
            """
            {
              "id": 123456789012345678,
              "name": "Docs",
              "ssl_url": "https://Docs.Example.com:8443/path?q=1",
              "custom_domain": null,
              "domain_aliases": ["www.example.com", "example.com"],
              "build_settings": { "repo_url": "https://github.com/acme/docs" },
              "published_deploy": { "created_at": "2026-10-27T09:30:00.120Z", "deploy_time": 42 },
              "private": false
            }
            """.trimIndent(),
        )

    private fun eval(expr: String) = Expression.parse(expr).evaluate(site)

    @Test
    fun resolvesNestedKeysAndArrayIndexes() {
        assertEquals(Value.Text("https://github.com/acme/docs"), eval("build_settings.repo_url"))
        assertEquals(Value.Text("example.com"), eval("domain_aliases.1"))
        assertNull(eval("domain_aliases.5"))
        assertNull(eval("build_settings.missing.deeper"))
    }

    @Test
    fun keepsLargeNumericIdsExact() {
        assertEquals("123456789012345678", eval("id")?.asText())
    }

    @Test
    fun nullAndMissingAreBothNull() {
        assertNull(eval("custom_domain"))
        assertNull(eval("nope"))
    }

    @Test
    fun rootPathReturnsTheElement() {
        assertEquals(Value.Text("Docs"), Expression.parse("$").evaluate(Json.parseToJsonElement("\"Docs\"")))
    }

    @Test
    fun hostPipeStripsSchemePortPathAndCase() {
        assertEquals(Value.Text("docs.example.com"), eval("ssl_url | host"))
        assertEquals(Value.Text("example.com"), Expression.parse("$ | host").evaluate(json("\"EXAMPLE.com.\"")))
        assertEquals(
            Value.Text("api.example.com"),
            Expression.parse("$ | host").evaluate(json("\"u:p@api.example.com\"")),
        )
    }

    @Test
    fun lowerPipe() {
        assertEquals(Value.Text("docs"), eval("name | lower"))
    }

    @Test
    fun datePipeParsesIsoTimestamps() {
        assertEquals(Instant.parse("2026-10-27T09:30:00.120Z"), eval("published_deploy.created_at | date")?.asInstant())
        assertEquals(
            Instant.parse("2026-10-27T07:30:00Z"),
            Expression.parse("$ | date").evaluate(json("\"2026-10-27T09:30:00+02:00\""))?.asInstant(),
        )
        assertNull(eval("name | date"))
    }

    @Test
    fun defaultPipeFillsNullAndEmpty() {
        assertEquals(Value.Text("none"), eval("custom_domain | default:none"))
        assertEquals(Value.Text("none"), Expression.parse("$ | default:none").evaluate(json("\"\"")))
        assertEquals(Value.Text("Docs"), eval("name | default:none"))
    }

    @Test
    fun pipesChainLeftToRight() {
        assertEquals(Value.Text("example.com"), eval("custom_domain | default:HTTPS://Example.com/x | host"))
    }

    @Test
    fun valueAccessors() {
        assertEquals(42, eval("published_deploy.deploy_time")?.asInt())
        assertEquals(listOf("www.example.com", "example.com"), eval("domain_aliases")?.asTextList())
        assertEquals(listOf("Docs"), eval("name")?.asTextList())
        assertEquals("false", eval("private")?.asText())
    }

    @Test
    fun parseErrorsExplainTheProblem() {
        assertEquals(
            "unknown pipe \"upper\" in \"name | upper\"; available: host, lower, date, default",
            assertFailsWith<ExpressionException> { Expression.parse("name | upper") }.message,
        )
        assertFailsWith<ExpressionException> { Expression.parse("") }
        assertFailsWith<ExpressionException> { Expression.parse("a..b") }
        assertFailsWith<ExpressionException> { Expression.parse("name | default") }
        assertFailsWith<ExpressionException> { Expression.parse("name | lower:x") }
    }

    private fun json(text: String) = Json.parseToJsonElement(text)
}

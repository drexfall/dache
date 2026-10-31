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
    fun aKeyValueSegmentFiltersAnArray() {
        val droplet =
            Json.parseToJsonElement(
                """{"networks": {"v4": [
                  {"ip_address": "10.0.0.2", "type": "private"},
                  {"ip_address": "203.0.113.7", "type": "public"}
                ]}}""",
            )

        assertEquals(
            Value.Text("203.0.113.7"),
            Expression.parse("networks.v4.type=public.0.ip_address").evaluate(droplet),
        )
        assertNull(Expression.parse("networks.v4.type=floating.0.ip_address").evaluate(droplet))
        assertNull(Expression.parse("networks.type=public").evaluate(droplet))
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
    fun millisPipeReadsEpochMilliseconds() {
        val millis = Expression.parse("$ | millis")

        assertEquals(Instant.parse("2026-10-27T09:30:00.120Z"), millis.evaluate(json("1793093400120"))?.asInstant())
        assertEquals(Instant.parse("2026-10-27T09:30:00.120Z"), millis.evaluate(json("\"1793093400120\""))?.asInstant())
        assertNull(eval("name | millis"))
    }

    @Test
    fun keysOnAnArrayReadEveryItem() {
        val aliases = """{"domain": "a.example.com"}, {"other": 1}, {"domain": null}, {"domain": "b.example.com"}"""
        val project = json("""{"alias": [$aliases]}""")

        assertEquals(
            listOf("a.example.com", "b.example.com"),
            Expression.parse("alias.domain").evaluate(project)?.asTextList(),
        )
        assertEquals(Value.Text("a.example.com"), Expression.parse("alias.0.domain").evaluate(project))
        assertNull(Expression.parse("alias.9.domain").evaluate(project))
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
            "unknown pipe \"upper\" in \"name | upper\"; available: host, lower, date, millis, default",
            assertFailsWith<ExpressionException> { Expression.parse("name | upper") }.message,
        )
        assertFailsWith<ExpressionException> { Expression.parse("") }
        assertFailsWith<ExpressionException> { Expression.parse("a..b") }
        assertFailsWith<ExpressionException> { Expression.parse("name | default") }
        assertFailsWith<ExpressionException> { Expression.parse("name | lower:x") }
    }

    private fun json(text: String) = Json.parseToJsonElement(text)
}

package dev.dache.plugin.cli

import dev.dache.core.model.Capability
import dev.dache.plugin.runtime.mapping.ManifestKind
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SchemaTest {
    private val committed = File("../../schema/manifest.schema.json")

    @Test
    fun theCommittedSchemaIsUpToDate() {
        assertEquals(
            schemaText(),
            committed.readText().trimEnd(),
            "schema/manifest.schema.json is stale; regenerate it with " +
                "./gradlew -q :tools:plugin-cli:run --args=schema > schema/manifest.schema.json",
        )
    }

    @Test
    fun itNamesEveryKindAndCapability() {
        val text = schemaText()

        ManifestKind.entries.forEach { assertTrue("\"const\": \"${it.kindName}\"" in text, it.kindName) }
        Capability.entries.forEach { assertTrue("\"${it.name}\"" in text, it.name) }
    }

    @Test
    fun noChoiceIsEmpty() {
        // An empty oneOf or anyOf matches nothing, so every value at that spot fails validation.
        val empty = mutableListOf<String>()

        fun walk(
            element: JsonElement,
            path: String,
        ) {
            when (element) {
                is JsonObject ->
                    element.forEach { (key, value) ->
                        val choice = key == "oneOf" || key == "anyOf"
                        if (choice && value is JsonArray && value.isEmpty()) empty += "$path/$key"
                        walk(value, "$path/$key")
                    }
                is JsonArray -> element.forEachIndexed { i, value -> walk(value, "$path/$i") }
                else -> Unit
            }
        }
        walk(ManifestSchema.generate(), "#")

        assertEquals(emptyList(), empty)
    }

    @Test
    fun bundledManifestsPointAtIt() {
        File("../../plugin-registry/manifests").listFiles { f -> f.extension == "json" }.orEmpty().forEach {
            assertTrue("\"\$schema\": \"../../schema/manifest.schema.json\"" in it.readText(), it.name)
        }
    }
}

package dev.dache.plugin.cli

import dev.dache.core.model.Capability
import dev.dache.plugin.runtime.mapping.ManifestKind
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
    fun bundledManifestsPointAtIt() {
        File("../../plugin-registry/manifests").listFiles { f -> f.extension == "json" }.orEmpty().forEach {
            assertTrue("\"\$schema\": \"../../schema/manifest.schema.json\"" in it.readText(), it.name)
        }
    }
}

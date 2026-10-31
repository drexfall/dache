package dev.dache.plugin.cli

import dev.dache.core.model.Capability
import dev.dache.plugin.api.FieldSpec
import dev.dache.plugin.api.PluginManifest
import dev.dache.plugin.runtime.mapping.FieldDef
import dev.dache.plugin.runtime.mapping.FieldType
import dev.dache.plugin.runtime.mapping.ManifestKind
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.PolymorphicKind
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.descriptors.elementDescriptors
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * The JSON Schema for plugin manifests. The structure comes from plugin-api's serializers, so it can't drift from
 * what the app reads; the kinds, their fields and the capabilities come from the engine, so editors can complete
 * them. It doesn't check expressions, hosts or placeholders: `dache-plugin validate` does.
 */
@OptIn(ExperimentalSerializationApi::class)
object ManifestSchema {
    const val ID = "https://raw.githubusercontent.com/drexfall/dache/main/schema/manifest.schema.json"

    private const val API_PACKAGE = "dev.dache.plugin.api."
    private const val FIELD_SPEC = "FieldSpec"
    private const val JSON_OBJECT = "kotlinx.serialization.json.JsonObject"
    private const val NESTED = "FieldSpecNested"

    fun generate(): JsonObject {
        val defs = sortedMapOf<String, JsonObject>()
        val root = Builder(defs).schema(PluginManifest.serializer().descriptor)
        defs["PluginManifest"] = defs.getValue("PluginManifest").withProperty("capabilities", capabilities())
        defs["ResourceSpec"] =
            defs
                .getValue("ResourceSpec")
                .withProperty("kind", enum(ManifestKind.entries.map { it.kindName }))
                .plus("allOf" to JsonArray(ManifestKind.entries.map(::fieldsFor)))
        return JsonObject(
            mapOf(
                "\$schema" to JsonPrimitive("https://json-schema.org/draft/2020-12/schema"),
                "\$id" to JsonPrimitive(ID),
                "title" to JsonPrimitive("Dache plugin manifest"),
            ) + root + ("\$defs" to JsonObject(defs)),
        )
    }

    /** Walks serializer descriptors, adding a definition per class to [defs] and returning a schema or a ref. */
    private class Builder(
        private val defs: MutableMap<String, JsonObject>,
    ) {
        fun schema(descriptor: SerialDescriptor): JsonObject =
            // An action body is free-form JSON; its string values are templates.
            if (descriptor.serialName == JSON_OBJECT) type("object") else typed(descriptor)

        private fun typed(descriptor: SerialDescriptor): JsonObject =
            when (val kind = descriptor.kind) {
                SerialKind.ENUM ->
                    JsonObject(
                        mapOf(
                            "enum" to
                                JsonArray(
                                    (0 until descriptor.elementsCount).map {
                                        JsonPrimitive(
                                            descriptor.getElementName(it),
                                        )
                                    },
                                ),
                        ),
                    )
                PrimitiveKind.STRING -> type("string")
                PrimitiveKind.BOOLEAN -> type("boolean")
                PrimitiveKind.INT, PrimitiveKind.LONG, PrimitiveKind.SHORT, PrimitiveKind.BYTE -> type("integer")
                PrimitiveKind.FLOAT, PrimitiveKind.DOUBLE -> type("number")
                StructureKind.LIST -> array(schema(descriptor.getElementDescriptor(0)))
                StructureKind.MAP ->
                    type("object").plus("additionalProperties" to schema(descriptor.getElementDescriptor(1)))
                PolymorphicKind.SEALED -> define(descriptor) { sealed(descriptor) }
                StructureKind.CLASS, StructureKind.OBJECT -> classOrFieldSpec(descriptor)
                else -> error("no schema for ${descriptor.serialName} ($kind)")
            }

        private fun classOrFieldSpec(descriptor: SerialDescriptor): JsonObject =
            if (name(descriptor) == FIELD_SPEC) {
                define(descriptor) { fieldSpec() }
            } else {
                define(descriptor) { obj(descriptor) }
            }

        /** A string expression or template, a list of expressions, or nested records. */
        private fun fieldSpec(): JsonObject {
            val nested = schema(FieldSpec.Nested.serializer().descriptor)
            return JsonObject(mapOf("oneOf" to JsonArray(listOf(type("string"), array(type("string")), nested))))
        }

        private fun obj(
            descriptor: SerialDescriptor,
            discriminator: String? = null,
        ): JsonObject {
            val names = (0 until descriptor.elementsCount).map(descriptor::getElementName)
            val properties =
                names.indices.associate { index -> names[index] to schema(descriptor.getElementDescriptor(index)) }
            val required = names.filterIndexed { index, _ -> !descriptor.isElementOptional(index) }
            val typed = discriminator?.let { mapOf("type" to JsonObject(mapOf("const" to JsonPrimitive(it)))) }
            return buildJsonObject {
                put("type", "object")
                put("properties", JsonObject(typed.orEmpty() + properties))
                putJsonArray("required") {
                    (listOfNotNull(discriminator?.let { "type" }) + required).forEach { add(JsonPrimitive(it)) }
                }
                // Manifests are decoded strictly, so an unknown key is an error.
                put("additionalProperties", false)
            }
        }

        /** One object per subclass, told apart by its `type`. */
        private fun sealed(descriptor: SerialDescriptor): JsonObject {
            val subclasses = descriptor.getElementDescriptor(1).elementDescriptors
            return JsonObject(mapOf("oneOf" to JsonArray(subclasses.map { obj(it, discriminator = it.serialName) })))
        }

        private fun define(
            descriptor: SerialDescriptor,
            build: () -> JsonObject,
        ): JsonObject {
            val name = name(descriptor)
            if (name !in defs) {
                defs[name] = JsonObject(emptyMap()) // Placeholder, so a recursive type refers to itself.
                defs[name] = build()
            }
            return ref(name)
        }

        private fun name(descriptor: SerialDescriptor): String =
            descriptor.serialName
                .removeSuffix("?")
                .removePrefix(API_PACKAGE)
                .replace(".", "")
    }

    private fun capabilities(): JsonObject = array(enum(Capability.entries.map { it.name }))

    /** When `kind` is [kind], `fields` takes exactly its fields, with the required ones required. */
    private fun fieldsFor(kind: ManifestKind): JsonObject =
        buildJsonObject {
            putJsonObject("if") {
                putJsonObject("properties") { putJsonObject("kind") { put("const", kind.kindName) } }
                putJsonArray("required") { add(JsonPrimitive("kind")) }
            }
            putJsonObject("then") {
                putJsonObject("properties") { put("fields", fieldMap(kind.fields)) }
            }
        }

    private fun fieldMap(fields: Map<String, FieldDef>): JsonObject =
        buildJsonObject {
            put("type", "object")
            put("properties", JsonObject(fields.mapValues { (_, def) -> field(def) }))
            putJsonArray("required") { fields.filterValues { it.required }.keys.forEach { add(JsonPrimitive(it)) } }
            put("additionalProperties", false)
        }

    private fun field(def: FieldDef): JsonElement =
        when (def.type) {
            FieldType.RECORDS -> ref(NESTED).plus("properties" to JsonObject(mapOf("fields" to fieldMap(def.nested))))
            FieldType.TEXT_LIST ->
                JsonObject(
                    mapOf("oneOf" to JsonArray(listOf(type("string"), array(type("string"))))),
                )
            else -> type("string")
        }
}

private fun type(name: String) = JsonObject(mapOf("type" to JsonPrimitive(name)))

private fun array(items: JsonObject) = JsonObject(mapOf("type" to JsonPrimitive("array"), "items" to items))

private fun enum(values: List<String>) =
    buildJsonObject {
        put("enum", buildJsonArray { values.forEach { add(JsonPrimitive(it)) } })
    }

private fun ref(name: String) = JsonObject(mapOf("\$ref" to JsonPrimitive("#/\$defs/$name")))

private fun JsonObject.plus(entry: Pair<String, JsonElement>) = JsonObject(this + entry)

private fun JsonObject.withProperty(
    name: String,
    schema: JsonObject,
): JsonObject {
    val properties = getValue("properties") as JsonObject
    return plus("properties" to JsonObject(properties + (name to schema)))
}

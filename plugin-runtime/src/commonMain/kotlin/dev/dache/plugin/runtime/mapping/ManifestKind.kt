package dev.dache.plugin.runtime.mapping

import dev.dache.core.model.Capability

enum class FieldType {
    TEXT,
    TEXT_LIST,

    /** Must end in the `date` pipe. */
    TIME,
    INT,

    /** Mapped through the record's statusMap to a `DeployStatus`. */
    STATUS,

    /** A nested mapping with its own schema. */
    RECORDS,
}

data class FieldDef(
    val type: FieldType,
    val required: Boolean,
    val nested: Map<String, FieldDef> = emptyMap(),
)

/**
 * The unified models a manifest can produce, with the fields each needs. Kinds are added when the first
 * plugin that produces them is written.
 */
enum class ManifestKind(
    val kindName: String,
    val capability: Capability,
    val fields: Map<String, FieldDef>,
) {
    SITE(
        kindName = "site",
        capability = Capability.SITES,
        fields =
            mapOf(
                "nativeId" to FieldDef(FieldType.TEXT, required = true),
                "name" to FieldDef(FieldType.TEXT, required = true),
                // Without it, the site links to the provider's dashboard.
                "webUrl" to FieldDef(FieldType.TEXT, required = false),
                "primaryUrl" to FieldDef(FieldType.TEXT, required = true),
                "customDomains" to FieldDef(FieldType.TEXT_LIST, required = false),
                "repoUrl" to FieldDef(FieldType.TEXT, required = false),
                "deployments" to
                    FieldDef(
                        FieldType.RECORDS,
                        required = false,
                        nested =
                            mapOf(
                                "nativeId" to FieldDef(FieldType.TEXT, required = true),
                                "webUrl" to FieldDef(FieldType.TEXT, required = true),
                                "status" to FieldDef(FieldType.STATUS, required = true),
                                "createdAt" to FieldDef(FieldType.TIME, required = true),
                                "branch" to FieldDef(FieldType.TEXT, required = false),
                                "commitMessage" to FieldDef(FieldType.TEXT, required = false),
                                "durationSeconds" to FieldDef(FieldType.INT, required = false),
                            ),
                    ),
            ),
    ),
    ;

    companion object {
        fun named(name: String): ManifestKind? = entries.firstOrNull { it.kindName == name }
    }
}

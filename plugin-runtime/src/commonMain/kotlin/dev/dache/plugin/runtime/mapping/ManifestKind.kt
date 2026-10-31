package dev.dache.plugin.runtime.mapping

import dev.dache.core.model.Capability
import dev.dache.core.model.CertificateStatus
import dev.dache.core.model.DeployStatus
import dev.dache.core.model.ResourceKind
import dev.dache.core.model.ServiceStatus

enum class FieldType {
    TEXT,
    TEXT_LIST,

    /** Must end in the `date` pipe. */
    TIME,
    INT,

    /** Mapped through the record's statusMap to one of the field's `statuses`. */
    STATUS,

    /** A nested mapping with its own schema. */
    RECORDS,
}

/** [statuses]: for a [FieldType.STATUS] field, the names a statusMap may map to. */
data class FieldDef(
    val type: FieldType,
    val required: Boolean,
    val nested: Map<String, FieldDef> = emptyMap(),
    val statuses: List<String> = emptyList(),
)

private fun text(required: Boolean = false) = FieldDef(FieldType.TEXT, required)

private fun time(required: Boolean = false) = FieldDef(FieldType.TIME, required)

private val textList = FieldDef(FieldType.TEXT_LIST, required = false)

private fun status(
    values: Array<out Enum<*>>,
    required: Boolean = true,
) = FieldDef(FieldType.STATUS, required, statuses = values.map { it.name })

/**
 * The unified models a manifest can produce, with the fields each needs. Kinds are added when the first
 * plugin that produces them is written. Every kind with a `webUrl` falls back to the provider's dashboard.
 */
enum class ManifestKind(
    val kindName: String,
    val resourceKind: ResourceKind,
    val capability: Capability,
    val fields: Map<String, FieldDef>,
) {
    SITE(
        kindName = "site",
        resourceKind = ResourceKind.SITE,
        capability = Capability.SITES,
        fields =
            mapOf(
                "nativeId" to text(required = true),
                "name" to text(required = true),
                "webUrl" to text(),
                "primaryUrl" to text(required = true),
                "customDomains" to textList,
                "repoUrl" to text(),
                "deployments" to
                    FieldDef(
                        FieldType.RECORDS,
                        required = false,
                        nested =
                            mapOf(
                                "nativeId" to text(required = true),
                                "webUrl" to text(required = true),
                                "status" to status(DeployStatus.entries.toTypedArray()),
                                "createdAt" to time(required = true),
                                "branch" to text(),
                                "commitMessage" to text(),
                                "durationSeconds" to FieldDef(FieldType.INT, required = false),
                            ),
                    ),
            ),
    ),
    DOMAIN(
        kindName = "domain",
        resourceKind = ResourceKind.DOMAIN,
        capability = Capability.DOMAINS,
        fields =
            mapOf(
                "nativeId" to text(required = true),
                "name" to text(required = true),
                "webUrl" to text(),
                // Only registrars know it.
                "expiresAt" to time(),
                "certificates" to
                    FieldDef(
                        FieldType.RECORDS,
                        required = false,
                        nested =
                            mapOf(
                                "nativeId" to text(required = true),
                                "status" to status(CertificateStatus.entries.toTypedArray()),
                                "hosts" to textList,
                                "expiresAt" to time(),
                            ),
                    ),
            ),
    ),
    DNS_RECORD(
        kindName = "dnsRecord",
        resourceKind = ResourceKind.DNS_RECORD,
        capability = Capability.DNS,
        fields =
            mapOf(
                "nativeId" to text(required = true),
                "webUrl" to text(),
                "zoneName" to text(required = true),
                "type" to text(required = true),
                "name" to text(required = true),
                "content" to text(required = true),
            ),
    ),
    WORKER(
        kindName = "worker",
        resourceKind = ResourceKind.WORKER,
        capability = Capability.WORKERS,
        fields =
            mapOf(
                "nativeId" to text(required = true),
                "name" to text(required = true),
                "webUrl" to text(),
                "routes" to textList,
                "modifiedAt" to time(),
            ),
    ),

    /** Anything else, described by the resource's `service` spec. */
    SERVICE(
        kindName = "service",
        resourceKind = ResourceKind.SERVICE,
        capability = Capability.SERVICES,
        fields =
            mapOf(
                "nativeId" to text(required = true),
                "name" to text(required = true),
                "webUrl" to text(),
                "detail" to text(),
                "status" to status(ServiceStatus.entries.toTypedArray(), required = false),
                "createdAt" to time(),
                "updatedAt" to time(),
            ),
    ),
    ;

    companion object {
        fun named(name: String): ManifestKind? = entries.firstOrNull { it.kindName == name }
    }
}

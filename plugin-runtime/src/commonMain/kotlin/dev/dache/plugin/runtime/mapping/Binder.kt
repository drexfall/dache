package dev.dache.plugin.runtime.mapping

import dev.dache.core.model.Certificate
import dev.dache.core.model.CertificateStatus
import dev.dache.core.model.DeployStatus
import dev.dache.core.model.Deployment
import dev.dache.core.model.DnsRecord
import dev.dache.core.model.Domain
import dev.dache.core.model.ProviderId
import dev.dache.core.model.ServiceResource
import dev.dache.core.model.ServiceStatus
import dev.dache.core.model.Site
import dev.dache.core.model.Worker
import dev.dache.plugin.api.ServiceSpec

/** Builds unified models from mapped records. A record missing a required field is skipped. */
internal class Binder(
    private val providerId: ProviderId,
    private val accountId: String,
) {
    fun site(record: Mapping.Record): Site? =
        orSkip {
            Site(
                providerId = providerId,
                accountId = accountId,
                nativeId = record.required("nativeId"),
                webUrl = record.webUrl(),
                name = record.required("name"),
                primaryUrl = record.required("primaryUrl"),
                customDomains = record.textList("customDomains").distinct(),
                repoUrl = record.text("repoUrl"),
                deployments = record.records("deployments").mapNotNull(::deployment),
            )
        }

    fun domain(record: Mapping.Record): Domain? =
        orSkip {
            Domain(
                providerId = providerId,
                accountId = accountId,
                nativeId = record.required("nativeId"),
                webUrl = record.webUrl(),
                name = record.required("name"),
                expiresAt = record.time("expiresAt"),
                certificates = record.records("certificates").mapNotNull(::certificate),
            )
        }

    fun dnsRecord(record: Mapping.Record): DnsRecord? =
        orSkip {
            DnsRecord(
                providerId = providerId,
                accountId = accountId,
                nativeId = record.required("nativeId"),
                webUrl = record.webUrl(),
                zoneName = record.required("zoneName"),
                type = record.required("type"),
                name = record.required("name"),
                content = record.required("content"),
            )
        }

    fun worker(record: Mapping.Record): Worker? =
        orSkip {
            Worker(
                providerId = providerId,
                accountId = accountId,
                nativeId = record.required("nativeId"),
                webUrl = record.webUrl(),
                name = record.required("name"),
                routes = record.textList("routes").distinct(),
                modifiedAt = record.time("modifiedAt"),
            )
        }

    fun service(
        record: Mapping.Record,
        spec: ServiceSpec,
    ): ServiceResource? =
        orSkip {
            ServiceResource(
                providerId = providerId,
                accountId = accountId,
                nativeId = "${spec.id}/${record.required("nativeId")}",
                webUrl = record.webUrl(),
                service = spec.id,
                serviceLabel = spec.label,
                name = record.required("name"),
                detail = record.text("detail")?.ifBlank { null },
                status = record.text("status")?.let { record.status("status", ServiceStatus.UNKNOWN) },
                createdAt = record.time("createdAt"),
                updatedAt = record.time("updatedAt"),
            )
        }

    private fun deployment(record: Mapping.Record): Deployment? =
        orSkip {
            Deployment(
                nativeId = record.required("nativeId"),
                webUrl = record.required("webUrl"),
                status = record.status("status", DeployStatus.UNKNOWN),
                branch = record.text("branch"),
                commitMessage = record.text("commitMessage"),
                createdAt = record.time("createdAt") ?: throw MissingFieldException("createdAt"),
                durationSeconds = record.int("durationSeconds"),
            )
        }

    private fun certificate(record: Mapping.Record): Certificate? =
        orSkip {
            Certificate(
                nativeId = record.required("nativeId"),
                status = record.status("status", CertificateStatus.UNKNOWN),
                hosts = record.textList("hosts").distinct(),
                expiresAt = record.time("expiresAt"),
            )
        }

    private fun Mapping.Record.required(field: String): String = text(field) ?: throw MissingFieldException(field)

    /** Without its own link, a resource links to the provider's dashboard. */
    private fun Mapping.Record.webUrl(): String = text("webUrl") ?: providerId.dashboardUrl
}

private inline fun <T> orSkip(build: () -> T): T? =
    try {
        build()
    } catch (_: MissingFieldException) {
        null
    }

/** Unmapped and unlisted provider values are [unknown]. */
private inline fun <reified E : Enum<E>> Mapping.Record.status(
    field: String,
    unknown: E,
): E = status(field)?.let { name -> enumValues<E>().firstOrNull { it.name == name } } ?: unknown

private class MissingFieldException(
    field: String,
) : Exception("missing $field")

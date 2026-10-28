package dev.dache.plugin.runtime.mapping

import dev.dache.core.model.Deployment
import dev.dache.core.model.ProviderId
import dev.dache.core.model.Site

/** Builds unified models from mapped records. A record missing a required field is skipped. */
internal object Binder {
    fun site(
        record: Mapping.Record,
        providerId: ProviderId,
        accountId: String,
    ): Site? =
        orSkip {
            Site(
                providerId = providerId,
                accountId = accountId,
                nativeId = record.required("nativeId"),
                webUrl = record.text("webUrl") ?: providerId.dashboardUrl,
                name = record.required("name"),
                primaryUrl = record.required("primaryUrl"),
                customDomains = record.textList("customDomains").distinct(),
                repoUrl = record.text("repoUrl"),
                deployments = record.records("deployments").mapNotNull(::deployment),
            )
        }

    private fun deployment(record: Mapping.Record): Deployment? =
        orSkip {
            Deployment(
                nativeId = record.required("nativeId"),
                webUrl = record.required("webUrl"),
                status = record.status("status"),
                branch = record.text("branch"),
                commitMessage = record.text("commitMessage"),
                createdAt = record.time("createdAt") ?: throw MissingFieldException("createdAt"),
                durationSeconds = record.int("durationSeconds"),
            )
        }

    private inline fun <T> orSkip(build: () -> T): T? =
        try {
            build()
        } catch (_: MissingFieldException) {
            null
        }

    private fun Mapping.Record.required(field: String): String = text(field) ?: throw MissingFieldException(field)

    private class MissingFieldException(
        field: String,
    ) : Exception("missing $field")
}

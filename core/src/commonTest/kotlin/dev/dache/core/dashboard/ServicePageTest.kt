package dev.dache.core.dashboard

import dev.dache.core.model.Capability
import dev.dache.core.model.ProviderId
import dev.dache.core.model.ProviderSnapshot
import dev.dache.core.model.ResourceKind
import dev.dache.core.model.ServicePage
import dev.dache.core.model.ServiceResource
import dev.dache.core.model.Worker
import kotlin.test.Test
import kotlin.test.assertEquals

class ServicePageTest {
    private fun service(
        service: String,
        name: String,
    ) = ServiceResource(
        providerId = ProviderId.CLOUDFLARE,
        accountId = "acc",
        nativeId = "$service/$name",
        webUrl = "https://dash.example.com",
        service = service,
        serviceLabel = service,
        name = name,
        detail = null,
        status = null,
        createdAt = null,
        updatedAt = null,
    )

    private val worker =
        Worker(ProviderId.CLOUDFLARE, "acc", "w1", "https://dash.example.com", "api", emptyList(), null)

    @Test
    fun withoutDeclaredPagesEachKindGetsOneInCapabilityOrder() {
        val pages = ServicePage.perKind(setOf(Capability.DNS, Capability.SITES, Capability.DEPLOYMENTS))

        assertEquals(listOf(setOf(ResourceKind.SITE), setOf(ResourceKind.DNS_RECORD)), pages.map { it.kinds })
        assertEquals(listOf("site", "dns_record"), pages.map { it.id })
    }

    @Test
    fun aSnapshotNarrowedToAPageKeepsOnlyWhatItShows() {
        val snapshot =
            ProviderSnapshot(
                workers = listOf(worker),
                services = listOf(service("bucket", "assets"), service("queue", "jobs")),
                missingPermissions = setOf("Storage Read", "Queues Read"),
            )
        val page = ServicePage("r2", "R2", services = setOf("bucket"), permissions = setOf("Storage Read"))

        val shown = snapshot.only(page)

        assertEquals(listOf("assets"), shown.resources.map { it.displayName })
        assertEquals(setOf("Storage Read"), shown.missingPermissions)
    }

    @Test
    fun servicesAreMatchedByServiceNotKind() {
        val workers = ServicePage("workers", null, kinds = setOf(ResourceKind.WORKER, ResourceKind.SERVICE))

        val snapshot = ProviderSnapshot(workers = listOf(worker), services = listOf(service("kv", "a")))

        assertEquals(listOf(worker), snapshot.only(workers).resources)
    }
}

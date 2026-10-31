package dev.dache.core.model

/**
 * One page of an account beside its dashboard, such as DNS or Workers. It shows the resources of [kinds] and
 * the [ServiceResource]s whose service is in [services]. A null [title] means the page is named after its kind.
 * [permissions] are what its resources need from the token, so the page can say when one is missing.
 */
data class ServicePage(
    val id: String,
    val title: String?,
    val kinds: Set<ResourceKind> = emptySet(),
    val services: Set<String> = emptySet(),
    val permissions: Set<String> = emptySet(),
) {
    fun shows(resource: Resource): Boolean =
        if (resource is ServiceResource) resource.service in services else resource.kind in kinds

    companion object {
        /** One page per kind of resource [capabilities] can show, for providers that don't group them. */
        fun perKind(capabilities: Set<Capability>): List<ServicePage> =
            capabilities
                .sorted()
                .mapNotNull { it.kind }
                .distinct()
                .map { ServicePage(id = it.name.lowercase(), title = null, kinds = setOf(it)) }
    }
}

/** The kind of resource a capability shows; null for ones that add to another kind or have none of their own. */
val Capability.kind: ResourceKind?
    get() =
        when (this) {
            Capability.SITES -> ResourceKind.SITE
            Capability.DOMAINS -> ResourceKind.DOMAIN
            Capability.DNS -> ResourceKind.DNS_RECORD
            Capability.CI -> ResourceKind.CI_RUN
            Capability.SERVERS -> ResourceKind.SERVER
            Capability.WORKERS -> ResourceKind.WORKER
            Capability.MONITORS -> ResourceKind.MONITOR
            Capability.DEPLOYMENTS, Capability.SERVICES -> null
        }

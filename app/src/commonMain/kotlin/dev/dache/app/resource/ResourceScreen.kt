package dev.dache.app.resource

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.dache.app.actions.ResourceActions
import dev.dache.app.dashboard.CiRunDetails
import dev.dache.app.dashboard.CollapsibleSection
import dev.dache.app.dashboard.DnsRecordDetails
import dev.dache.app.dashboard.DomainDetails
import dev.dache.app.dashboard.MonitorDetails
import dev.dache.app.dashboard.ServerDetails
import dev.dache.app.dashboard.ServiceResourceDetails
import dev.dache.app.dashboard.SiteDetails
import dev.dache.app.dashboard.WorkerDetails
import dev.dache.app.dashboard.domainExpiry
import dev.dache.app.dashboard.resourceSections
import dev.dache.app.navigation.BackStack
import dev.dache.app.navigation.Breadcrumbs
import dev.dache.app.navigation.Destination
import dev.dache.app.resources.Res
import dev.dache.app.resources.detail_linked
import dev.dache.app.resources.kind_ci_run
import dev.dache.app.resources.kind_dns_record
import dev.dache.app.resources.kind_domain
import dev.dache.app.resources.kind_monitor
import dev.dache.app.resources.kind_server
import dev.dache.app.resources.kind_service
import dev.dache.app.resources.kind_site
import dev.dache.app.resources.kind_worker
import dev.dache.app.resources.open_in
import dev.dache.app.resources.resource_details
import dev.dache.app.resources.resource_missing
import dev.dache.app.resources.resource_nothing_linked
import dev.dache.app.ui.components.ExternalLinkButton
import dev.dache.app.ui.layout.AdaptiveGrid
import dev.dache.app.ui.layout.FormFactor
import dev.dache.app.ui.layout.LocalFormFactor
import dev.dache.app.ui.layout.PageScaffold
import dev.dache.core.dashboard.DashboardState
import dev.dache.core.model.CiRun
import dev.dache.core.model.DnsRecord
import dev.dache.core.model.Domain
import dev.dache.core.model.Monitor
import dev.dache.core.model.Resource
import dev.dache.core.model.ResourceKind
import dev.dache.core.model.Server
import dev.dache.core.model.ServiceResource
import dev.dache.core.model.Site
import dev.dache.core.model.Worker
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Clock

/** One resource as its own page: its details, then everything linked to it, grouped by type like an account. */
@Composable
fun ResourceScreen(
    destination: Destination.Resource,
    /** The pages that led here, this one last, for the breadcrumb. */
    trail: BackStack,
    state: DashboardState,
    onRefresh: () -> Unit,
    onOpenMenu: (() -> Unit)?,
    onNavigate: (Destination) -> Unit,
) {
    val ref = destination.ref
    val resource = state.resource(ref)
    val account = state.accounts.firstOrNull { it.id == ref.accountId }
    val compact = LocalFormFactor.current == FormFactor.COMPACT
    val openLabel = stringResource(Res.string.open_in, ref.providerId.displayName)
    PageScaffold(
        title = resource?.displayName ?: ref.nativeId,
        subtitle =
            listOfNotNull(
                (resource as? ServiceResource)?.serviceLabel ?: stringResource(ref.kind.label()),
                ref.providerId.displayName,
                account?.displayName,
            ).joinToString(" · "),
        isRefreshing = state.isRefreshing,
        onRefresh = onRefresh,
        onOpenMenu = onOpenMenu,
        header = { Breadcrumbs(trail, state, onNavigate) },
        // Same placement as the account page: toolbar on desktop, its own row on phones.
        actions = {
            if (!compact && resource != null) ExternalLinkButton(openLabel, resource.webUrl)
        },
    ) {
        if (resource == null) {
            if (state.lastUpdated == null) {
                Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                Text(stringResource(Res.string.resource_missing), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@PageScaffold
        }
        if (compact) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                ExternalLinkButton(openLabel, resource.webUrl, outlined = true)
            }
        }
        CollapsibleSection(Res.string.resource_details) { DetailsCard(resource, state) }
        val sections = resourceSections(state.linkedTo(ref).mapNotNull { state.resource(it) }, state)
        CollapsibleSection(Res.string.detail_linked) {
            if (sections.isEmpty()) {
                Text(
                    stringResource(Res.string.resource_nothing_linked),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                AdaptiveGrid(sections, minCellWidth = 420.dp)
            }
        }
    }
}

@Composable
private fun DetailsCard(
    resource: Resource,
    state: DashboardState,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when (resource) {
                is Site -> SiteDetails(resource)
                is CiRun -> CiRunDetails(resource, state)
                is Domain -> DomainDetails(resource, domainExpiry(resource, Clock.System.now()))
                is DnsRecord -> DnsRecordDetails(resource)
                is Server -> ServerDetails(resource)
                is Worker -> WorkerDetails(resource)
                is Monitor -> MonitorDetails(resource)
                is ServiceResource -> ServiceResourceDetails(resource)
            }
            ResourceActions(resource)
        }
    }
}

private fun ResourceKind.label(): StringResource =
    when (this) {
        ResourceKind.SITE -> Res.string.kind_site
        ResourceKind.DOMAIN -> Res.string.kind_domain
        ResourceKind.DNS_RECORD -> Res.string.kind_dns_record
        ResourceKind.CI_RUN -> Res.string.kind_ci_run
        ResourceKind.SERVER -> Res.string.kind_server
        ResourceKind.WORKER -> Res.string.kind_worker
        ResourceKind.MONITOR -> Res.string.kind_monitor
        ResourceKind.SERVICE -> Res.string.kind_service
    }

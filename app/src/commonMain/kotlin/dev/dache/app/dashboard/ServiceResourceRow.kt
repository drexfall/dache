package dev.dache.app.dashboard

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import dev.dache.app.resources.Res
import dev.dache.app.resources.detail_created
import dev.dache.app.resources.detail_modified
import dev.dache.app.resources.detail_name
import dev.dache.app.resources.detail_status
import dev.dache.app.ui.components.StatusChip
import dev.dache.app.ui.components.formatDay
import dev.dache.app.ui.components.formatHour
import dev.dache.app.ui.components.relativeTime
import dev.dache.app.ui.components.serviceStatusLabel
import dev.dache.core.dashboard.DashboardState
import dev.dache.core.model.ServiceResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun ServiceResourceRow(
    resource: ServiceResource,
    state: DashboardState,
) {
    ResourceRow(
        resource = resource,
        state = state,
        title = resource.name,
        summary =
            listOf(
                listOfNotNull(
                    resource.providerId.displayName,
                    resource.detail,
                    (resource.updatedAt ?: resource.createdAt)?.let { relativeTime(it) },
                ).joinToString(SEPARATOR),
            ),
        trailing = { resource.status?.let { StatusChip(it) } },
    ) { ServiceResourceDetails(resource) }
}

@Composable
fun ServiceResourceDetails(resource: ServiceResource) {
    Column {
        DetailLine(stringResource(Res.string.detail_name), resource.name)
        resource.detail?.let { DetailLine(resource.serviceLabel, it) }
        resource.status?.let { DetailLine(stringResource(Res.string.detail_status), serviceStatusLabel(it)) }
        resource.createdAt?.let {
            DetailLine(stringResource(Res.string.detail_created), "${formatDay(it)} ${formatHour(it)}")
        }
        resource.updatedAt?.let {
            DetailLine(stringResource(Res.string.detail_modified), "${formatDay(it)} ${formatHour(it)}")
        }
    }
}

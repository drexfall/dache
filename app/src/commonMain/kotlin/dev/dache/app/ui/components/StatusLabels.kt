package dev.dache.app.ui.components

import androidx.compose.runtime.Composable
import dev.dache.app.resources.Res
import dev.dache.app.resources.status_active
import dev.dache.app.resources.status_building
import dev.dache.app.resources.status_cancelled
import dev.dache.app.resources.status_down
import dev.dache.app.resources.status_failed
import dev.dache.app.resources.status_off
import dev.dache.app.resources.status_paused
import dev.dache.app.resources.status_provisioning
import dev.dache.app.resources.status_success
import dev.dache.app.resources.status_unknown
import dev.dache.app.resources.status_up
import dev.dache.core.model.DeployStatus
import dev.dache.core.model.MonitorStatus
import dev.dache.core.model.ServerStatus
import org.jetbrains.compose.resources.stringResource

/** Text labels for statuses, for places where a chip would be too heavy. */
@Composable
fun deployStatusLabel(status: DeployStatus?): String =
    stringResource(
        when (status) {
            DeployStatus.SUCCESS -> Res.string.status_success
            DeployStatus.BUILDING -> Res.string.status_building
            DeployStatus.FAILED -> Res.string.status_failed
            DeployStatus.CANCELLED -> Res.string.status_cancelled
            DeployStatus.UNKNOWN, null -> Res.string.status_unknown
        },
    )

@Composable
fun monitorStatusLabel(status: MonitorStatus): String =
    stringResource(
        when (status) {
            MonitorStatus.UP -> Res.string.status_up
            MonitorStatus.DOWN -> Res.string.status_down
            MonitorStatus.PAUSED -> Res.string.status_paused
            MonitorStatus.UNKNOWN -> Res.string.status_unknown
        },
    )

@Composable
fun serverStatusLabel(status: ServerStatus): String =
    stringResource(
        when (status) {
            ServerStatus.ACTIVE -> Res.string.status_active
            ServerStatus.OFF -> Res.string.status_off
            ServerStatus.PROVISIONING -> Res.string.status_provisioning
            ServerStatus.UNKNOWN -> Res.string.status_unknown
        },
    )

package dev.dache.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
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
import dev.dache.app.ui.theme.LocalStatusColors
import dev.dache.core.model.CertificateStatus
import dev.dache.core.model.DeployStatus
import dev.dache.core.model.MonitorStatus
import dev.dache.core.model.ServerStatus
import dev.dache.core.model.ServiceStatus
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** A status chip always shows an icon and a text label, never color alone. */
@Composable
fun StatusChip(
    tone: StatusTone,
    label: StringResource,
    modifier: Modifier = Modifier,
) {
    val color = tone.color()
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.12f),
        contentColor = color,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // Decorative: the label next to it carries the meaning for screen readers.
            Icon(tone.icon(), contentDescription = null, modifier = Modifier.size(16.dp))
            Text(stringResource(label), style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
fun StatusChip(
    status: DeployStatus,
    modifier: Modifier = Modifier,
) {
    val (tone, label) =
        when (status) {
            DeployStatus.SUCCESS -> StatusTone.SUCCESS to Res.string.status_success
            DeployStatus.BUILDING -> StatusTone.BUILDING to Res.string.status_building
            DeployStatus.FAILED -> StatusTone.FAILED to Res.string.status_failed
            DeployStatus.CANCELLED -> StatusTone.CANCELLED to Res.string.status_cancelled
            DeployStatus.UNKNOWN -> StatusTone.UNKNOWN to Res.string.status_unknown
        }
    StatusChip(tone, label, modifier)
}

@Composable
fun StatusChip(
    status: MonitorStatus,
    modifier: Modifier = Modifier,
) {
    val (tone, label) =
        when (status) {
            MonitorStatus.UP -> StatusTone.SUCCESS to Res.string.status_up
            MonitorStatus.DOWN -> StatusTone.FAILED to Res.string.status_down
            MonitorStatus.PAUSED -> StatusTone.CANCELLED to Res.string.status_paused
            MonitorStatus.UNKNOWN -> StatusTone.UNKNOWN to Res.string.status_unknown
        }
    StatusChip(tone, label, modifier)
}

@Composable
fun StatusChip(
    status: ServerStatus,
    modifier: Modifier = Modifier,
) {
    val (tone, label) =
        when (status) {
            ServerStatus.ACTIVE -> StatusTone.SUCCESS to Res.string.status_active
            ServerStatus.OFF -> StatusTone.CANCELLED to Res.string.status_off
            ServerStatus.PROVISIONING -> StatusTone.BUILDING to Res.string.status_provisioning
            ServerStatus.UNKNOWN -> StatusTone.UNKNOWN to Res.string.status_unknown
        }
    StatusChip(tone, label, modifier)
}

@Composable
fun StatusChip(
    status: CertificateStatus,
    modifier: Modifier = Modifier,
) {
    val tone =
        when (status) {
            CertificateStatus.ACTIVE -> StatusTone.SUCCESS
            CertificateStatus.PENDING -> StatusTone.BUILDING
            CertificateStatus.EXPIRED, CertificateStatus.FAILED -> StatusTone.FAILED
            CertificateStatus.INACTIVE -> StatusTone.CANCELLED
            CertificateStatus.UNKNOWN -> StatusTone.UNKNOWN
        }
    StatusChip(tone, certificateStatusText(status), modifier)
}

@Composable
fun StatusChip(
    status: ServiceStatus,
    modifier: Modifier = Modifier,
) {
    val tone =
        when (status) {
            ServiceStatus.HEALTHY -> StatusTone.SUCCESS
            ServiceStatus.DEGRADED -> StatusTone.BUILDING
            ServiceStatus.DOWN -> StatusTone.FAILED
            ServiceStatus.INACTIVE -> StatusTone.CANCELLED
            ServiceStatus.UNKNOWN -> StatusTone.UNKNOWN
        }
    StatusChip(tone, serviceStatusText(status), modifier)
}

@Composable
private fun StatusTone.color(): Color {
    val colors = LocalStatusColors.current
    return when (this) {
        StatusTone.SUCCESS -> colors.success
        StatusTone.BUILDING -> colors.building
        StatusTone.FAILED -> colors.failed
        StatusTone.CANCELLED -> colors.cancelled
        StatusTone.UNKNOWN -> colors.unknown
    }
}

private fun StatusTone.icon(): ImageVector =
    when (this) {
        StatusTone.SUCCESS -> Icons.Filled.CheckCircle
        StatusTone.BUILDING -> Icons.Filled.Refresh
        StatusTone.FAILED -> Icons.Filled.Warning
        StatusTone.CANCELLED -> Icons.Filled.Close
        StatusTone.UNKNOWN -> Icons.Filled.Info
    }

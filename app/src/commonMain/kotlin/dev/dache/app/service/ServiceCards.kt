package dev.dache.app.service

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Badge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.dache.app.dashboard.sectionTitle
import dev.dache.app.resources.Res
import dev.dache.app.resources.nav_problems
import dev.dache.app.resources.section_count
import dev.dache.app.resources.service_card_empty
import dev.dache.app.resources.service_card_missing_permissions
import dev.dache.core.dashboard.DashboardSummary
import dev.dache.core.model.ProviderSnapshot
import dev.dache.core.model.ServicePage
import dev.dache.core.model.ServiceResource
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Instant

/** What a page is called: the provider's title, or its one kind's section heading. */
@Composable
fun pageTitle(page: ServicePage): String =
    page.title ?: page.kinds.firstOrNull()?.let { stringResource(it.sectionTitle()) } ?: page.id

/** Problems on one page, for its sidebar badge and dashboard card. */
fun ProviderSnapshot.problemsOn(
    page: ServicePage,
    now: Instant,
): Int = DashboardSummary.from(listOf(only(page)), now).problemCount

/** One card per page of the account, each counting what is on it. Tapping a card opens the page. */
fun serviceCards(
    pages: List<ServicePage>,
    snapshot: ProviderSnapshot,
    now: Instant,
    onOpen: (ServicePage) -> Unit,
): List<@Composable () -> Unit> =
    pages.map { page ->
        { ServiceCard(page, snapshot.only(page), snapshot.problemsOn(page, now)) { onOpen(page) } }
    }

@Composable
private fun ServiceCard(
    page: ServicePage,
    shown: ProviderSnapshot,
    problems: Int,
    onClick: () -> Unit,
) {
    val kindCounts =
        shown.resources
            .filter { it !is ServiceResource }
            .groupBy { it.kind }
            .map { (kind, items) ->
                stringResource(Res.string.section_count, stringResource(kind.sectionTitle()), items.size)
            }
    val serviceCounts =
        shown.services.groupBy { it.serviceLabel }.map { (label, items) ->
            stringResource(Res.string.section_count, label, items.size)
        }
    val counts = kindCounts + serviceCounts
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    pageTitle(page),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                if (problems > 0) {
                    val description = stringResource(Res.string.nav_problems, problems)
                    Badge {
                        Text(problems.toString(), Modifier.clearAndSetSemantics { contentDescription = description })
                    }
                }
            }
            Text(
                counts.joinToString(" · ").ifEmpty { stringResource(Res.string.service_card_empty) },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (shown.missingPermissions.isNotEmpty()) {
                Text(
                    stringResource(Res.string.service_card_missing_permissions),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

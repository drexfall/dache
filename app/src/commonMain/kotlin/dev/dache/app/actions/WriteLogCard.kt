package dev.dache.app.actions

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.dache.app.resources.Res
import dev.dache.app.resources.write_log_empty
import dev.dache.app.resources.write_log_failed
import dev.dache.app.resources.write_log_title
import dev.dache.app.ui.components.relativeTime
import dev.dache.app.ui.theme.LocalStatusColors
import dev.dache.core.actions.WriteLogEntry
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Instant

/** The latest writes Dache sent, kept on this device only. */
@Composable
fun WriteLogCard(entries: List<WriteLogEntry>) {
    val status = LocalStatusColors.current
    Column {
        Text(
            stringResource(Res.string.write_log_title),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
        )
        Card {
            if (entries.isEmpty()) {
                Text(
                    stringResource(Res.string.write_log_empty),
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            entries.take(SHOWN).forEach { entry ->
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    leadingContent = {
                        Icon(
                            if (entry.ok) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                            contentDescription = null,
                            tint = if (entry.ok) status.success else status.failed,
                        )
                    },
                    headlineContent = { Text("${entry.action} · ${entry.target}") },
                    supportingContent = {
                        Text(
                            listOfNotNull(
                                relativeTime(Instant.fromEpochMilliseconds(entry.atMillis)),
                                entry.accountName,
                                entry.error?.takeUnless { entry.ok }?.let {
                                    stringResource(
                                        Res.string.write_log_failed,
                                        it,
                                    )
                                },
                            ).joinToString(" · "),
                        )
                    },
                )
            }
        }
    }
}

private const val SHOWN = 20

package dev.dache.app.dashboard

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.dache.app.ui.components.ExpandableBody
import dev.dache.app.ui.components.ExpandableHeader
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** A page section ("Activity", "Resources") whose heading folds the whole section away. */
@Composable
fun CollapsibleSection(
    title: StringResource,
    content: @Composable () -> Unit,
) {
    var expanded by rememberSaveable(title.key) { mutableStateOf(true) }
    Column {
        ExpandableHeader(expanded = expanded, onToggle = { expanded = !expanded }) {
            Text(
                stringResource(title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
        }
        ExpandableBody(expanded) { Column(Modifier.padding(top = 8.dp)) { content() } }
    }
}

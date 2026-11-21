package dev.dache.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import kotlinx.coroutines.launch

/** [text] as plain text for the system clipboard. */
expect fun plainTextClip(text: String): ClipEntry

/** Puts text on the system clipboard. */
fun interface TextCopier {
    fun copy(text: String)
}

@Composable
fun rememberTextCopier(): TextCopier {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    return remember(clipboard, scope) {
        TextCopier { text -> scope.launch { clipboard.setClipEntry(plainTextClip(text)) } }
    }
}

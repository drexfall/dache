package dev.dache.app.ui.components

import android.content.ClipData
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.toClipEntry

actual fun plainTextClip(text: String): ClipEntry = ClipData.newPlainText("", text).toClipEntry()

package dev.dache.app.ui.components

import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.ui.window.DialogProperties

// Dialogs and sheets open their own windows, so a secure flag on the activity doesn't reach them.

/** Dialog properties that keep the window out of screenshots, screen recording and recents where supported. */
expect fun secureDialogProperties(): DialogProperties

/** Bottom sheet counterpart of [secureDialogProperties]. */
expect fun secureSheetProperties(): ModalBottomSheetProperties

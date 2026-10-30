package dev.dache.app.ui.components

import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.ui.window.DialogProperties

// Desktop windows have no capture protection to opt into.
actual fun secureDialogProperties(): DialogProperties = DialogProperties()

actual fun secureSheetProperties(): ModalBottomSheetProperties = ModalBottomSheetProperties()

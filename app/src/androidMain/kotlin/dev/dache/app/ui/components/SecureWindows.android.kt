package dev.dache.app.ui.components

import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy

actual fun secureDialogProperties(): DialogProperties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn)

actual fun secureSheetProperties(): ModalBottomSheetProperties =
    ModalBottomSheetProperties(securePolicy = SecureFlagPolicy.SecureOn)

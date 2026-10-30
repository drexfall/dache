package dev.dache.android

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import dev.dache.app.DacheApp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

// A FragmentActivity because BiometricPrompt shows itself as a fragment.
class MainActivity : FragmentActivity() {
    private val container by lazy { (application as DacheApplication).container }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            container.settingsStore.appLock
                .map { it.enabled }
                .distinctUntilChanged()
                .collect(::hideFromRecents)
        }
        val authenticator = BiometricLockAuthenticator(this)
        setContent { DacheApp(container, authenticator) }
    }

    /**
     * Keeps the dashboard out of the recents thumbnail while the app lock is on. Before Android 13 the only way is
     * FLAG_SECURE, which also blocks screenshots.
     */
    private fun hideFromRecents(hide: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            setRecentsScreenshotEnabled(!hide)
        } else if (hide) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
}

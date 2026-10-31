package dev.dache.android

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationManagerCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import dev.dache.app.DacheApp
import dev.dache.app.alerts.BackgroundChecksPlatform
import dev.dache.app.alerts.NotificationAccess
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

// A FragmentActivity because BiometricPrompt shows itself as a fragment.
class MainActivity : FragmentActivity() {
    private val container by lazy { (application as DacheApplication).container }

    private var permissionAnswer: CompletableDeferred<Boolean>? = null

    // Registered before the activity starts, as the activity result API requires.
    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            permissionAnswer?.complete(granted)
        }

    private val notificationAccess =
        object : NotificationAccess {
            override fun isGranted(): Boolean =
                Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                    checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

            override suspend fun request(): Boolean {
                if (isGranted()) return NotificationManagerCompat.from(this@MainActivity).areNotificationsEnabled()
                val answer = CompletableDeferred<Boolean>()
                permissionAnswer = answer
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                return answer.await()
            }

            override fun openSettings() {
                startActivity(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, packageName),
                )
            }
        }

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
        val integrity = AndroidIntegrityChecker(applicationContext)
        val backgroundChecks = BackgroundChecksPlatform(notificationAccess)
        setContent { DacheApp(container, authenticator, integrity, backgroundChecks) }
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

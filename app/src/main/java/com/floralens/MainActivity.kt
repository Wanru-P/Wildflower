package com.floralens

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.floralens.reminders.NotificationHelper
import com.floralens.ui.navigation.FloraLensNavHost
import com.floralens.ui.theme.FloraLensTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private companion object { const val PENDING_NOTIFICATION = "pending_notification_destination" }

    /** Set from the notification's PendingIntent extra; consumed by the NavHost. */
    private val pendingDestination = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        installSplashScreen()
        pendingDestination.value = if (savedInstanceState?.containsKey(PENDING_NOTIFICATION) == true)
            savedInstanceState.getString(PENDING_NOTIFICATION)
        else intent.getStringExtra(NotificationHelper.EXTRA_NAVIGATE_TO)
        setContent {
            FloraLensTheme {
                FloraLensNavHost(
                    pendingDestination = pendingDestination.value,
                    onDestinationConsumed = {
                        pendingDestination.value = null
                        intent.removeExtra(NotificationHelper.EXTRA_NAVIGATE_TO)
                    },
                )
            }
        }
    }

    /** App already open (singleTop) — notification tap lands here instead of onCreate. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        pendingDestination.value = intent.getStringExtra(NotificationHelper.EXTRA_NAVIGATE_TO)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(PENDING_NOTIFICATION, pendingDestination.value)
        super.onSaveInstanceState(outState)
    }
}

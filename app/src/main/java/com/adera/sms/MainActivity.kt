package com.adera.sms

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.adera.sms.data.AppDatabase
import com.adera.sms.ui.navigation.AderaNavGraph
import com.adera.sms.ui.navigation.Screen
import com.adera.sms.ui.theme.AderaSmsTheme

/**
 * Single Activity — hosts the entire Compose navigation graph.
 *
 * On launch:
 *   1. Reads [AppSettings.consentGiven] from Room.
 *   2. If false → start at [Screen.Onboarding].
 *   3. If true  → start at [Screen.Main].
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            AderaSmsTheme {
                var startDest by remember { mutableStateOf<String?>(null) }

                // Determine start destination from DB before rendering NavHost
                LaunchedEffect(Unit) {
                    val db       = AppDatabase.getInstance(applicationContext)
                    val settings = db.settingsDao().getSettings()
                    startDest = if (settings?.consentGiven == true)
                        Screen.Main.route
                    else
                        Screen.Onboarding.route
                    // Track app open (mandatory, always fires)
                    com.adera.sms.analytics.AnalyticsManager.appOpen(applicationContext)
                }

                if (startDest != null) {
                    val navController = rememberNavController()
                    AderaNavGraph(
                        navController    = navController,
                        startDestination = startDest!!,
                        modifier         = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

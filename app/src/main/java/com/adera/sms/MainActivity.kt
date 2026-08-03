package com.adera.sms

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.adera.sms.data.AppDatabase
import com.adera.sms.ui.navigation.AderaNavGraph
import com.adera.sms.ui.navigation.Screen
import com.adera.sms.ui.theme.AderaSmsTheme
import kotlinx.coroutines.launch

/**
 * Single Activity — hosts the entire Compose navigation graph.
 *
 * On launch:
 *   1. Reads [AppSettings.onboardingComplete] from Room.
 *   2. If false → start at [Screen.Onboarding].
 *   3. If true  → start at [Screen.Home].
 *
 * The start destination is held in [startDest] state; the NavHost is not shown
 * until the DB read resolves (avoids a flash of the wrong screen).
 *
 * The update check is delegated to [SettingsScreen] on user action. Forced-update
 * navigation happens inside the NavGraph when SettingsViewModel detects a blocking
 * version (spec §12.6).
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
                    lifecycleScope.launch {
                        val db       = AppDatabase.getInstance(applicationContext)
                        val settings = db.settingsDao().getSettings()
                        startDest = if (settings?.onboardingComplete == true)
                            Screen.Home.route
                        else
                            Screen.Onboarding.route
                    }
                }

                if (startDest != null) {
                    val navController = rememberNavController()
                    AderaNavGraph(
                        navController    = navController,
                        startDestination = startDest!!
                    )
                }
                // While startDest is null, the window shows the splash background color
                // from themes.xml (brand_green_bg_dark) — no white flash.
            }
        }
    }
}

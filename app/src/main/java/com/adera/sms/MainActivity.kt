package com.adera.sms

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.List
import androidx.compose.material.icons.rounded.Message
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
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
 *   3. If true  → start at [Screen.Home].
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
                        Screen.Home.route
                    else
                        Screen.Onboarding.route
                    // Track app open (mandatory, always fires)
                    com.adera.sms.analytics.AnalyticsManager.appOpen(applicationContext)
                }

                if (startDest != null) {
                    val navController = rememberNavController()
                    val navBackStackEntry by navController.currentBackStackEntryAsState()
                    val currentDestination = navBackStackEntry?.destination

                    // Only show bottom bar on main destinations.
                    // Fall back to startDest when currentDestination is null (first frame
                    // before NavHost emits its first back-stack entry) so Scaffold reserves
                    // correct bottom padding from the very first composition.
                    val showBottomBar = (currentDestination?.route ?: startDest) in listOf(
                        Screen.Home.route,
                        Screen.TemplateEditor.route,
                        Screen.ActivityLog.route,
                        Screen.Settings.route
                    )

                    Scaffold(
                        bottomBar = {
                            if (showBottomBar) {
                                NavigationBar {
                                    val items = listOf(
                                        Triple(Screen.Home.route, "Home", Icons.Rounded.Home),
                                        Triple(Screen.TemplateEditor.route, "Templates", Icons.Rounded.Message),
                                        Triple(Screen.ActivityLog.route, "Recents", Icons.Rounded.List),
                                        Triple(Screen.Settings.route, "Settings", Icons.Rounded.Settings)
                                    )
                                    items.forEach { (route, label, icon) ->
                                        NavigationBarItem(
                                            icon = { Icon(icon, contentDescription = label) },
                                            label = { Text(label) },
                                            selected = currentDestination?.hierarchy?.any { it.route == route } == true,
                                            onClick = {
                                                navController.navigate(route) {
                                                    // Pop up to the start destination of the graph to
                                                    // avoid building up a large stack of destinations
                                                    popUpTo(navController.graph.findStartDestination().id) {
                                                        saveState = true
                                                    }
                                                    // Avoid multiple copies of the same destination when
                                                    // reselecting the same item
                                                    launchSingleTop = true
                                                    // Restore state when reselecting a previously selected item
                                                    restoreState = true
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    ) { innerPadding ->
                        AderaNavGraph(
                            navController    = navController,
                            startDestination = startDest!!,
                            modifier = Modifier.padding(innerPadding)
                        )
                    }
                }
            }
        }
    }
}

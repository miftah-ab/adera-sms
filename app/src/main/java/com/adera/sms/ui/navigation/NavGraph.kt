package com.adera.sms.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.navArgument
import com.adera.sms.ui.activitylog.ActivityLogScreen
import com.adera.sms.ui.home.HomeScreen
import com.adera.sms.ui.onboarding.OnboardingScreen
import com.adera.sms.ui.settings.SettingsScreen
import com.adera.sms.ui.templates.TemplateEditorScreen
import com.adera.sms.ui.update.ForceUpdateScreen

@Composable
fun AderaNavGraph(
    navController: NavHostController,
    startDestination: String,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController  = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {
        composable(Screen.Onboarding.route) {
            OnboardingScreen(
                onOnboardingComplete = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Home.route) {
            HomeScreen(
                onNavigateToTemplates = {
                    // Use the same popUpTo/saveState/restoreState pattern as the
                    // bottom navigation bar — ensures the bottom bar's selected-tab
                    // state and the actual displayed destination never diverge.
                    navController.navigate(Screen.TemplateEditor.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState    = true
                    }
                },
                onNavigateToLog = {
                    // Same pattern — keeps nav state machine in sync with bottom bar.
                    navController.navigate(Screen.ActivityLog.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState    = true
                    }
                },
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState    = true
                    }
                }
            )
        }

        composable(Screen.TemplateEditor.route) {
            TemplateEditorScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.ActivityLog.route) {
            ActivityLogScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onBack                 = { navController.popBackStack() },
                onForceUpdate          = { url ->
                    navController.navigate(Screen.ForceUpdate.buildRoute(url)) {
                        popUpTo(0) { inclusive = true }  // Clear entire back stack — cannot go back
                    }
                }
            )
        }

        composable(
            route = Screen.ForceUpdate.route,
            arguments = listOf(navArgument("downloadUrl") { type = NavType.StringType })
        ) { backStackEntry ->
            ForceUpdateScreen(
                downloadUrl = backStackEntry.arguments?.getString("downloadUrl") ?: ""
            )
        }
    }
}

package com.adera.sms.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.adera.sms.ui.activitylog.ActivityLogScreen
import com.adera.sms.ui.home.HomeScreen
import com.adera.sms.ui.onboarding.OnboardingScreen
import com.adera.sms.ui.settings.QuietHoursScreen
import com.adera.sms.ui.settings.SettingsScreen
import com.adera.sms.ui.templates.TemplateEditorScreen
import com.adera.sms.ui.update.ForceUpdateScreen

@Composable
fun AderaNavGraph(
    navController: NavHostController,
    startDestination: String
) {
    NavHost(
        navController  = navController,
        startDestination = startDestination
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
                onNavigateToTemplates  = { navController.navigate(Screen.TemplateEditor.route) },
                onNavigateToLog        = { navController.navigate(Screen.ActivityLog.route) },
                onNavigateToSettings   = { navController.navigate(Screen.Settings.route) }
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
                onNavigateToQuietHours = { navController.navigate(Screen.QuietHours.route) },
                onBack                 = { navController.popBackStack() },
                onForceUpdate          = { url ->
                    navController.navigate(Screen.ForceUpdate.buildRoute(url)) {
                        popUpTo(0) { inclusive = true }  // Clear entire back stack — cannot go back
                    }
                }
            )
        }

        composable(Screen.QuietHours.route) {
            QuietHoursScreen(
                onBack = { navController.popBackStack() }
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

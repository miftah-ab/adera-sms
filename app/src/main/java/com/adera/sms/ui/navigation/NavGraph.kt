package com.adera.sms.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.adera.sms.ui.main.MainPagerScreen
import com.adera.sms.ui.onboarding.OnboardingScreen
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
                    navController.navigate(Screen.Main.route) {
                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Main.route) {
            MainPagerScreen(
                onForceUpdate = { url ->
                    navController.navigate(Screen.ForceUpdate.buildRoute(url)) {
                        popUpTo(0) { inclusive = true }  // Clear entire back stack — cannot go back
                    }
                }
            )
        }

        // Backward compatibility alias for Home route
        composable(Screen.Home.route) {
            MainPagerScreen(
                onForceUpdate = { url ->
                    navController.navigate(Screen.ForceUpdate.buildRoute(url)) {
                        popUpTo(0) { inclusive = true }
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

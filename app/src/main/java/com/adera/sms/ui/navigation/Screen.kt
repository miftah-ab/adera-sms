package com.adera.sms.ui.navigation

/** All navigable destinations in the app. */
sealed class Screen(val route: String) {
    object Onboarding      : Screen("onboarding")
    object Main            : Screen("main")
    object Home            : Screen("home")
    object TemplateEditor  : Screen("template_editor")
    object ActivityLog     : Screen("activity_log")
    object Settings        : Screen("settings")
    object ForceUpdate     : Screen("force_update/{downloadUrl}") {
        fun buildRoute(downloadUrl: String) = "force_update/$downloadUrl"
    }
}

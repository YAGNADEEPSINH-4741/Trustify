package com.safecallai.app.ui.navigation

sealed class Screen(val route: String, val title: String) {
    object Onboarding : Screen("onboarding", "Welcome")
    object Dashboard : Screen("dashboard", "Dashboard")
    object Activity : Screen("activity", "Recent Activity")
    object Trusted : Screen("trusted", "Trusted Callers")
    object Report : Screen("report", "Report Number")
    object Settings : Screen("settings", "Settings")
    object CallerDetails : Screen("caller_details/{phoneNumber}", "Caller Details") {
        fun createRoute(phoneNumber: String): String = "caller_details/$phoneNumber"
    }
}

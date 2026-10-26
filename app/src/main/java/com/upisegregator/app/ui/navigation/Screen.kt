package com.upisegregator.app.ui.navigation

/**
 * Type-safe navigation routes for the application.
 */
sealed class Screen(val route: String) {
    data object Login : Screen("login")
    data object Signup : Screen("signup")
    data object DemoHome : Screen("demo_home")
}

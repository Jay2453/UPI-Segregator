package com.upisegregator.app.ui.navigation

/**
 * Type-safe navigation routes for the application.
 */
sealed class Screen(val route: String) {
    // Milestone 1-3: Auth and Home
    data object Login : Screen("login")
    data object Signup : Screen("signup")
    data object DemoHome : Screen("demo_home")
    data object UserHome : Screen("user_home")

    // Milestone 4: UPI Payment Flow
    data object PaymentEntry : Screen("payment_entry")
    data object UpiAppPicker : Screen("upi_app_picker/{transactionId}") {
        fun createRoute(transactionId: String) = "upi_app_picker/$transactionId"
    }
    data object PaymentReview : Screen("payment_review/{transactionId}") {
        fun createRoute(transactionId: String) = "payment_review/$transactionId"
    }
    data object PaymentOutcome : Screen("payment_outcome/{transactionId}") {
        fun createRoute(transactionId: String) = "payment_outcome/$transactionId"
    }
    data object TransactionHistory : Screen("transaction_history")
}

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

    // UPI Payment Flow (QR Scanner Experience)
    data object QrScanner : Screen("qr_scanner")
    data object QrPaymentDetails : Screen("qr_payment_details")
    data object PaymentEntry : Screen("qr_payment_details")
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

    // Manual Expense Flow
    data object AddManualExpense : Screen("add_manual_expense?expenseId={expenseId}") {
        fun createRoute(expenseId: String? = null): String =
            if (expenseId != null) "add_manual_expense?expenseId=$expenseId" else "add_manual_expense"
    }
    data object ManualExpenseHistory : Screen("manual_expense_history")
}


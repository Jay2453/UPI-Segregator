package com.upisegregator.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.upisegregator.app.ui.screens.auth.AuthViewModel
import com.upisegregator.app.ui.screens.auth.LoginScreen
import com.upisegregator.app.ui.screens.auth.SignupScreen
import com.upisegregator.app.ui.screens.home.DemoHomeScreen
import com.upisegregator.app.ui.screens.home.UserHomeScreen
import com.upisegregator.app.ui.screens.payment.PaymentEntryScreen
import com.upisegregator.app.ui.screens.payment.PaymentOutcomeScreen
import com.upisegregator.app.ui.screens.payment.PaymentReviewScreen
import com.upisegregator.app.ui.screens.payment.PaymentViewModel
import com.upisegregator.app.ui.screens.payment.TransactionHistoryScreen
import com.upisegregator.app.ui.screens.payment.UpiAppPickerScreen

@Composable
fun AppNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    authViewModel: AuthViewModel = viewModel(factory = AuthViewModel.Factory)
) {
    val session by authViewModel.sessionState.collectAsState()

    // Shared PaymentViewModel scoped to the nav graph so all payment screens share state
    val paymentViewModel: PaymentViewModel = viewModel(factory = PaymentViewModel.Factory)

    // Saveable amount paise to survive configuration changes in the payment flow
    var pendingAmountPaise by rememberSaveable { mutableLongStateOf(0L) }
    var pendingTransactionId by rememberSaveable { mutableStateOf("") }

    // Automatic session restoration on app startup
    LaunchedEffect(session.isLoggedIn, session.isDemoMode) {
        if (session.isLoggedIn && !session.isDemoMode) {
            navController.navigate(Screen.UserHome.route) {
                popUpTo(Screen.Login.route) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = Screen.Login.route,
        modifier = modifier
    ) {
        // ── Auth ──────────────────────────────────────────────────────────────
        composable(Screen.Login.route) {
            LoginScreen(
                viewModel = authViewModel,
                onNavigateToSignup = {
                    navController.navigate(Screen.Signup.route)
                },
                onNavigateToDemoHome = {
                    navController.navigate(Screen.DemoHome.route)
                },
                onLoginSuccess = {
                    navController.navigate(Screen.UserHome.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Signup.route) {
            SignupScreen(
                viewModel = authViewModel,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onSignupSuccess = {
                    navController.navigate(Screen.UserHome.route) {
                        popUpTo(Screen.Signup.route) { inclusive = true }
                    }
                }
            )
        }

        // ── Demo Home ─────────────────────────────────────────────────────────
        composable(Screen.DemoHome.route) {
            DemoHomeScreen(
                onExitDemo = {
                    authViewModel.logout {
                        navController.navigate(Screen.Login.route) {
                            popUpTo(Screen.DemoHome.route) { inclusive = true }
                        }
                    }
                },
                onViewHistory = {
                    navController.navigate(Screen.TransactionHistory.route)
                }
            )
        }

        // ── User Home ─────────────────────────────────────────────────────────
        composable(Screen.UserHome.route) {
            UserHomeScreen(
                onLogout = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.UserHome.route) { inclusive = true }
                    }
                },
                onStartPayment = {
                    navController.navigate(Screen.PaymentEntry.route)
                },
                onViewHistory = {
                    navController.navigate(Screen.TransactionHistory.route)
                }
            )
        }

        // ── Milestone 4: UPI Payment Flow ─────────────────────────────────────

        composable(Screen.PaymentEntry.route) {
            PaymentEntryScreen(
                viewModel = paymentViewModel,
                onNavigateBack = { navController.popBackStack() },
                onContinue = { amountPaise ->
                    pendingAmountPaise = amountPaise
                    navController.navigate(Screen.UpiAppPicker.createRoute("pending"))
                }
            )
        }

        composable(Screen.UpiAppPicker.route) {
            UpiAppPickerScreen(
                viewModel = paymentViewModel,
                amountPaise = pendingAmountPaise,
                onNavigateBack = { navController.popBackStack() },
                onContinue = {
                    navController.navigate(Screen.PaymentReview.createRoute("pending"))
                }
            )
        }

        composable(Screen.PaymentReview.route) { backStackEntry ->
            PaymentReviewScreen(
                viewModel = paymentViewModel,
                transactionId = "pending",
                onNavigateBack = { navController.popBackStack() },
                onPaymentLaunched = { transactionId ->
                    pendingTransactionId = transactionId
                    navController.navigate(Screen.PaymentOutcome.createRoute(transactionId)) {
                        // Pop the review screen so back button doesn't re-launch the UPI app
                        popUpTo(Screen.PaymentReview.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.PaymentOutcome.route) { backStackEntry ->
            val argId = backStackEntry.arguments?.getString("transactionId")
            val txnId = if (!argId.isNullOrBlank() && argId != "{transactionId}" && argId != "pending") {
                argId
            } else {
                pendingTransactionId
            }
            PaymentOutcomeScreen(
                viewModel = paymentViewModel,
                transactionId = txnId,
                onDone = {
                    navController.navigate(Screen.UserHome.route) {
                        popUpTo(Screen.PaymentEntry.route) { inclusive = true }
                    }
                }
            )
        }

        // ── Transaction History ────────────────────────────────────────────────
        composable(Screen.TransactionHistory.route) {
            TransactionHistoryScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}

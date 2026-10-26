package com.upisegregator.app.ui.navigation

import androidx.compose.runtime.Composable
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

@Composable
fun AppNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    authViewModel: AuthViewModel = viewModel()
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Login.route,
        modifier = modifier
    ) {
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
                    navController.navigate(Screen.DemoHome.route) {
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
                    navController.navigate(Screen.DemoHome.route) {
                        popUpTo(Screen.Signup.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.DemoHome.route) {
            DemoHomeScreen(
                onExitDemo = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.DemoHome.route) { inclusive = true }
                    }
                }
            )
        }
    }
}

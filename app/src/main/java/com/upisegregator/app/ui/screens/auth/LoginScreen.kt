package com.upisegregator.app.ui.screens.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.upisegregator.app.ui.components.AppTextField
import com.upisegregator.app.ui.components.DemoEntryCard
import com.upisegregator.app.ui.components.DemoModeBanner
import com.upisegregator.app.ui.components.PrimaryButton
import com.upisegregator.app.ui.theme.BackgroundWhite
import com.upisegregator.app.ui.theme.BorderLight
import com.upisegregator.app.ui.theme.PillShape
import com.upisegregator.app.ui.theme.PrimaryBlue
import com.upisegregator.app.ui.theme.TextMuted
import com.upisegregator.app.ui.theme.TextPrimary
import com.upisegregator.app.ui.theme.TextSecondary

@Composable
fun LoginScreen(
    viewModel: AuthViewModel,
    onNavigateToSignup: () -> Unit,
    onNavigateToDemoHome: () -> Unit,
    onLoginSuccess: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.loginState.collectAsState()
    val scrollState = rememberScrollState()

    if (state.showForgotPasswordDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.showForgotPasswordDialog(false) },
            icon = {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = PrimaryBlue,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = {
                Text(
                    text = "Local-Only Account Recovery",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            },
            text = {
                Text(
                    text = "UPI Segregator runs locally on this device with no remote cloud servers or email delivery backends.\n\nWe cannot send password reset links. Please sign in with the local password chosen during account creation, or sign up with a new local profile.",
                    style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary, lineHeight = 20.sp)
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.showForgotPasswordDialog(false) },
                    colors = ButtonDefaults.textButtonColors(contentColor = PrimaryBlue)
                ) {
                    Text(text = "Understood", fontWeight = FontWeight.Bold)
                }
            },
            containerColor = BackgroundWhite,
            shape = MaterialTheme.shapes.medium
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundWhite)
            .verticalScroll(scrollState)
            .padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        // App Logo & Brand Header
        Box(
            modifier = Modifier
                .size(68.dp)
                .clip(PillShape)
                .background(PrimaryBlue),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Payments,
                contentDescription = "UPI Segregator Logo",
                tint = Color.White,
                modifier = Modifier.size(36.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "UPI Segregator",
            style = MaterialTheme.typography.headlineLarge.copy(
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Track whom you pay, how much, and where it goes",
            style = MaterialTheme.typography.bodyMedium.copy(
                color = TextSecondary,
                textAlign = TextAlign.Center
            ),
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(28.dp))

        // Demo Mode Quick Entry Card
        DemoEntryCard(
            onEnterDemo = {
                viewModel.enterDemoMode(onSuccess = onNavigateToDemoHome)
            }
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Visual Separator
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HorizontalDivider(
                modifier = Modifier.weight(1f),
                color = BorderLight
            )
            Text(
                text = "OR SIGN IN",
                style = MaterialTheme.typography.labelMedium.copy(
                    color = TextMuted,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp
                ),
                modifier = Modifier.padding(horizontal = 12.dp)
            )
            HorizontalDivider(
                modifier = Modifier.weight(1f),
                color = BorderLight
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // General feedback message banner (e.g. error)
        if (state.generalMessage != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(PillShape)
                    .background(Color(0xFFFEE2E2))
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text(
                    text = state.generalMessage ?: "",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = Color(0xFF991B1B),
                        fontWeight = FontWeight.Medium
                    ),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        // Login Form Fields
        AppTextField(
            value = state.email,
            onValueChange = viewModel::onLoginEmailChange,
            label = "Email Address",
            placeholder = "name@example.com",
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Email,
                    contentDescription = null,
                    tint = TextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            },
            errorMessage = state.emailError,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next
            )
        )

        Spacer(modifier = Modifier.height(14.dp))

        AppTextField(
            value = state.password,
            onValueChange = viewModel::onLoginPasswordChange,
            label = "Password",
            placeholder = "Enter your password",
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = TextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            },
            trailingIcon = {
                IconButton(onClick = viewModel::toggleLoginPasswordVisibility) {
                    Icon(
                        imageVector = if (state.isPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = "Toggle password visibility",
                        tint = TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            },
            errorMessage = state.passwordError,
            visualTransformation = if (state.isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            )
        )

        // Forgot password row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(
                onClick = { viewModel.showForgotPasswordDialog(true) },
                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
            ) {
                Text(
                    text = "Forgot Password?",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = PrimaryBlue,
                        fontWeight = FontWeight.SemiBold
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Primary Blue Login Button
        PrimaryButton(
            text = "Sign In",
            onClick = { viewModel.performLogin(onSuccess = onLoginSuccess) },
            isLoading = state.isLoading
        )

        Spacer(modifier = Modifier.height(18.dp))

        // Signup Navigation Link
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Don't have an account?",
                style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary)
            )
            TextButton(onClick = onNavigateToSignup) {
                Text(
                    text = "Sign Up",
                    style = MaterialTheme.typography.labelLarge.copy(
                        color = PrimaryBlue,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        DemoModeBanner()

        Spacer(modifier = Modifier.height(16.dp))
    }
}

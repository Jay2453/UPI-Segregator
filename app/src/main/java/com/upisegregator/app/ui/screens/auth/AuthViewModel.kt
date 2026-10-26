package com.upisegregator.app.ui.screens.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AuthViewModel : ViewModel() {

    private val _loginState = MutableStateFlow(LoginUiState())
    val loginState: StateFlow<LoginUiState> = _loginState.asStateFlow()

    private val _signupState = MutableStateFlow(SignupUiState())
    val signupState: StateFlow<SignupUiState> = _signupState.asStateFlow()

    fun onLoginEmailChange(value: String) {
        _loginState.update { it.copy(email = value, emailError = null, generalMessage = null) }
    }

    fun onLoginPasswordChange(value: String) {
        _loginState.update { it.copy(password = value, passwordError = null, generalMessage = null) }
    }

    fun toggleLoginPasswordVisibility() {
        _loginState.update { it.copy(isPasswordVisible = !it.isPasswordVisible) }
    }

    fun onSignupFullNameChange(value: String) {
        _signupState.update { it.copy(fullName = value, fullNameError = null, generalMessage = null) }
    }

    fun onSignupEmailChange(value: String) {
        _signupState.update { it.copy(email = value, emailError = null, generalMessage = null) }
    }

    fun onSignupPasswordChange(value: String) {
        _signupState.update { it.copy(password = value, passwordError = null, generalMessage = null) }
    }

    fun onSignupConfirmPasswordChange(value: String) {
        _signupState.update { it.copy(confirmPassword = value, confirmPasswordError = null, generalMessage = null) }
    }

    fun toggleSignupPasswordVisibility() {
        _signupState.update { it.copy(isPasswordVisible = !it.isPasswordVisible) }
    }

    /**
     * Prototype login handler.
     * Validates input format and provides clear local prototype feedback.
     */
    fun performLogin(onSuccess: () -> Unit) {
        val current = _loginState.value
        var hasError = false

        if (current.email.isBlank() || !current.email.contains("@")) {
            _loginState.update { it.copy(emailError = "Please enter a valid email address") }
            hasError = true
        }

        if (current.password.length < 6) {
            _loginState.update { it.copy(passwordError = "Password must be at least 6 characters") }
            hasError = true
        }

        if (hasError) return

        viewModelScope.launch {
            _loginState.update { it.copy(isLoading = true, generalMessage = null) }
            // Simulating local prototype verification
            delay(600)
            _loginState.update {
                it.copy(
                    isLoading = false,
                    generalMessage = "Signed in as prototype user. You can also explore full features in Demo Mode."
                )
            }
            onSuccess()
        }
    }

    /**
     * Prototype signup handler.
     */
    fun performSignup(onSuccess: () -> Unit) {
        val current = _signupState.value
        var hasError = false

        if (current.fullName.trim().length < 2) {
            _signupState.update { it.copy(fullNameError = "Please enter your name") }
            hasError = true
        }

        if (current.email.isBlank() || !current.email.contains("@")) {
            _signupState.update { it.copy(emailError = "Please enter a valid email address") }
            hasError = true
        }

        if (current.password.length < 6) {
            _signupState.update { it.copy(passwordError = "Password must be at least 6 characters") }
            hasError = true
        }

        if (current.confirmPassword != current.password) {
            _signupState.update { it.copy(confirmPasswordError = "Passwords do not match") }
            hasError = true
        }

        if (hasError) return

        viewModelScope.launch {
            _signupState.update { it.copy(isLoading = true, generalMessage = null) }
            delay(600)
            _signupState.update {
                it.copy(
                    isLoading = false,
                    generalMessage = "Account registered (local prototype). Proceeding..."
                )
            }
            onSuccess()
        }
    }
}

package com.upisegregator.app.ui.screens.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.upisegregator.app.UpiSegregatorApp
import com.upisegregator.app.data.demo.DemoDataManager
import com.upisegregator.app.data.repository.UserRepository
import com.upisegregator.app.data.session.SessionManager
import com.upisegregator.app.data.session.UserSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AuthViewModel(
    private val userRepository: UserRepository,
    private val sessionManager: SessionManager,
    private val demoDataManager: DemoDataManager
) : ViewModel() {

    private val _loginState = MutableStateFlow(LoginUiState())
    val loginState: StateFlow<LoginUiState> = _loginState.asStateFlow()

    private val _signupState = MutableStateFlow(SignupUiState())
    val signupState: StateFlow<SignupUiState> = _signupState.asStateFlow()

    val sessionState: StateFlow<UserSession> = sessionManager.sessionFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = UserSession()
        )

    fun onLoginEmailChange(value: String) {
        _loginState.update { it.copy(email = value, emailError = null, generalMessage = null) }
    }

    fun onLoginPasswordChange(value: String) {
        _loginState.update { it.copy(password = value, passwordError = null, generalMessage = null) }
    }

    fun toggleLoginPasswordVisibility() {
        _loginState.update { it.copy(isPasswordVisible = !it.isPasswordVisible) }
    }

    fun showForgotPasswordDialog(show: Boolean) {
        _loginState.update { it.copy(showForgotPasswordDialog = show) }
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
     * Authenticates the user against local Room storage using salted PBKDF2.
     */
    fun performLogin(onSuccess: () -> Unit) {
        val current = _loginState.value
        if (current.isLoading) return

        var hasError = false

        if (current.email.isBlank() || !current.email.contains("@") || !current.email.contains(".")) {
            _loginState.update { it.copy(emailError = "Please enter a valid email address") }
            hasError = true
        }

        if (current.password.isBlank()) {
            _loginState.update { it.copy(passwordError = "Password cannot be empty") }
            hasError = true
        } else if (current.password.length < 6) {
            _loginState.update { it.copy(passwordError = "Password must be at least 6 characters") }
            hasError = true
        }

        if (hasError) return

        viewModelScope.launch {
            _loginState.update { it.copy(isLoading = true, generalMessage = null) }

            val result = userRepository.authenticateUser(
                email = current.email,
                passwordPlain = current.password
            )

            result.onSuccess { user ->
                sessionManager.startUserSession(user)
                _loginState.update { it.copy(isLoading = false, password = "", emailError = null, passwordError = null) }
                onSuccess()
            }.onFailure { error ->
                val message = error.message ?: "Authentication failed"
                _loginState.update {
                    it.copy(
                        isLoading = false,
                        generalMessage = message,
                        passwordError = if (message.contains("password", ignoreCase = true)) message else null,
                        emailError = if (message.contains("email", ignoreCase = true) || message.contains("account", ignoreCase = true)) message else null
                    )
                }
            }
        }
    }

    /**
     * Registers a new local profile and starts a session.
     */
    fun performSignup(onSuccess: () -> Unit) {
        val current = _signupState.value
        if (current.isLoading) return

        var hasError = false

        if (current.fullName.trim().length < 2) {
            _signupState.update { it.copy(fullNameError = "Please enter your full name (at least 2 characters)") }
            hasError = true
        }

        if (current.email.isBlank() || !current.email.contains("@") || !current.email.contains(".")) {
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

            val result = userRepository.registerUser(
                fullName = current.fullName,
                email = current.email,
                passwordPlain = current.password
            )

            result.onSuccess { user ->
                sessionManager.startUserSession(user)
                _signupState.update {
                    it.copy(
                        isLoading = false,
                        password = "",
                        confirmPassword = ""
                    )
                }
                onSuccess()
            }.onFailure { error ->
                val message = error.message ?: "Registration failed"
                _signupState.update {
                    it.copy(
                        isLoading = false,
                        generalMessage = message,
                        emailError = if (message.contains("email", ignoreCase = true)) message else null
                    )
                }
            }
        }
    }

    /**
     * Enters Demo Mode without registering an account.
     */
    fun enterDemoMode(onSuccess: () -> Unit) {
        viewModelScope.launch {
            sessionManager.startDemoSession()
            demoDataManager.seedDemoDataIfNotPresent()
            onSuccess()
        }
    }

    /**
     * Clears session and returns to login screen.
     */
    fun logout(onSuccess: () -> Unit) {
        viewModelScope.launch {
            sessionManager.clearSession()
            onSuccess()
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as UpiSegregatorApp
                AuthViewModel(
                    userRepository = app.container.userRepository,
                    sessionManager = app.container.sessionManager,
                    demoDataManager = app.container.demoDataManager
                )
            }
        }
    }
}

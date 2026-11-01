package com.upisegregator.app.ui.screens.manualexpense

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.upisegregator.app.UpiSegregatorApp
import com.upisegregator.app.data.local.entity.ManualExpenseEntity
import com.upisegregator.app.data.local.entity.PaymentMethod
import com.upisegregator.app.data.repository.ManualExpenseRepository
import com.upisegregator.app.data.session.SessionManager
import com.upisegregator.app.data.session.UserSession
import com.upisegregator.app.util.PaiseUtils
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Standard categories required for Manual Expense logging.
 */
val MANUAL_EXPENSE_CATEGORIES = listOf(
    "Food",
    "Transport",
    "Shopping",
    "Education",
    "Entertainment",
    "Groceries",
    "Bills",
    "Health",
    "Other"
)

data class ManualExpenseFormUiState(
    val amountText: String = "",
    val recipientName: String = "",
    val category: String = "Food",
    val description: String = "",
    val paymentMethod: PaymentMethod = PaymentMethod.CASH,
    val expenseDate: Long = System.currentTimeMillis(),
    val amountError: String? = null,
    val recipientError: String? = null,
    val generalError: String? = null,
    val isSubmitting: Boolean = false,
    val isEditing: Boolean = false,
    val editingExpenseId: String? = null
)

data class ManualExpenseHistoryUiState(
    val expenses: List<ManualExpenseEntity> = emptyList(),
    val isLoading: Boolean = false,
    val expenseToDelete: ManualExpenseEntity? = null,
    val isDeleting: Boolean = false,
    val error: String? = null
)

data class ManualExpenseDashboardTotals(
    val todaySpendingPaise: Long = 0L,
    val monthSpendingPaise: Long = 0L,
    val totalSpendingPaise: Long = 0L
)

@OptIn(ExperimentalCoroutinesApi::class)
class ManualExpenseViewModel(
    private val sessionManager: SessionManager,
    private val manualExpenseRepository: ManualExpenseRepository
) : ViewModel() {

    val sessionState: StateFlow<UserSession> = sessionManager.sessionFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = UserSession()
        )

    // ── Form State ───────────────────────────────────────────────────────────

    private val _formState = MutableStateFlow(ManualExpenseFormUiState())
    val formState: StateFlow<ManualExpenseFormUiState> = _formState.asStateFlow()

    fun onAmountChange(v: String) {
        _formState.update { it.copy(amountText = v, amountError = null, generalError = null) }
    }

    fun onRecipientChange(v: String) {
        _formState.update { it.copy(recipientName = v, recipientError = null, generalError = null) }
    }

    fun onCategoryChange(v: String) {
        _formState.update { it.copy(category = v) }
    }

    fun onDescriptionChange(v: String) {
        _formState.update { it.copy(description = v) }
    }

    fun onPaymentMethodChange(v: PaymentMethod) {
        _formState.update { it.copy(paymentMethod = v) }
    }

    fun onDateChange(v: Long) {
        _formState.update { it.copy(expenseDate = v) }
    }

    fun resetForm() {
        _formState.value = ManualExpenseFormUiState(
            expenseDate = System.currentTimeMillis()
        )
    }

    /**
     * Loads an existing expense into the form for editing.
     */
    fun loadExpenseForEdit(expenseId: String) {
        viewModelScope.launch {
            val session = sessionManager.sessionFlow.first()
            val userId = if (session.isDemoMode) "demo_user" else (session.userId ?: "demo_user")
            val existing = manualExpenseRepository.getExpenseById(expenseId, userId)
            if (existing != null) {
                _formState.value = ManualExpenseFormUiState(
                    amountText = PaiseUtils.paiseToRupeesString(existing.amountPaise),
                    recipientName = existing.recipientName,
                    category = existing.category,
                    description = existing.description,
                    paymentMethod = existing.paymentMethod,
                    expenseDate = existing.expenseDate,
                    isEditing = true,
                    editingExpenseId = existing.id
                )
            }
        }
    }

    /**
     * Validates and saves the manual expense (insert or update).
     */
    fun saveExpense(onSuccess: () -> Unit) {
        val current = _formState.value
        var valid = true

        if (current.recipientName.trim().length < 2) {
            _formState.update { it.copy(recipientError = "Recipient name must be at least 2 characters") }
            valid = false
        }

        val paise = PaiseUtils.rupeesToPaiseOrNull(current.amountText)
        if (paise == null || paise <= 0L) {
            _formState.update { it.copy(amountError = "Enter a valid amount greater than ₹0") }
            valid = false
        }

        if (!valid || paise == null) return

        _formState.update { it.copy(isSubmitting = true, generalError = null) }

        viewModelScope.launch {
            try {
                val session = sessionManager.sessionFlow.first()
                val userId = if (session.isDemoMode) "demo_user" else (session.userId ?: "demo_user")

                if (current.isEditing && current.editingExpenseId != null) {
                    val existing = manualExpenseRepository.getExpenseById(current.editingExpenseId, userId)
                    if (existing != null) {
                        val updated = existing.copy(
                            amountPaise = paise,
                            recipientName = current.recipientName.trim(),
                            category = current.category,
                            description = current.description.trim(),
                            paymentMethod = current.paymentMethod,
                            expenseDate = current.expenseDate
                        )
                        manualExpenseRepository.updateExpense(updated)
                    }
                } else {
                    val newExpense = ManualExpenseEntity(
                        id = UUID.randomUUID().toString(),
                        amountPaise = paise,
                        recipientName = current.recipientName.trim(),
                        category = current.category,
                        description = current.description.trim(),
                        paymentMethod = current.paymentMethod,
                        expenseDate = current.expenseDate,
                        userId = userId,
                        createdAt = System.currentTimeMillis()
                    )
                    manualExpenseRepository.insertExpense(newExpense)
                }

                _formState.update { it.copy(isSubmitting = false) }
                onSuccess()
            } catch (e: Exception) {
                _formState.update { it.copy(isSubmitting = false, generalError = "Failed to save: ${e.message}") }
            }
        }
    }

    // ── History & Deletion State ─────────────────────────────────────────────

    val expensesFlow: Flow<List<ManualExpenseEntity>> = sessionManager.sessionFlow
        .flatMapLatest { session ->
            val userId = if (session.isDemoMode) "demo_user" else (session.userId ?: "demo_user")
            manualExpenseRepository.getExpensesByUserFlow(userId)
        }

    val expensesListFlow: StateFlow<List<ManualExpenseEntity>> = expensesFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _historyState = MutableStateFlow(ManualExpenseHistoryUiState())
    val historyState: StateFlow<ManualExpenseHistoryUiState> = _historyState.asStateFlow()

    fun requestDelete(expense: ManualExpenseEntity) {
        _historyState.update { it.copy(expenseToDelete = expense, error = null) }
    }

    fun dismissDelete() {
        _historyState.update { it.copy(expenseToDelete = null) }
    }

    val deleteConfirmationExpense: StateFlow<ManualExpenseEntity?> = _historyState
        .map { it.expenseToDelete }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    fun confirmDelete() {
        val toDelete = _historyState.value.expenseToDelete ?: return
        _historyState.update { it.copy(isDeleting = true) }
        viewModelScope.launch {
            try {
                manualExpenseRepository.deleteExpense(toDelete)
                _historyState.update { it.copy(expenseToDelete = null, isDeleting = false) }
            } catch (e: Exception) {
                _historyState.update { it.copy(isDeleting = false, error = "Failed to delete: ${e.message}") }
            }
        }
    }

    // ── Dashboard Totals ─────────────────────────────────────────────────────

    val todaySpendingFlow: Flow<Long> = sessionManager.sessionFlow
        .flatMapLatest { session ->
            val userId = if (session.isDemoMode) "demo_user" else (session.userId ?: "demo_user")
            manualExpenseRepository.getDailySpendingByUserFlow(userId)
        }

    val todaySpendingState: StateFlow<Long> = todaySpendingFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0L
        )

    val monthSpendingFlow: Flow<Long> = sessionManager.sessionFlow
        .flatMapLatest { session ->
            val userId = if (session.isDemoMode) "demo_user" else (session.userId ?: "demo_user")
            manualExpenseRepository.getMonthlySpendingByUserFlow(userId)
        }

    val monthSpendingState: StateFlow<Long> = monthSpendingFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0L
        )

    val totalSpendingFlow: Flow<Long> = sessionManager.sessionFlow
        .flatMapLatest { session ->
            val userId = if (session.isDemoMode) "demo_user" else (session.userId ?: "demo_user")
            manualExpenseRepository.getTotalSpendingByUserFlow(userId)
        }

    val totalSpendingState: StateFlow<Long> = totalSpendingFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0L
        )

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as UpiSegregatorApp
                ManualExpenseViewModel(
                    sessionManager = app.container.sessionManager,
                    manualExpenseRepository = app.container.manualExpenseRepository
                )
            }
        }
    }
}

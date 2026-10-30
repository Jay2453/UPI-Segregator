package com.upisegregator.app.ui.screens.payment

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.upisegregator.app.UpiSegregatorApp
import com.upisegregator.app.data.local.entity.PaymentMethod
import com.upisegregator.app.data.local.entity.RecipientEntity
import com.upisegregator.app.data.local.entity.TransactionEntity
import com.upisegregator.app.data.local.entity.TransactionSource
import com.upisegregator.app.data.local.entity.TransactionStatus
import com.upisegregator.app.data.repository.RecipientRepository
import com.upisegregator.app.data.repository.TransactionRepository
import com.upisegregator.app.data.session.SessionManager
import com.upisegregator.app.data.session.UserSession
import com.upisegregator.app.data.upi.UpiApp
import com.upisegregator.app.data.upi.UpiAppPreferenceManager
import com.upisegregator.app.data.upi.UpiIdValidation
import com.upisegregator.app.data.upi.UpiIntentBuilder
import com.upisegregator.app.data.upi.UpiLaunchResult
import com.upisegregator.app.data.upi.UpiPaymentResponse
import com.upisegregator.app.data.upi.UpiResponseParser
import com.upisegregator.app.data.upi.UpiResponseStatus
import com.upisegregator.app.util.PaiseUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

// ─────────────────────────────────────────────────────────────────────────────
// UI State models
// ─────────────────────────────────────────────────────────────────────────────

data class PaymentEntryUiState(
    val recipientName: String = "",
    val recipientNameError: String? = null,
    val upiId: String = "",
    val upiIdError: String? = null,
    val amountText: String = "",
    val amountError: String? = null,
    val note: String = "",
    val isLoading: Boolean = false,
    val generalError: String? = null
)

data class UpiAppPickerUiState(
    val availableApps: List<UpiApp> = emptyList(),
    val selectedApp: UpiApp? = null,
    val preferredPackage: String? = null,
    val isLoading: Boolean = true,
    val noAppsFound: Boolean = false
)

data class PaymentReviewUiState(
    val recipientName: String = "",
    val upiId: String = "",
    val amountPaise: Long = 0L,
    val note: String = "",
    val selectedApp: UpiApp? = null,
    val transactionId: String = "",
    val isLaunching: Boolean = false,
    val error: String? = null
)

data class PaymentOutcomeUiState(
    val transactionId: String = "",
    val recipientName: String = "",
    val amountPaise: Long = 0L,
    val note: String = "",
    val selectedOutcome: TransactionStatus? = null,
    val detectedStatus: UpiResponseStatus? = null,
    val detectedMessage: String? = null,
    val externalReference: String? = null,
    val isFound: Boolean = true,
    val isLoading: Boolean = false,
    val isSubmitting: Boolean = false,
    val isConfirmed: Boolean = false,
    val error: String? = null
)

// ─────────────────────────────────────────────────────────────────────────────
// ViewModel
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Shared ViewModel for the entire UPI payment flow:
 * PaymentEntry -> UpiAppPicker -> PaymentReview -> PaymentOutcome.
 *
 * FINANCIAL INTEGRITY RULES:
 * - Payment is created in Room as PENDING *before* launching the external app.
 * - SUCCESS is NEVER set automatically — only when the user explicitly confirms.
 * - Transaction IDs are UUID v4 — duplicates are impossible.
 * - Demo Mode cannot initiate real payments.
 * - All amounts are Long (paise) — never Float or Double.
 */
class PaymentViewModel(
    private val sessionManager: SessionManager,
    private val transactionRepository: TransactionRepository,
    private val recipientRepository: RecipientRepository,
    private val upiPrefs: UpiAppPreferenceManager
) : ViewModel() {

    val sessionState: StateFlow<UserSession> = sessionManager.sessionFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = UserSession()
        )

    // ── Payment Entry State ──────────────────────────────────────────────────

    private val _entryState = MutableStateFlow(PaymentEntryUiState())
    val entryState: StateFlow<PaymentEntryUiState> = _entryState.asStateFlow()

    fun onRecipientNameChange(v: String) =
        _entryState.update { it.copy(recipientName = v, recipientNameError = null, generalError = null) }

    fun onUpiIdChange(v: String) =
        _entryState.update { it.copy(upiId = v, upiIdError = null, generalError = null) }

    fun onAmountChange(v: String) =
        _entryState.update { it.copy(amountText = v, amountError = null, generalError = null) }

    fun onNoteChange(v: String) =
        _entryState.update { it.copy(note = v) }

    /**
     * Validates all payment entry fields and returns the validated amount in paise,
     * or null if validation failed.
     */
    fun validateAndGetPaise(): Long? {
        val state = _entryState.value
        var valid = true

        if (state.recipientName.trim().length < 2) {
            _entryState.update { it.copy(recipientNameError = "Recipient name must be at least 2 characters") }
            valid = false
        }

        when (val v = UpiIntentBuilder.validateUpiId(state.upiId)) {
            is UpiIdValidation.Valid -> { /* ok */ }
            is UpiIdValidation.Invalid -> {
                _entryState.update { it.copy(upiIdError = v.reason) }
                valid = false
            }
        }

        val amountPaise = PaiseUtils.rupeesToPaiseOrNull(state.amountText)
        if (amountPaise == null || amountPaise <= 0L) {
            _entryState.update { it.copy(amountError = "Enter a valid amount greater than ₹0") }
            valid = false
        }

        return if (valid) amountPaise else null
    }

    // ── UPI App Picker State ─────────────────────────────────────────────────

    private val _pickerState = MutableStateFlow(UpiAppPickerUiState())
    val pickerState: StateFlow<UpiAppPickerUiState> = _pickerState.asStateFlow()

    fun loadUpiApps(context: Context) {
        viewModelScope.launch {
            _pickerState.update { it.copy(isLoading = true) }
            val apps = UpiIntentBuilder.getInstalledUpiApps(context)
            val preferred = upiPrefs.getValidatedPreferredPackage()
            val preSelectedApp = apps.firstOrNull { it.packageName == preferred } ?: apps.firstOrNull()
            _pickerState.update {
                it.copy(
                    availableApps = apps,
                    selectedApp = preSelectedApp,
                    preferredPackage = preferred,
                    isLoading = false,
                    noAppsFound = apps.isEmpty()
                )
            }
        }
    }

    fun onUpiAppSelected(app: UpiApp) {
        _pickerState.update { it.copy(selectedApp = app) }
    }

    fun savePreferredApp(app: UpiApp) {
        viewModelScope.launch {
            upiPrefs.savePreferredUpiApp(app.packageName)
            _pickerState.update { it.copy(preferredPackage = app.packageName) }
        }
    }

    // ── Payment Review State ─────────────────────────────────────────────────

    private val _reviewState = MutableStateFlow(PaymentReviewUiState())
    val reviewState: StateFlow<PaymentReviewUiState> = _reviewState.asStateFlow()

    /**
     * Prepares the review screen with validated details from the entry and picker screens.
     * Does NOT yet write to Room — that happens in [createPendingAndLaunch].
     */
    fun prepareReview(amountPaise: Long) {
        val entry = _entryState.value
        val picker = _pickerState.value
        _reviewState.update {
            it.copy(
                recipientName = entry.recipientName.trim(),
                upiId = entry.upiId.trim(),
                amountPaise = amountPaise,
                note = entry.note.trim(),
                selectedApp = picker.selectedApp,
                error = null
            )
        }
    }

    /**
     * Creates a PENDING transaction record in Room, then builds and returns the launch intent.
     *
     * CRITICAL ORDER OF OPERATIONS:
     * 1. Insert recipient into Room (or reuse existing).
     * 2. Insert PENDING transaction into Room with generated UUID.
     * 3. Persist active transaction ID to SessionManager.
     * 4. Return the launch intent along with the exact transactionId.
     */
    fun createPendingAndLaunch(
        context: Context,
        onLaunch: (intent: Intent, transactionId: String) -> Unit,
        onNoApp: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            val session = sessionManager.sessionFlow.first()

            // Block real payments in Demo Mode
            if (session.isDemoMode) {
                onError("Demo Mode cannot initiate real payments. Sign up or log in to use UPI payments.")
                return@launch
            }

            val userId = session.userId ?: run {
                onError("You must be logged in to make a payment.")
                return@launch
            }

            if (_reviewState.value.isLaunching) return@launch // Prevent duplicate taps
            _reviewState.update { it.copy(isLaunching = true, error = null) }

            try {
                val review = _reviewState.value

                // 1. Upsert recipient
                val recipientId = UUID.randomUUID().toString()
                val recipient = RecipientEntity(
                    recipientId = recipientId,
                    recipientName = review.recipientName,
                    upiId = review.upiId,
                    userId = userId
                )
                recipientRepository.insertRecipient(recipient)

                // 2. Create PENDING transaction BEFORE launching external app
                val transactionId = UUID.randomUUID().toString()
                val now = System.currentTimeMillis()
                val transaction = TransactionEntity(
                    transactionId = transactionId,
                    amountPaise = review.amountPaise,
                    recipientId = recipientId,
                    recipientName = review.recipientName,
                    paymentMethod = PaymentMethod.UPI,
                    category = "UPI Payment",
                    note = review.note,
                    transactionDate = now,
                    transactionStatus = TransactionStatus.PENDING,
                    transactionSource = TransactionSource.UPI_INTENT,
                    externalReference = null,
                    userId = userId,
                    createdAt = now
                )
                transactionRepository.insertTransaction(transaction)

                // Persist active transaction ID across process recreation
                sessionManager.setActivePaymentTransactionId(transactionId)

                // Store transactionId in review state
                _reviewState.update { it.copy(transactionId = transactionId) }

                // 3. Build URI and resolve launch
                val uri = UpiIntentBuilder.buildUpiUri(
                    payeeUpiId = review.upiId,
                    payeeName = review.recipientName,
                    amountPaise = review.amountPaise,
                    note = review.note
                )

                val targetPackage = review.selectedApp?.packageName
                    ?: upiPrefs.getValidatedPreferredPackage()
                when (val result = UpiIntentBuilder.resolvePaymentLaunch(context, uri, targetPackage)) {
                    is UpiLaunchResult.Success -> onLaunch(result.intent, transactionId)
                    is UpiLaunchResult.Fallback -> onLaunch(result.chooserIntent, transactionId)
                    is UpiLaunchResult.NoAppAvailable -> onNoApp()
                    is UpiLaunchResult.Error -> onError(result.message)
                }

                _reviewState.update { it.copy(isLaunching = false) }

            } catch (e: Exception) {
                _reviewState.update { it.copy(isLaunching = false, error = "Payment setup failed: ${e.message}") }
            }
        }
    }

    /**
     * Processes the result returned from the UPI application via ActivityResult.
     * Parses the response defensively according to NPCI standards.
     * Updates local Room transaction with any returned reference number (RRN / txnId).
     */
    fun processUpiResult(resultCode: Int, data: Intent?, transactionId: String) {
        val response = UpiResponseParser.parse(resultCode, data)
        val suggestedOutcome = when (response.status) {
            UpiResponseStatus.SUCCESS -> TransactionStatus.SUCCESS
            UpiResponseStatus.FAILURE -> TransactionStatus.FAILED
            UpiResponseStatus.CANCELLED -> TransactionStatus.CANCELLED
            UpiResponseStatus.SUBMITTED -> TransactionStatus.PENDING
            UpiResponseStatus.UNKNOWN -> null
        }

        val externalRef = response.approvalRefNo ?: response.txnId

        _outcomeState.update {
            it.copy(
                transactionId = transactionId,
                detectedStatus = response.status,
                detectedMessage = response.message,
                externalReference = externalRef,
                selectedOutcome = suggestedOutcome,
                error = null
            )
        }

        // If an external reference was captured, persist it to the PENDING transaction in Room
        if (!externalRef.isNullOrBlank() && transactionId.isNotBlank()) {
            viewModelScope.launch {
                try {
                    val session = sessionManager.sessionFlow.first()
                    val userId = session.userId ?: return@launch
                    val existing = transactionRepository.getTransactionById(transactionId, userId)
                    if (existing != null) {
                        transactionRepository.updateTransaction(
                            existing.copy(externalReference = externalRef)
                        )
                    }
                } catch (_: Exception) {}
            }
        }
    }

    // ── Payment Outcome State ────────────────────────────────────────────────

    private val _outcomeState = MutableStateFlow(PaymentOutcomeUiState())
    val outcomeState: StateFlow<PaymentOutcomeUiState> = _outcomeState.asStateFlow()

    /**
     * Loads the transaction directly from Room database.
     * If transactionId is missing or "pending", attempts to recover from persistent SessionManager.
     */
    fun prepareOutcome(transactionId: String) {
        viewModelScope.launch {
            _outcomeState.update { it.copy(isLoading = true, error = null) }
            val session = sessionManager.sessionFlow.first()
            val userId = session.userId ?: "demo_user"

            val resolvedId = if (transactionId.isNotBlank() && transactionId != "pending") {
                transactionId
            } else {
                sessionManager.getActivePaymentTransactionId() ?: ""
            }

            if (resolvedId.isBlank()) {
                _outcomeState.update {
                    it.copy(
                        transactionId = "",
                        isFound = false,
                        isLoading = false,
                        error = "No active transaction ID found."
                    )
                }
                return@launch
            }

            val existing = transactionRepository.getTransactionById(resolvedId, userId)
            if (existing != null) {
                val review = _reviewState.value
                val recipient = existing.recipientName.ifBlank { review.recipientName }
                val amount = if (existing.amountPaise > 0) existing.amountPaise else review.amountPaise

                _outcomeState.update {
                    it.copy(
                        transactionId = existing.transactionId,
                        recipientName = recipient,
                        amountPaise = amount,
                        note = existing.note,
                        externalReference = existing.externalReference ?: it.externalReference,
                        isFound = true,
                        isLoading = false,
                        error = null
                    )
                }
            } else {
                _outcomeState.update {
                    it.copy(
                        transactionId = resolvedId,
                        isFound = false,
                        isLoading = false,
                        error = "Transaction record not found in local database."
                    )
                }
            }
        }
    }

    fun onOutcomeSelected(status: TransactionStatus) {
        _outcomeState.update { it.copy(selectedOutcome = status, error = null) }
    }

    /**
     * Updates the transaction with the user-reported outcome.
     *
     * CRITICAL RULE: We only call updateTransaction — never insertTransaction.
     * The original PENDING record already exists from [createPendingAndLaunch].
     *
     * SUCCESS is marked as USER_REPORTED via [TransactionSource.UPI_INTENT].
     * Only SUCCESS transactions contribute to spending totals (enforced by DAO query).
     */
    fun confirmOutcome(onDone: () -> Unit) {
        val state = _outcomeState.value
        val outcome = state.selectedOutcome ?: run {
            _outcomeState.update { it.copy(error = "Please select a payment outcome before continuing.") }
            return
        }

        if (state.isSubmitting || state.isConfirmed) return
        _outcomeState.update { it.copy(isSubmitting = true, error = null) }

        viewModelScope.launch {
            try {
                val session = sessionManager.sessionFlow.first()
                val userId = session.userId ?: run {
                    _outcomeState.update { it.copy(isSubmitting = false, error = "Session expired. Please log in.") }
                    return@launch
                }

                val existing = transactionRepository.getTransactionById(state.transactionId, userId)
                if (existing == null) {
                    _outcomeState.update {
                        it.copy(
                            isSubmitting = false,
                            error = "Transaction record could not be updated because it was not found in the database."
                        )
                    }
                    return@launch
                }

                val updated = existing.copy(
                    transactionStatus = outcome,
                    externalReference = state.externalReference ?: existing.externalReference
                )
                transactionRepository.updateTransaction(updated)

                // Clear persistent active payment ID
                sessionManager.clearActivePaymentTransactionId()

                _outcomeState.update { it.copy(isSubmitting = false, isConfirmed = true) }
                onDone()
            } catch (e: Exception) {
                _outcomeState.update { it.copy(isSubmitting = false, error = "Failed to save outcome: ${e.message}") }
            }
        }
    }

    /**
     * Direct one-tap outcome confirmation (e.g. Cancel Payment or Leave Pending).
     */
    fun markOutcomeAndFinish(outcome: TransactionStatus, onDone: () -> Unit) {
        _outcomeState.update { it.copy(selectedOutcome = outcome) }
        confirmOutcome(onDone)
    }

    // ── Factory ──────────────────────────────────────────────────────────────

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as UpiSegregatorApp
                PaymentViewModel(
                    sessionManager = app.container.sessionManager,
                    transactionRepository = app.container.transactionRepository,
                    recipientRepository = app.container.recipientRepository,
                    upiPrefs = app.container.upiAppPreferenceManager
                )
            }
        }
    }
}

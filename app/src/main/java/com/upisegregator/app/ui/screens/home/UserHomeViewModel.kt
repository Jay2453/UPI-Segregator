package com.upisegregator.app.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.upisegregator.app.UpiSegregatorApp
import com.upisegregator.app.data.local.entity.TransactionEntity
import com.upisegregator.app.data.repository.TransactionRepository
import com.upisegregator.app.data.session.SessionManager
import com.upisegregator.app.data.session.UserSession
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class UserHomeUiState(
    val session: UserSession = UserSession(),
    val totalSpendingPaise: Long = 0L,
    val totalCount: Int = 0,
    val transactions: List<TransactionEntity> = emptyList(),
    val isLoading: Boolean = false
)

@OptIn(ExperimentalCoroutinesApi::class)
class UserHomeViewModel(
    private val sessionManager: SessionManager,
    private val transactionRepository: TransactionRepository
) : ViewModel() {

    val sessionState: StateFlow<UserSession> = sessionManager.sessionFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = UserSession()
        )

    private val userTransactionsFlow: Flow<List<TransactionEntity>> = sessionManager.sessionFlow
        .flatMapLatest { session ->
            val uid = session.userId
            if (uid != null && session.isLoggedIn) {
                transactionRepository.getTransactionsByUserFlow(uid)
            } else {
                flowOf(emptyList())
            }
        }

    private val userSpendingFlow: Flow<Long> = sessionManager.sessionFlow
        .flatMapLatest { session ->
            val uid = session.userId
            if (uid != null && session.isLoggedIn) {
                transactionRepository.getTotalSpendingByUserFlow(uid)
            } else {
                flowOf(0L)
            }
        }

    val uiState: StateFlow<UserHomeUiState> = combine(
        sessionState,
        userSpendingFlow,
        userTransactionsFlow
    ) { session, spending, transactions ->
        UserHomeUiState(
            session = session,
            totalSpendingPaise = spending,
            totalCount = transactions.size,
            transactions = transactions
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = UserHomeUiState()
    )

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
                UserHomeViewModel(
                    sessionManager = app.container.sessionManager,
                    transactionRepository = app.container.transactionRepository
                )
            }
        }
    }
}

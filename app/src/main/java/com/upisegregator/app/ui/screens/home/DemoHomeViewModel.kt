package com.upisegregator.app.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.upisegregator.app.UpiSegregatorApp
import com.upisegregator.app.data.demo.DemoDataManager
import com.upisegregator.app.data.local.entity.TransactionEntity
import com.upisegregator.app.data.local.entity.TransactionStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DemoHomeUiState(
    val totalSpendingPaise: Long = 0L,
    val totalCount: Int = 0,
    val successCount: Int = 0,
    val pendingCount: Int = 0,
    val failedCount: Int = 0,
    val recentTransactions: List<TransactionEntity> = emptyList(),
    val isResetting: Boolean = false
)

class DemoHomeViewModel(
    private val demoDataManager: DemoDataManager
) : ViewModel() {

    private val _isResetting = MutableStateFlow(false)

    val uiState: StateFlow<DemoHomeUiState> = combine(
        demoDataManager.demoSpendingTotalFlow,
        demoDataManager.demoTransactionsFlow,
        _isResetting
    ) { spendingTotal, transactions, isResetting ->
        DemoHomeUiState(
            totalSpendingPaise = spendingTotal,
            totalCount = transactions.size,
            successCount = transactions.count { it.transactionStatus == TransactionStatus.SUCCESS },
            pendingCount = transactions.count { it.transactionStatus == TransactionStatus.PENDING },
            failedCount = transactions.count { it.transactionStatus == TransactionStatus.FAILED || it.transactionStatus == TransactionStatus.CANCELLED },
            recentTransactions = transactions,
            isResetting = isResetting
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DemoHomeUiState()
    )

    init {
        viewModelScope.launch {
            demoDataManager.seedDemoDataIfNotPresent()
        }
    }

    fun resetDemoData() {
        viewModelScope.launch {
            _isResetting.value = true
            try {
                demoDataManager.resetDemoData()
            } finally {
                _isResetting.value = false
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as UpiSegregatorApp
                DemoHomeViewModel(app.container.demoDataManager)
            }
        }
    }
}

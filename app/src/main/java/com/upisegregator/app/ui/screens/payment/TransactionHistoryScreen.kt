package com.upisegregator.app.ui.screens.payment

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Pending
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.upisegregator.app.UpiSegregatorApp
import com.upisegregator.app.data.local.entity.TransactionEntity
import com.upisegregator.app.data.local.entity.TransactionSource
import com.upisegregator.app.data.local.entity.TransactionStatus
import com.upisegregator.app.data.repository.TransactionRepository
import com.upisegregator.app.data.session.SessionManager
import com.upisegregator.app.data.session.UserSession
import com.upisegregator.app.ui.theme.AccentTeal
import com.upisegregator.app.ui.theme.AccentTealLight
import com.upisegregator.app.ui.theme.BackgroundWhite
import com.upisegregator.app.ui.theme.BorderLight
import com.upisegregator.app.ui.theme.CardShape
import com.upisegregator.app.ui.theme.DemoGreen
import com.upisegregator.app.ui.theme.DemoGreenLight
import com.upisegregator.app.ui.theme.ErrorRed
import com.upisegregator.app.ui.theme.ErrorRedLight
import com.upisegregator.app.ui.theme.PillShape
import com.upisegregator.app.ui.theme.SurfaceLight
import com.upisegregator.app.ui.theme.TextMuted
import com.upisegregator.app.ui.theme.TextPrimary
import com.upisegregator.app.ui.theme.TextSecondary
import com.upisegregator.app.ui.theme.WarningAmber
import com.upisegregator.app.util.PaiseUtils
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ── ViewModel ────────────────────────────────────────────────────────────────

data class TransactionHistoryUiState(
    val transactions: List<TransactionEntity> = emptyList(),
    val session: UserSession = UserSession(),
    val isLoading: Boolean = true
)

@OptIn(ExperimentalCoroutinesApi::class)
class TransactionHistoryViewModel(
    private val sessionManager: SessionManager,
    private val transactionRepository: TransactionRepository
) : ViewModel() {

    val uiState: StateFlow<TransactionHistoryUiState> = sessionManager.sessionFlow
        .flatMapLatest { session ->
            val uid = session.userId
            if (uid != null) {
                combine(
                    transactionRepository.getTransactionsByUserFlow(uid),
                    kotlinx.coroutines.flow.flowOf(session)
                ) { txns, sess ->
                    TransactionHistoryUiState(
                        transactions = txns,
                        session = sess,
                        isLoading = false
                    )
                }
            } else {
                flowOf(TransactionHistoryUiState(isLoading = false))
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = TransactionHistoryUiState()
        )

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as UpiSegregatorApp
                TransactionHistoryViewModel(
                    sessionManager = app.container.sessionManager,
                    transactionRepository = app.container.transactionRepository
                )
            }
        }
    }
}

// ── Screen ───────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionHistoryScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TransactionHistoryViewModel = viewModel(factory = TransactionHistoryViewModel.Factory)
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Transaction History",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundWhite)
            )
        },
        containerColor = BackgroundWhite
    ) { innerPadding ->
        when {
            state.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            state.transactions.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ReceiptLong,
                            contentDescription = null,
                            tint = TextMuted,
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No transactions yet",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (state.session.isDemoMode)
                                "Demo transactions appear here once loaded."
                            else
                                "Your UPI payments will appear here after you complete your first transaction.",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = TextSecondary,
                                lineHeight = 18.sp
                            )
                        )
                    }
                }
            }

            else -> {
                LazyColumn(
                    modifier = modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${state.transactions.size} transaction${if (state.transactions.size != 1) "s" else ""}",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = TextMuted,
                                    fontWeight = FontWeight.Medium
                                )
                            )
                            if (state.session.isDemoMode) {
                                Box(
                                    modifier = Modifier
                                        .clip(PillShape)
                                        .background(DemoGreenLight)
                                        .padding(horizontal = 10.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = "DEMO DATA",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = DemoGreen,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 10.sp
                                        )
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                    }

                    items(state.transactions) { txn ->
                        TransactionCard(transaction = txn)
                    }

                    item { Spacer(modifier = Modifier.height(24.dp)) }
                }
            }
        }
    }
}

@Composable
private fun TransactionCard(transaction: TransactionEntity) {
    val (statusIcon, statusTint, statusBg, statusLabel) = when (transaction.transactionStatus) {
        TransactionStatus.SUCCESS -> Tuple4(Icons.Default.CheckCircle, DemoGreen, DemoGreenLight, "SUCCESS")
        TransactionStatus.PENDING -> Tuple4(Icons.Default.Pending, WarningAmber, Color(0xFFFFFBEB), "PENDING")
        TransactionStatus.FAILED -> Tuple4(Icons.Default.Cancel, ErrorRed, ErrorRedLight, "FAILED")
        TransactionStatus.CANCELLED -> Tuple4(Icons.Default.Cancel, TextSecondary, SurfaceLight, "CANCELLED")
        TransactionStatus.UNKNOWN -> Tuple4(Icons.AutoMirrored.Filled.HelpOutline, AccentTeal, AccentTealLight, "UNKNOWN")
    }

    val dateStr = remember(transaction.transactionDate) {
        SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
            .format(Date(transaction.transactionDate))
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = BackgroundWhite),
        border = BorderStroke(1.dp, BorderLight)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Status indicator circle
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(PillShape)
                    .background(statusBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = statusIcon,
                    contentDescription = statusLabel,
                    tint = statusTint,
                    modifier = Modifier.size(24.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = transaction.recipientName,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = dateStr,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                )
                Spacer(modifier = Modifier.height(3.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .clip(PillShape)
                            .background(statusBg)
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = statusLabel,
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = statusTint,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp
                            )
                        )
                    }
                    if (transaction.transactionSource == TransactionSource.DEMO) {
                        Box(
                            modifier = Modifier
                                .clip(PillShape)
                                .background(DemoGreenLight)
                                .padding(horizontal = 7.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "DEMO",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = DemoGreen,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp
                                )
                            )
                        }
                    }
                    Text(
                        text = transaction.paymentMethod.name,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = TextMuted,
                            fontSize = 11.sp
                        )
                    )
                }
                if (transaction.note.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = transaction.note,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = TextSecondary,
                            fontSize = 11.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Amount
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = PaiseUtils.formatPaiseToCurrency(transaction.amountPaise),
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = when (transaction.transactionStatus) {
                            TransactionStatus.SUCCESS -> DemoGreen
                            TransactionStatus.FAILED, TransactionStatus.CANCELLED -> TextMuted
                            else -> TextPrimary
                        },
                        fontSize = 15.sp
                    )
                )
            }
        }
    }
}

// Helper to destructure 4 values cleanly
private data class Tuple4<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

private operator fun <A, B, C, D> Tuple4<A, B, C, D>.component1() = a
private operator fun <A, B, C, D> Tuple4<A, B, C, D>.component2() = b
private operator fun <A, B, C, D> Tuple4<A, B, C, D>.component3() = c
private operator fun <A, B, C, D> Tuple4<A, B, C, D>.component4() = d

package com.upisegregator.app.ui.screens.home

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.upisegregator.app.data.local.entity.PaymentMethod
import com.upisegregator.app.data.local.entity.TransactionEntity
import com.upisegregator.app.data.local.entity.TransactionStatus
import com.upisegregator.app.ui.components.DemoModeBanner
import com.upisegregator.app.ui.components.SecondaryButton
import com.upisegregator.app.ui.theme.AccentPurple
import com.upisegregator.app.ui.theme.AccentPurpleLight
import com.upisegregator.app.ui.theme.AccentTeal
import com.upisegregator.app.ui.theme.AccentTealLight
import com.upisegregator.app.ui.theme.BackgroundWhite
import com.upisegregator.app.ui.theme.BorderLight
import com.upisegregator.app.ui.theme.CardShape
import com.upisegregator.app.ui.theme.DemoGreen
import com.upisegregator.app.ui.theme.DemoGreenDark
import com.upisegregator.app.ui.theme.DemoGreenLight
import com.upisegregator.app.ui.theme.PillShape
import com.upisegregator.app.ui.theme.PrimaryBlue
import com.upisegregator.app.ui.theme.PrimaryBlueLight
import com.upisegregator.app.ui.theme.SurfaceLight
import com.upisegregator.app.ui.theme.TextMuted
import com.upisegregator.app.ui.theme.TextPrimary
import com.upisegregator.app.ui.theme.TextSecondary
import com.upisegregator.app.util.PaiseUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DemoHomeScreen(
    onExitDemo: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DemoHomeViewModel = viewModel(factory = DemoHomeViewModel.Factory)
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "UPI Segregator",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(PillShape)
                                .background(DemoGreenLight)
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "DEMO",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = DemoGreenDark
                                )
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onExitDemo) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Logout,
                            contentDescription = "Exit Demo Mode",
                            tint = TextSecondary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BackgroundWhite
                )
            )
        },
        containerColor = BackgroundWhite
    ) { innerPadding ->
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(BackgroundWhite)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(2.dp))
                DemoModeBanner()
            }

            // Milestone 2 Room Database Stats Card
            item {
                DatabaseStatsCard(
                    totalSpendingPaise = uiState.totalSpendingPaise,
                    totalCount = uiState.totalCount,
                    successCount = uiState.successCount,
                    pendingCount = uiState.pendingCount,
                    failedCount = uiState.failedCount,
                    isResetting = uiState.isResetting,
                    onReset = { viewModel.resetDemoData() }
                )
            }

            // Section Header: Live Room Database Transactions
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "DEMO TRANSACTIONS (ROOM DB)",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = TextMuted,
                                letterSpacing = 1.sp,
                                fontSize = 12.sp
                            )
                        )
                        Text(
                            text = "Reactive Flow • Stored as integer paise in SQLite",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = TextSecondary,
                                fontSize = 11.sp
                            )
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(PillShape)
                            .background(PrimaryBlueLight)
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "${uiState.recentTransactions.size} Records",
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = PrimaryBlue,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        )
                    }
                }
            }

            // Transactions list items
            items(
                items = uiState.recentTransactions,
                key = { it.transactionId }
            ) { tx ->
                TransactionItemCard(transaction = tx)
            }

            // Section Header: Upcoming Modules
            item {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "UPCOMING V1 MODULES",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = TextMuted,
                        letterSpacing = 1.sp,
                        fontSize = 12.sp
                    )
                )
            }

            item {
                FeaturePreviewCard(
                    icon = Icons.Default.Payments,
                    iconTint = PrimaryBlue,
                    iconBg = PrimaryBlueLight,
                    title = "UPI Intent Handoff & Outcome Check",
                    subtitle = "Milestone 4 • Handoff to installed UPI apps with two-step user confirmation."
                )
            }

            item {
                FeaturePreviewCard(
                    icon = Icons.AutoMirrored.Filled.ReceiptLong,
                    iconTint = DemoGreen,
                    iconBg = DemoGreenLight,
                    title = "Manual Expense Logging",
                    subtitle = "Milestone 5 • Add and tag cash & digital spending into Room DB."
                )
            }

            item {
                FeaturePreviewCard(
                    icon = Icons.Default.FileDownload,
                    iconTint = AccentPurple,
                    iconBg = AccentPurpleLight,
                    title = "Statement Import & Deduplication",
                    subtitle = "Milestone 6 • SAF file picker with cryptographic duplicate detection."
                )
            }

            item {
                FeaturePreviewCard(
                    icon = Icons.Default.Analytics,
                    iconTint = AccentTeal,
                    iconBg = AccentTealLight,
                    title = "Recipient-Wise Spending Analytics",
                    subtitle = "Milestone 7 • Daily, weekly, monthly charts of whom you pay most."
                )
            }

            item {
                Spacer(modifier = Modifier.height(12.dp))
                SecondaryButton(
                    text = "Exit Demo Mode & Return to Login",
                    onClick = onExitDemo
                )
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun DatabaseStatsCard(
    totalSpendingPaise: Long,
    totalCount: Int,
    successCount: Int,
    pendingCount: Int,
    failedCount: Int,
    isResetting: Boolean,
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = SurfaceLight),
        border = BorderStroke(1.dp, BorderLight)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(PillShape)
                            .background(DemoGreenLight),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Storage,
                            contentDescription = null,
                            tint = DemoGreenDark,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Milestone 2 Room Active",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        )
                        Text(
                            text = "Local-first SQLite persistence layer",
                            style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                        )
                    }
                }

                OutlinedButton(
                    onClick = onReset,
                    enabled = !isResetting,
                    shape = PillShape,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = PrimaryBlue
                    ),
                    border = BorderStroke(1.dp, PrimaryBlue.copy(alpha = 0.4f)),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 10.dp,
                        vertical = 4.dp
                    )
                ) {
                    if (isResetting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = PrimaryBlue
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Reset Demo",
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Reset",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Total Confirmed Spending Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(CardShape)
                    .background(BackgroundWhite)
                    .padding(14.dp)
            ) {
                Column {
                    Text(
                        text = "TOTAL CONFIRMED SPENDING",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextMuted,
                            letterSpacing = 0.8.sp
                        )
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = PaiseUtils.formatPaiseToCurrency(totalSpendingPaise),
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = PrimaryBlue
                        )
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Calculated from $successCount SUCCESS records. Excludes PENDING & FAILED.",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Status Breakdown Chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatusBadge(
                    label = "$successCount Success",
                    bgColor = Color(0xFFDCFCE7),
                    textColor = Color(0xFF166534),
                    modifier = Modifier.weight(1f)
                )
                StatusBadge(
                    label = "$pendingCount Pending",
                    bgColor = Color(0xFFFEF3C7),
                    textColor = Color(0xFF92400E),
                    modifier = Modifier.weight(1f)
                )
                StatusBadge(
                    label = "$failedCount Failed",
                    bgColor = Color(0xFFFEE2E2),
                    textColor = Color(0xFF991B1B),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun StatusBadge(
    label: String,
    bgColor: Color,
    textColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(PillShape)
            .background(bgColor)
            .padding(vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                color = textColor,
                fontSize = 11.sp
            )
        )
    }
}

@Composable
private fun TransactionItemCard(
    transaction: TransactionEntity,
    modifier: Modifier = Modifier
) {
    val dateFormatter = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
    val formattedDate = dateFormatter.format(Date(transaction.transactionDate))

    val statusColor = when (transaction.transactionStatus) {
        TransactionStatus.SUCCESS -> Color(0xFF16A34A)
        TransactionStatus.PENDING -> Color(0xFFD97706)
        TransactionStatus.FAILED -> Color(0xFFDC2626)
        TransactionStatus.CANCELLED -> Color(0xFF6B7280)
        TransactionStatus.UNKNOWN -> Color(0xFF9CA3AF)
    }

    val statusIcon = when (transaction.transactionStatus) {
        TransactionStatus.SUCCESS -> Icons.Default.CheckCircle
        TransactionStatus.PENDING -> Icons.Default.HourglassTop
        TransactionStatus.FAILED, TransactionStatus.CANCELLED -> Icons.Default.Close
        TransactionStatus.UNKNOWN -> Icons.Default.Close
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = BackgroundWhite),
        border = BorderStroke(1.dp, BorderLight)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Payment Method Badge
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(PillShape)
                    .background(
                        when (transaction.paymentMethod) {
                            PaymentMethod.UPI -> PrimaryBlueLight
                            PaymentMethod.CASH -> DemoGreenLight
                            PaymentMethod.CARD -> AccentPurpleLight
                            PaymentMethod.BANK_TRANSFER -> AccentTealLight
                            PaymentMethod.OTHER -> SurfaceLight
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = when (transaction.paymentMethod) {
                        PaymentMethod.UPI -> "UPI"
                        PaymentMethod.CASH -> "Cash"
                        PaymentMethod.CARD -> "Card"
                        PaymentMethod.BANK_TRANSFER -> "Bank"
                        PaymentMethod.OTHER -> "Other"
                    },
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = when (transaction.paymentMethod) {
                            PaymentMethod.UPI -> PrimaryBlue
                            PaymentMethod.CASH -> DemoGreenDark
                            PaymentMethod.CARD -> AccentPurple
                            PaymentMethod.BANK_TRANSFER -> AccentTeal
                            PaymentMethod.OTHER -> TextSecondary
                        },
                        fontSize = 11.sp
                    )
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Details
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = transaction.recipientName,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary,
                            fontSize = 14.sp
                        )
                    )
                    Icon(
                        imageVector = statusIcon,
                        contentDescription = transaction.transactionStatus.name,
                        tint = statusColor,
                        modifier = Modifier.size(13.dp)
                    )
                }

                if (transaction.note.isNotBlank()) {
                    Text(
                        text = transaction.note,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = TextSecondary,
                            fontSize = 12.sp
                        ),
                        maxLines = 1
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = transaction.category,
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = TextMuted,
                            fontSize = 10.sp
                        )
                    )
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = BorderLight,
                            fontSize = 10.sp
                        )
                    )
                    Text(
                        text = formattedDate,
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = TextMuted,
                            fontSize = 10.sp
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Amount in Paise formatted
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = PaiseUtils.formatPaiseToCurrency(transaction.amountPaise),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = if (transaction.transactionStatus == TransactionStatus.SUCCESS) TextPrimary else TextMuted,
                        fontSize = 15.sp
                    )
                )
                Text(
                    text = "${transaction.amountPaise} paise",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = TextMuted,
                        fontSize = 10.sp
                    )
                )
            }
        }
    }
}

@Composable
private fun FeaturePreviewCard(
    icon: ImageVector,
    iconTint: Color,
    iconBg: Color,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = BackgroundWhite),
        border = BorderStroke(1.dp, BorderLight)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(PillShape)
                    .background(iconBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                        fontSize = 14.sp
                    )
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = TextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                )
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = BorderLight,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

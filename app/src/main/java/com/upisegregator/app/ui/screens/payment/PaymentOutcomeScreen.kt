package com.upisegregator.app.ui.screens.payment

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Pending
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.upisegregator.app.data.local.entity.TransactionStatus
import com.upisegregator.app.data.upi.UpiResponseStatus
import com.upisegregator.app.ui.components.PrimaryButton
import com.upisegregator.app.ui.components.SecondaryButton
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
import com.upisegregator.app.ui.theme.PrimaryBlue
import com.upisegregator.app.ui.theme.PrimaryBlueLight
import com.upisegregator.app.ui.theme.SurfaceLight
import com.upisegregator.app.ui.theme.TextMuted
import com.upisegregator.app.ui.theme.TextPrimary
import com.upisegregator.app.ui.theme.TextSecondary
import com.upisegregator.app.ui.theme.WarningAmber
import com.upisegregator.app.util.PaiseUtils

/**
 * Step 4 of 4 — Payment Outcome Confirmation.
 *
 * Shown after the user returns from the external UPI app.
 *
 * CRITICAL FINANCIAL INTEGRITY RULES:
 * - Returning from the UPI app alone does NOT automatically mark SUCCESS.
 * - Client-side intent responses cannot be independently bank-verified.
 * - Only confirmed SUCCESS transactions count towards spending totals in the DAO.
 * - PENDING, FAILED, and CANCELLED records are retained for auditing.
 * - Never converts an unknown transaction silently into SUCCESS.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentOutcomeScreen(
    transactionId: String,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PaymentViewModel = viewModel(factory = PaymentViewModel.Factory)
) {
    val state by viewModel.outcomeState.collectAsState()
    val scrollState = rememberScrollState()

    LaunchedEffect(transactionId) {
        viewModel.prepareOutcome(transactionId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Payment Status",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundWhite)
            )
        },
        containerColor = BackgroundWhite
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(BackgroundWhite)
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Step indicator
            Box(
                modifier = Modifier
                    .clip(PillShape)
                    .background(PrimaryBlueLight)
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "STEP 4 OF 4 — CONFIRM OUTCOME",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = PrimaryBlue,
                        fontSize = 11.sp
                    )
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            when {
                state.isLoading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = PrimaryBlue)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Loading payment details…",
                                style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary)
                            )
                        }
                    }
                }

                !state.isFound -> {
                    // Friendly recovery card when transaction record cannot be loaded
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = CardShape,
                        colors = CardDefaults.cardColors(containerColor = SurfaceLight),
                        border = BorderStroke(1.dp, BorderLight)
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = PrimaryBlue,
                                modifier = Modifier.size(36.dp)
                            )
                            Text(
                                text = "Payment Incomplete or Not Found",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                            )
                            Text(
                                text = "We couldn't locate an active pending record for this payment in your local database. " +
                                        "If you completed a payment in your UPI app, please check your bank statement or transaction history.",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = TextSecondary,
                                    lineHeight = 18.sp
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    PrimaryButton(
                        text = "View Transaction History",
                        onClick = onDone
                    )
                }

                else -> {
                    // 1. Payment Summary Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = CardShape,
                        colors = CardDefaults.cardColors(containerColor = SurfaceLight),
                        border = BorderStroke(1.dp, BorderLight)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp)
                        ) {
                            Text(
                                text = "PAYMENT DETAILS",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = TextMuted,
                                    letterSpacing = 0.8.sp,
                                    fontSize = 11.sp
                                )
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "To: ${state.recipientName.ifBlank { "Recipient" }}",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextPrimary
                                )
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = PaiseUtils.formatPaiseToCurrency(state.amountPaise),
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    color = PrimaryBlue
                                )
                            )
                            if (state.note.isNotBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Note: ${state.note}",
                                    style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 2. UPI App Detection Banner
                    DetectedStatusBanner(
                        status = state.detectedStatus,
                        message = state.detectedMessage,
                        reference = state.externalReference
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Text(
                        text = "Confirm the final payment outcome:",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            lineHeight = 22.sp
                        )
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Please verify with your UPI app or bank SMS, then select an outcome below.",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = TextSecondary,
                            lineHeight = 17.sp
                        )
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // 3. Four Outcome Options
                    OutcomeOptionCard(
                        icon = Icons.Default.CheckCircle,
                        iconTint = DemoGreen,
                        iconBg = DemoGreenLight,
                        title = "Payment Successful",
                        subtitle = "Money was debited from your account and sent to the recipient.",
                        isSelected = state.selectedOutcome == TransactionStatus.SUCCESS,
                        onSelect = { viewModel.onOutcomeSelected(TransactionStatus.SUCCESS) }
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutcomeOptionCard(
                        icon = Icons.Default.Cancel,
                        iconTint = TextSecondary,
                        iconBg = SurfaceLight,
                        title = "Payment Cancelled",
                        subtitle = "You cancelled or exited before completing payment in the UPI app.",
                        isSelected = state.selectedOutcome == TransactionStatus.CANCELLED,
                        onSelect = { viewModel.onOutcomeSelected(TransactionStatus.CANCELLED) }
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutcomeOptionCard(
                        icon = Icons.Default.Cancel,
                        iconTint = ErrorRed,
                        iconBg = ErrorRedLight,
                        title = "Payment Failed",
                        subtitle = "Payment was declined by your bank or the UPI app encountered an error.",
                        isSelected = state.selectedOutcome == TransactionStatus.FAILED,
                        onSelect = { viewModel.onOutcomeSelected(TransactionStatus.FAILED) }
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutcomeOptionCard(
                        icon = Icons.AutoMirrored.Filled.HelpOutline,
                        iconTint = WarningAmber,
                        iconBg = Color(0xFFFFFBEB),
                        title = "Leave as Pending",
                        subtitle = "Status could not be confirmed yet. Retain as PENDING for future review.",
                        isSelected = state.selectedOutcome == TransactionStatus.PENDING,
                        onSelect = { viewModel.onOutcomeSelected(TransactionStatus.PENDING) }
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    // 4. Financial Integrity Notice
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = CardShape,
                        colors = CardDefaults.cardColors(containerColor = AccentTealLight),
                        border = BorderStroke(1.dp, AccentTeal.copy(alpha = 0.3f))
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Pending,
                                contentDescription = null,
                                tint = AccentTeal,
                                modifier = Modifier
                                    .size(18.dp)
                                    .padding(top = 2.dp)
                            )
                            Text(
                                text = "Only confirmed SUCCESS payments count towards your spending total. " +
                                        "Local UPI intent handoff does not independently verify bank ledger settlement.",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = AccentTeal,
                                    lineHeight = 17.sp,
                                    fontSize = 12.sp
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Error display if any
                    if (state.error != null) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = CardShape,
                            colors = CardDefaults.cardColors(containerColor = ErrorRedLight),
                            border = BorderStroke(1.dp, ErrorRed.copy(alpha = 0.3f))
                        ) {
                            Text(
                                text = state.error!!,
                                style = MaterialTheme.typography.bodySmall.copy(color = ErrorRed),
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    // 5. Action Buttons
                    PrimaryButton(
                        text = if (state.isSubmitting) "Saving…" else "Save Outcome & Finish",
                        isLoading = state.isSubmitting,
                        enabled = state.selectedOutcome != null && !state.isSubmitting,
                        onClick = {
                            viewModel.confirmOutcome(onDone = onDone)
                        }
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Convenient cancel shortcut if detected as cancelled
                    if (state.detectedStatus == UpiResponseStatus.CANCELLED && state.selectedOutcome != TransactionStatus.CANCELLED) {
                        SecondaryButton(
                            text = "Mark as Cancelled & Finish",
                            onClick = {
                                viewModel.markOutcomeAndFinish(TransactionStatus.CANCELLED, onDone)
                            }
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    SecondaryButton(
                        text = "Leave as Pending & Return Home",
                        onClick = {
                            viewModel.markOutcomeAndFinish(TransactionStatus.PENDING, onDone)
                        }
                    )

                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }
}

@Composable
private fun DetectedStatusBanner(
    status: UpiResponseStatus?,
    message: String?,
    reference: String?
) {
    val (containerColor, borderColor, icon, iconTint, title, subtitle) = when (status) {
        UpiResponseStatus.SUCCESS -> StatusBannerConfig(
            containerColor = DemoGreenLight,
            borderColor = DemoGreen.copy(alpha = 0.4f),
            icon = Icons.Default.CheckCircle,
            iconTint = DemoGreen,
            title = "UPI App Reported: Payment Successful",
            subtitle = reference?.let { "Transaction Reference: $it" }
                ?: "The UPI application returned a success response."
        )

        UpiResponseStatus.CANCELLED -> StatusBannerConfig(
            containerColor = SurfaceLight,
            borderColor = BorderLight,
            icon = Icons.Default.Cancel,
            iconTint = TextSecondary,
            title = "Payment Cancelled / Incomplete",
            subtitle = "You returned without completing the payment in the UPI app."
        )

        UpiResponseStatus.FAILURE -> StatusBannerConfig(
            containerColor = ErrorRedLight,
            borderColor = ErrorRed.copy(alpha = 0.3f),
            icon = Icons.Default.Cancel,
            iconTint = ErrorRed,
            title = "UPI App Reported: Payment Failed",
            subtitle = message ?: "The UPI application indicated that the transaction failed."
        )

        UpiResponseStatus.SUBMITTED -> StatusBannerConfig(
            containerColor = Color(0xFFFFFBEB),
            borderColor = WarningAmber.copy(alpha = 0.4f),
            icon = Icons.Default.Pending,
            iconTint = WarningAmber,
            title = "Payment Processing / Submitted",
            subtitle = "The transaction is processing through banking networks."
        )

        else -> StatusBannerConfig(
            containerColor = PrimaryBlueLight,
            borderColor = PrimaryBlue.copy(alpha = 0.3f),
            icon = Icons.AutoMirrored.Filled.HelpOutline,
            iconTint = PrimaryBlue,
            title = "Payment Status Could Not Be Confirmed",
            subtitle = "We couldn't confirm the payment status. Please check your UPI app or bank transaction history."
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier
                    .size(20.dp)
                    .padding(top = 1.dp)
            )
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = TextSecondary,
                        lineHeight = 16.sp
                    )
                )
            }
        }
    }
}

private data class StatusBannerConfig(
    val containerColor: Color,
    val borderColor: Color,
    val icon: ImageVector,
    val iconTint: Color,
    val title: String,
    val subtitle: String
)

@Composable
private fun OutcomeOptionCard(
    icon: ImageVector,
    iconTint: Color,
    iconBg: Color,
    title: String,
    subtitle: String,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    val borderColor = if (isSelected) PrimaryBlue else BorderLight
    val bgColor = if (isSelected) PrimaryBlueLight.copy(alpha = 0.4f) else BackgroundWhite

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(bgColor)
            .border(1.5.dp, borderColor, CardShape)
            .clickable(onClick = onSelect)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(PillShape)
                .background(iconBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(26.dp)
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = TextSecondary,
                    lineHeight = 16.sp,
                    fontSize = 12.sp
                )
            )
        }

        if (isSelected) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "Selected",
                tint = PrimaryBlue,
                modifier = Modifier.size(22.dp)
            )
        } else {
            Icon(
                imageVector = Icons.Default.RadioButtonUnchecked,
                contentDescription = null,
                tint = BorderLight,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

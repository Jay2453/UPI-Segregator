package com.upisegregator.app.ui.screens.payment

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.CurrencyRupee
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.upisegregator.app.ui.components.PrimaryButton
import com.upisegregator.app.ui.components.SecondaryButton
import com.upisegregator.app.ui.theme.BackgroundWhite
import com.upisegregator.app.ui.theme.BorderLight
import com.upisegregator.app.ui.theme.CardShape
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
 * Step 3 of 4 — Payment Review Screen.
 *
 * Shows the full payment details + selected UPI app for the user to review
 * before the PENDING transaction is written to Room and the UPI app is launched.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentReviewScreen(
    transactionId: String,
    onNavigateBack: () -> Unit,
    onPaymentLaunched: (transactionId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PaymentViewModel = viewModel(factory = PaymentViewModel.Factory)
) {
    val context = LocalContext.current
    val state by viewModel.reviewState.collectAsState()
    val scrollState = rememberScrollState()

    var launchedTxnId by rememberSaveable { mutableStateOf("") }

    val upiLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val effectiveTxnId = launchedTxnId.ifBlank {
            viewModel.activeTransactionId?.ifBlank { null } ?: transactionId
        }
        viewModel.processUpiResult(
            resultCode = result.resultCode,
            data = result.data,
            transactionId = effectiveTxnId
        )
        onPaymentLaunched(effectiveTxnId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Review Payment",
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
                    text = "STEP 3 OF 4 — CONFIRM PAYMENT DETAILS",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = PrimaryBlue,
                        fontSize = 11.sp
                    )
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Amount highlight card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = CardShape,
                colors = CardDefaults.cardColors(containerColor = PrimaryBlueLight),
                border = BorderStroke(1.dp, PrimaryBlue.copy(alpha = 0.25f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "AMOUNT TO PAY",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = PrimaryBlue,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            fontSize = 11.sp
                        )
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = PaiseUtils.formatPaiseToCurrency(state.amountPaise),
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = PrimaryBlue,
                            fontSize = 38.sp
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Payment details card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = CardShape,
                colors = CardDefaults.cardColors(containerColor = SurfaceLight),
                border = BorderStroke(1.dp, BorderLight)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "PAYMENT DETAILS",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextMuted,
                            letterSpacing = 0.8.sp,
                            fontSize = 11.sp
                        )
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    ReviewRow(
                        icon = Icons.Default.Person,
                        label = "Recipient",
                        value = state.recipientName.ifBlank { "—" }
                    )
                    HorizontalDivider(color = BorderLight, modifier = Modifier.padding(vertical = 10.dp))

                    ReviewRow(
                        icon = Icons.Default.AccountBalanceWallet,
                        label = "UPI ID",
                        value = state.upiId.ifBlank { "—" }
                    )
                    HorizontalDivider(color = BorderLight, modifier = Modifier.padding(vertical = 10.dp))

                    ReviewRow(
                        icon = Icons.Default.CurrencyRupee,
                        label = "Amount",
                        value = PaiseUtils.formatPaiseToCurrency(state.amountPaise)
                    )

                    if (!state.merchantCategoryName.isNullOrBlank()) {
                        HorizontalDivider(color = BorderLight, modifier = Modifier.padding(vertical = 10.dp))
                        ReviewRow(
                            icon = Icons.Default.Category,
                            label = "Category",
                            value = state.merchantCategoryName!!
                        )
                    }

                    if (state.note.isNotBlank()) {
                        HorizontalDivider(color = BorderLight, modifier = Modifier.padding(vertical = 10.dp))
                        ReviewRow(
                            icon = Icons.Default.Tag,
                            label = "Note",
                            value = state.note
                        )
                    }

                    HorizontalDivider(color = BorderLight, modifier = Modifier.padding(vertical = 10.dp))

                    ReviewRow(
                        icon = Icons.Default.PhoneAndroid,
                        label = "Pay with",
                        value = state.selectedApp?.displayName ?: "System UPI Chooser"
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Important warning card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = CardShape,
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFBEB)),
                border = BorderStroke(1.dp, WarningAmber.copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.WarningAmber,
                        contentDescription = null,
                        tint = WarningAmber,
                        modifier = Modifier
                            .size(18.dp)
                            .padding(top = 2.dp)
                    )
                    Column {
                        Text(
                            text = "After tapping \"Confirm & Open UPI App\":",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF92400E),
                                lineHeight = 16.sp,
                                fontSize = 12.sp
                            )
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "• A PENDING record is saved in your local history.\n" +
                                    "• Your UPI app will open with these details.\n" +
                                    "• You will confirm the outcome when you return.\n" +
                                    "• We do NOT auto-confirm payment success.",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = Color(0xFF78350F),
                                lineHeight = 17.sp,
                                fontSize = 12.sp
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Error
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

            PrimaryButton(
                text = if (state.isLaunching) "Opening UPI App…" else "Confirm & Open UPI App →",
                isLoading = state.isLaunching,
                onClick = {
                    viewModel.createPendingAndLaunch(
                        context = context,
                        onLaunch = { intent, transactionId ->
                            launchedTxnId = transactionId
                            try {
                                upiLauncher.launch(intent)
                            } catch (e: android.content.ActivityNotFoundException) {
                                try {
                                    context.startActivity(intent)
                                    onPaymentLaunched(transactionId)
                                } catch (_: Exception) {
                                    viewModel.setReviewError("Selected UPI application could not be opened. Please select another app.")
                                }
                            } catch (e: Exception) {
                                viewModel.setReviewError("Failed to open UPI app: ${e.message ?: "Unknown error"}")
                            }
                        },
                        onNoApp = {
                            // Error message is set in review state
                        },
                        onError = { /* error is displayed in state */ }
                    )
                },
                enabled = !state.isLaunching
            )

            Spacer(modifier = Modifier.height(12.dp))

            SecondaryButton(
                text = "← Edit Details",
                onClick = onNavigateBack
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun ReviewRow(
    icon: ImageVector,
    label: String,
    value: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = TextMuted,
            modifier = Modifier.size(18.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = TextMuted,
                    fontSize = 11.sp
                )
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
            )
        }
    }
}

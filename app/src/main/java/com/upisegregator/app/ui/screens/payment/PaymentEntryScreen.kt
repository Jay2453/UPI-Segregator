package com.upisegregator.app.ui.screens.payment

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CurrencyRupee
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Tag
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.upisegregator.app.ui.components.AppTextField
import com.upisegregator.app.ui.components.DemoModeBanner
import com.upisegregator.app.ui.components.PrimaryButton
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
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults

/**
 * Step 1 of 4 in the UPI payment flow.
 *
 * Collects: Recipient Name, UPI ID, Amount (INR), and an optional note.
 * Validates all fields before proceeding to app selection.
 * Blocks real payment initiation in Demo Mode with a clear explanation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentEntryScreen(
    onNavigateBack: () -> Unit,
    onContinue: (amountPaise: Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PaymentViewModel = viewModel(factory = PaymentViewModel.Factory)
) {
    val state by viewModel.entryState.collectAsState()
    val session by viewModel.sessionState.collectAsState()
    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "New UPI Payment",
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
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Demo Mode banner + block
            if (session.isDemoMode) {
                DemoModeBanner()
                Spacer(modifier = Modifier.height(12.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = CardShape,
                    colors = CardDefaults.cardColors(containerColor = ErrorRedLight),
                    border = BorderStroke(1.dp, ErrorRed.copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccountBalanceWallet,
                            contentDescription = null,
                            tint = ErrorRed,
                            modifier = Modifier.size(24.dp)
                        )
                        Column {
                            Text(
                                text = "Demo Mode — Payments Disabled",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = ErrorRed
                                )
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Real UPI payments are only available for registered accounts. Sign up or log in to initiate payments.",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = ErrorRed.copy(alpha = 0.85f),
                                    lineHeight = 16.sp
                                )
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
                return@Column
            }

            // Step indicator pill
            Box(
                modifier = Modifier
                    .clip(PillShape)
                    .background(PrimaryBlueLight)
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "STEP 1 OF 4 — PAYMENT DETAILS",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = PrimaryBlue,
                        fontSize = 11.sp,
                        letterSpacing = 0.5.sp
                    )
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Recipient section
            Text(
                text = "Recipient",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            )
            Spacer(modifier = Modifier.height(14.dp))

            AppTextField(
                value = state.recipientName,
                onValueChange = viewModel::onRecipientNameChange,
                label = "Recipient Name",
                placeholder = "e.g. Rahul Kumar",
                errorMessage = state.recipientNameError,
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = if (state.recipientNameError != null) ErrorRed else TextMuted,
                        modifier = Modifier.size(20.dp)
                    )
                },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Next
                )
            )

            Spacer(modifier = Modifier.height(14.dp))

            AppTextField(
                value = state.upiId,
                onValueChange = viewModel::onUpiIdChange,
                label = "UPI ID (VPA)",
                placeholder = "e.g. rahul@upi or 9876543210@paytm",
                errorMessage = state.upiIdError,
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.AccountBalanceWallet,
                        contentDescription = null,
                        tint = if (state.upiIdError != null) ErrorRed else TextMuted,
                        modifier = Modifier.size(20.dp)
                    )
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next
                )
            )

            Spacer(modifier = Modifier.height(22.dp))

            // Amount section
            Text(
                text = "Amount",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            )
            Spacer(modifier = Modifier.height(14.dp))

            AppTextField(
                value = state.amountText,
                onValueChange = viewModel::onAmountChange,
                label = "Amount (₹)",
                placeholder = "0.00",
                errorMessage = state.amountError,
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.CurrencyRupee,
                        contentDescription = null,
                        tint = if (state.amountError != null) ErrorRed else TextMuted,
                        modifier = Modifier.size(20.dp)
                    )
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Next
                )
            )

            // Amount helper
            Text(
                text = "Enter amount in Indian Rupees (e.g. 250 or 1499.50). Max 2 decimal places.",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = TextMuted,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                ),
                modifier = Modifier.padding(top = 6.dp, start = 4.dp)
            )

            Spacer(modifier = Modifier.height(22.dp))

            // Note section
            Text(
                text = "Note (Optional)",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            )
            Spacer(modifier = Modifier.height(14.dp))

            AppTextField(
                value = state.note,
                onValueChange = viewModel::onNoteChange,
                label = "Payment Description",
                placeholder = "e.g. Lunch, Rent, Tuition fees",
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Tag,
                        contentDescription = null,
                        tint = TextMuted,
                        modifier = Modifier.size(20.dp)
                    )
                },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done
                )
            )

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "The note will appear in your payment record. Maximum 50 characters.",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = TextMuted,
                    fontSize = 12.sp
                ),
                modifier = Modifier.padding(start = 4.dp)
            )

            Spacer(modifier = Modifier.height(28.dp))

            // General error
            if (state.generalError != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = CardShape,
                    colors = CardDefaults.cardColors(containerColor = ErrorRedLight),
                    border = BorderStroke(1.dp, ErrorRed.copy(alpha = 0.3f))
                ) {
                    Text(
                        text = state.generalError!!,
                        style = MaterialTheme.typography.bodySmall.copy(color = ErrorRed),
                        modifier = Modifier.padding(12.dp)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Safety notice
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = CardShape,
                colors = CardDefaults.cardColors(containerColor = SurfaceLight),
                border = BorderStroke(1.dp, BorderLight)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AccountBalanceWallet,
                        contentDescription = null,
                        tint = PrimaryBlue,
                        modifier = Modifier
                            .size(18.dp)
                            .padding(top = 2.dp)
                    )
                    Text(
                        text = "UPI Segregator opens your installed UPI app with these details prefilled. " +
                                "The actual payment is completed inside that app. " +
                                "We never touch your bank credentials or PIN.",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = TextSecondary,
                            lineHeight = 17.sp,
                            fontSize = 12.sp
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            PrimaryButton(
                text = "Continue to Choose UPI App →",
                onClick = {
                    val paise = viewModel.validateAndGetPaise()
                    if (paise != null) onContinue(paise)
                },
                enabled = !state.isLoading
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

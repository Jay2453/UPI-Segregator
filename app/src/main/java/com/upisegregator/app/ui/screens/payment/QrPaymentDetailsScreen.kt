package com.upisegregator.app.ui.screens.payment

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.CurrencyRupee
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import com.upisegregator.app.ui.components.AppTextField
import com.upisegregator.app.ui.components.DemoModeBanner
import com.upisegregator.app.ui.components.PrimaryButton
import com.upisegregator.app.ui.components.SecondaryButton
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
import com.upisegregator.app.util.PaiseUtils

/**
 * Step 1 of 4 — Scanned Recipient Details & Smart Amount Mode.
 *
 * Displays validated recipient details extracted from the scanned UPI QR code.
 * Implements SMART AMOUNT MODE:
 * - If the QR specified a fixed amount, displays it as read-only with clear visual lock feedback.
 * - If the QR specified no amount, presents an interactive amount input with PaiseUtils validation.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun QrPaymentDetailsScreen(
    onNavigateBack: () -> Unit,
    onContinue: (amountPaise: Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PaymentViewModel
) {
    val state by viewModel.detailsState.collectAsState()
    val session by viewModel.sessionState.collectAsState()
    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Payment Details",
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

            // Demo Mode banner
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
                                text = "Real UPI payments can only be initiated from a registered user account. Log in or sign up to pay.",
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

            // Step Indicator
            Box(
                modifier = Modifier
                    .clip(PillShape)
                    .background(PrimaryBlueLight)
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "STEP 1 OF 4 — RECIPIENT & AMOUNT",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = PrimaryBlue,
                        fontSize = 11.sp,
                        letterSpacing = 0.5.sp
                    )
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            // 1. Scanned Recipient Card
            Card(
                modifier = Modifier.fillMaxWidth(),
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
                        Text(
                            text = "SCANNED RECIPIENT",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = TextMuted,
                                letterSpacing = 0.8.sp,
                                fontSize = 11.sp
                            )
                        )
                        Box(
                            modifier = Modifier
                                .clip(PillShape)
                                .background(DemoGreenLight)
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Verified,
                                    contentDescription = null,
                                    tint = DemoGreen,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = "QR Verified",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = DemoGreen,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp
                                    )
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Recipient Name
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(PillShape)
                                .background(PrimaryBlueLight),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = PrimaryBlue,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Column {
                            Text(
                                text = state.recipientName.ifBlank { "Recipient" },
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                            )
                            Text(
                                text = "Verified payee name from QR",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = TextSecondary,
                                    fontSize = 11.sp
                                )
                            )
                        }
                    }

                    HorizontalDivider(color = BorderLight, modifier = Modifier.padding(vertical = 12.dp))

                    // Recipient UPI ID
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(PillShape)
                                .background(Color(0xFFEDE9FE)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AccountBalanceWallet,
                                contentDescription = null,
                                tint = Color(0xFF6D28D9),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "UPI ID (VPA)",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = TextMuted,
                                    fontSize = 11.sp
                                )
                            )
                            Text(
                                text = state.upiId.ifBlank { "—" },
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                            )
                        }
                    }

                    // Merchant Category if available
                    if (!state.merchantCategoryName.isNullOrBlank()) {
                        HorizontalDivider(color = BorderLight, modifier = Modifier.padding(vertical = 12.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(PillShape)
                                    .background(Color(0xFFFEF3C7)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Category,
                                    contentDescription = null,
                                    tint = Color(0xFFB45309),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "Merchant Category",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = TextMuted,
                                        fontSize = 11.sp
                                    )
                                )
                                Text(
                                    text = state.merchantCategoryName!!,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        color = TextPrimary
                                    )
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 2. SMART AMOUNT MODE Section
            Text(
                text = "Payment Amount",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            )
            Spacer(modifier = Modifier.height(10.dp))

            if (state.isAmountFixed) {
                // FIXED AMOUNT CARD (QR specifies exact amount)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = CardShape,
                    colors = CardDefaults.cardColors(containerColor = PrimaryBlueLight),
                    border = BorderStroke(1.dp, PrimaryBlue.copy(alpha = 0.3f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = PrimaryBlue,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "FIXED AMOUNT BY RECIPIENT",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = PrimaryBlue,
                                    letterSpacing = 0.8.sp,
                                    fontSize = 11.sp
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = PaiseUtils.formatPaiseToCurrency(state.amountPaise),
                            style = MaterialTheme.typography.headlineLarge.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = PrimaryBlue,
                                fontSize = 38.sp
                            )
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "This QR code specifies a fixed payment amount. The amount cannot be modified.",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = PrimaryBlue.copy(alpha = 0.85f),
                                fontSize = 12.sp
                            )
                        )
                    }
                }
            } else {
                // USER ENTERABLE AMOUNT
                AppTextField(
                    value = state.amountText,
                    onValueChange = viewModel::onDetailsAmountChange,
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

                Spacer(modifier = Modifier.height(10.dp))

                // Quick Amount Suggestion Chips
                Text(
                    text = "Quick amount options:",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                )

                Spacer(modifier = Modifier.height(6.dp))

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("10", "100", "250", "500", "1000").forEach { quickAmount ->
                        Box(
                            modifier = Modifier
                                .clip(PillShape)
                                .background(SurfaceLight)
                                .border(1.dp, BorderLight, PillShape)
                                .clickable {
                                    viewModel.onDetailsAmountChange(quickAmount)
                                }
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "+₹$quickAmount",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = PrimaryBlue,
                                    fontSize = 12.sp
                                )
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 3. Optional Payment Note
            Text(
                text = "Payment Note (Optional)",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            )
            Spacer(modifier = Modifier.height(10.dp))

            AppTextField(
                value = state.note,
                onValueChange = viewModel::onDetailsNoteChange,
                label = "Description / Note",
                placeholder = "e.g. Chai, Groceries, Tuition fees",
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

            Spacer(modifier = Modifier.height(24.dp))

            // General error banner if any
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

            // Continue Button
            PrimaryButton(
                text = "Continue to Choose UPI App →",
                onClick = {
                    val paise = viewModel.validateAndGetDetailsPaise()
                    if (paise != null) {
                        onContinue(paise)
                    }
                }
            )

            Spacer(modifier = Modifier.height(10.dp))

            SecondaryButton(
                text = "← Rescan QR Code",
                onClick = onNavigateBack
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

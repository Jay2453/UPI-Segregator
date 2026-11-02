package com.upisegregator.app.ui.screens.importer

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.upisegregator.app.data.importer.model.DuplicateStatus
import com.upisegregator.app.data.importer.model.ImportableTransaction
import com.upisegregator.app.data.importer.model.RowValidationStatus
import com.upisegregator.app.data.importer.model.StatementImportResult
import com.upisegregator.app.data.importer.model.TransactionDirection
import com.upisegregator.app.ui.components.PrimaryButton
import com.upisegregator.app.ui.components.SecondaryButton
import com.upisegregator.app.ui.theme.AccentPurple
import com.upisegregator.app.ui.theme.AccentPurpleLight
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatementImportScreen(
    onNavigateBack: () -> Unit,
    onViewHistory: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: StatementImportViewModel = viewModel(factory = StatementImportViewModel.Factory)
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // SAF file picker launcher for CSV files
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val fileName = getFileName(context.contentResolver, uri) ?: "Bank_Statement.csv"
            viewModel.onFileSelected(uri, context.contentResolver, fileName)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Import Statement",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        )
                        Text(
                            text = if (uiState.session.isDemoMode) "Demo Profile • CSV Import" else "Bank statement import & deduplication",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = TextSecondary,
                                fontSize = 11.sp
                            )
                        )
                    }
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
        bottomBar = {
            if (uiState.candidates.isNotEmpty() && !uiState.isLoading) {
                SurfaceBottomBar(
                    selectedCount = uiState.selectedCount,
                    isImporting = uiState.isImporting,
                    onImportClick = {
                        viewModel.commitImport { /* handled via dialog */ }
                    }
                )
            }
        },
        containerColor = BackgroundWhite
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(BackgroundWhite)
        ) {
            when {
                uiState.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = PrimaryBlue)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Parsing statement & checking duplicates...",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Medium,
                                    color = TextPrimary
                                )
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Computing cryptographic SHA-256 fingerprints",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = TextSecondary,
                                    fontSize = 12.sp
                                )
                            )
                        }
                    }
                }

                uiState.candidates.isEmpty() -> {
                    // Empty / File Selection State
                    InitialPickerView(
                        errorMessage = uiState.errorMessage,
                        onPickFileClick = {
                            filePickerLauncher.launch(
                                arrayOf(
                                    "text/csv",
                                    "text/comma-separated-values",
                                    "application/csv",
                                    "text/*",
                                    "*/*"
                                )
                            )
                        }
                    )
                }

                else -> {
                    // Preview State
                    ImportPreviewView(
                        uiState = uiState,
                        onToggleSelection = { viewModel.toggleSelection(it) },
                        onSelectAllValid = { viewModel.selectAllValid() },
                        onDeselectAll = { viewModel.deselectAll() },
                        onFilterSelected = { viewModel.setFilter(it) },
                        onChooseDifferentFile = {
                            filePickerLauncher.launch(
                                arrayOf(
                                    "text/csv",
                                    "text/comma-separated-values",
                                    "application/csv",
                                    "text/*",
                                    "*/*"
                                )
                            )
                        }
                    )
                }
            }
        }
    }

    // Success Summary Dialog
    uiState.importResult?.let { summary ->
        ImportSummaryDialog(
            result = summary,
            onDismiss = {
                viewModel.dismissSummary()
                viewModel.resetState()
            },
            onViewHistory = {
                viewModel.dismissSummary()
                viewModel.resetState()
                onViewHistory()
            }
        )
    }
}

@Composable
private fun InitialPickerView(
    errorMessage: String?,
    onPickFileClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = CardShape,
            colors = CardDefaults.cardColors(containerColor = SurfaceLight),
            border = BorderStroke(1.dp, BorderLight)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(PillShape)
                        .background(PrimaryBlueLight),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = null,
                        tint = PrimaryBlue,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Select CSV Bank Statement",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Pick a downloaded statement file (.csv). Supports exports from HDFC, SBI, ICICI, Axis, and all major Indian banks.",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = TextSecondary,
                        lineHeight = 18.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                )

                Spacer(modifier = Modifier.height(20.dp))

                PrimaryButton(
                    text = "Choose CSV File",
                    onClick = onPickFileClick
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Privacy Guarantee Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = CardShape,
            colors = CardDefaults.cardColors(containerColor = BackgroundWhite),
            border = BorderStroke(1.dp, BorderLight)
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(PillShape)
                        .background(DemoGreenLight),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = DemoGreen,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Column {
                    Text(
                        text = "100% Local Processing",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    )
                    Text(
                        text = "Your bank statement is parsed directly on your device. No financial records are ever sent to the cloud.",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = TextSecondary,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    )
                }
            }
        }

        if (errorMessage != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = CardShape,
                colors = CardDefaults.cardColors(containerColor = ErrorRedLight),
                border = BorderStroke(1.dp, ErrorRed.copy(alpha = 0.3f))
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ErrorOutline,
                        contentDescription = null,
                        tint = ErrorRed,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = ErrorRed,
                            fontWeight = FontWeight.Medium,
                            lineHeight = 16.sp
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun ImportPreviewView(
    uiState: StatementImportUiState,
    onToggleSelection: (String) -> Unit,
    onSelectAllValid: () -> Unit,
    onDeselectAll: () -> Unit,
    onFilterSelected: (ImportFilterTab) -> Unit,
    onChooseDifferentFile: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(4.dp))

            // File Info Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = CardShape,
                colors = CardDefaults.cardColors(containerColor = SurfaceLight),
                border = BorderStroke(1.dp, BorderLight)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = uiState.fileName ?: "Bank_Statement.csv",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${uiState.totalDetected} rows parsed • ${uiState.validCount} ready • ${uiState.duplicateCount} duplicates",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = TextSecondary,
                                fontSize = 11.sp
                            )
                        )
                    }

                    TextButton(onClick = onChooseDifferentFile) {
                        Text("Change", color = PrimaryBlue, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Filter Chips Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = uiState.activeFilter == ImportFilterTab.ALL,
                    onClick = { onFilterSelected(ImportFilterTab.ALL) },
                    label = { Text("All (${uiState.totalDetected})") }
                )
                FilterChip(
                    selected = uiState.activeFilter == ImportFilterTab.VALID,
                    onClick = { onFilterSelected(ImportFilterTab.VALID) },
                    label = { Text("Ready (${uiState.validCount})") }
                )
                FilterChip(
                    selected = uiState.activeFilter == ImportFilterTab.DUPLICATES,
                    onClick = { onFilterSelected(ImportFilterTab.DUPLICATES) },
                    label = { Text("Duplicates (${uiState.duplicateCount})") }
                )
                FilterChip(
                    selected = uiState.activeFilter == ImportFilterTab.CREDITS,
                    onClick = { onFilterSelected(ImportFilterTab.CREDITS) },
                    label = { Text("Credits (${uiState.creditCount})") }
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Select / Deselect actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${uiState.selectedCount} of ${uiState.totalDetected} selected",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                )

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(onClick = onSelectAllValid) {
                        Text("Select Ready", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    TextButton(onClick = onDeselectAll) {
                        Text("Deselect All", fontSize = 12.sp, color = TextMuted)
                    }
                }
            }

            Spacer(modifier = Modifier.height(2.dp))
        }

        // Transaction items
        items(
            items = uiState.filteredCandidates,
            key = { it.id }
        ) { transaction ->
            ImportableTransactionCard(
                transaction = transaction,
                onToggle = { onToggleSelection(transaction.id) }
            )
        }

        item {
            Spacer(modifier = Modifier.height(84.dp)) // Clearance for bottom bar
        }
    }
}

@Composable
private fun ImportableTransactionCard(
    transaction: ImportableTransaction,
    onToggle: () -> Unit
) {
    val isDuplicate = transaction.duplicateStatus != DuplicateStatus.NONE
    val isInvalid = transaction.validationStatus == RowValidationStatus.INVALID
    val isCredit = transaction.direction == TransactionDirection.CREDIT

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isInvalid) { onToggle() },
        shape = CardShape,
        colors = CardDefaults.cardColors(
            containerColor = if (isDuplicate) SurfaceLight.copy(alpha = 0.6f) else BackgroundWhite
        ),
        border = BorderStroke(
            1.dp,
            if (transaction.isSelected) PrimaryBlue else BorderLight
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Checkbox(
                checked = transaction.isSelected,
                onCheckedChange = { onToggle() },
                enabled = !isInvalid,
                colors = CheckboxDefaults.colors(
                    checkedColor = PrimaryBlue,
                    checkmarkColor = Color.White
                ),
                modifier = Modifier.size(24.dp)
            )

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = transaction.recipientName,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = "${if (isCredit) "+" else "-"} ${PaiseUtils.formatPaiseToCurrency(transaction.amountPaise)}",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = if (isCredit) DemoGreen else TextPrimary
                        )
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = "${transaction.dateFormatted} • ${transaction.description}",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = TextSecondary,
                        fontSize = 11.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Badges Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Direction Badge
                    Box(
                        modifier = Modifier
                            .clip(PillShape)
                            .background(if (isCredit) DemoGreenLight else ErrorRedLight)
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (isCredit) "CREDIT" else "DEBIT",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = if (isCredit) DemoGreen else ErrorRed,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp
                            )
                        )
                    }

                    // Category Chip
                    Box(
                        modifier = Modifier
                            .clip(PillShape)
                            .background(AccentPurpleLight)
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = transaction.category,
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = AccentPurple,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 10.sp
                            )
                        )
                    }

                    // Duplicate / Status Badge
                    when (transaction.duplicateStatus) {
                        DuplicateStatus.EXACT_MATCH_DATABASE -> {
                            Box(
                                modifier = Modifier
                                    .clip(PillShape)
                                    .background(Color(0xFFFEF3C7))
                                    .padding(horizontal = 7.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "IN DATABASE",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = WarningAmber,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp
                                    )
                                )
                            }
                        }
                        DuplicateStatus.DUPLICATE_IN_FILE -> {
                            Box(
                                modifier = Modifier
                                    .clip(PillShape)
                                    .background(Color(0xFFFFEDD5))
                                    .padding(horizontal = 7.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "DUPLICATE IN FILE",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = Color(0xFFEA580C),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp
                                    )
                                )
                            }
                        }
                        DuplicateStatus.NONE -> {
                            if (isInvalid) {
                                Box(
                                    modifier = Modifier
                                        .clip(PillShape)
                                        .background(ErrorRedLight)
                                        .padding(horizontal = 7.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "INVALID",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = ErrorRed,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 10.sp
                                        )
                                    )
                                }
                            }
                        }
                    }
                }

                // Show reason text if duplicate or invalid
                if (transaction.duplicateReason != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Skipped: ${transaction.duplicateReason}",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = WarningAmber,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    )
                } else if (transaction.validationError != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Error: ${transaction.validationError}",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = ErrorRed,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun SurfaceBottomBar(
    selectedCount: Int,
    isImporting: Boolean,
    onImportClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = BackgroundWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        border = BorderStroke(1.dp, BorderLight)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "$selectedCount Selected",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                )
                Text(
                    text = "Duplicates are skipped",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                )
            }

            PrimaryButton(
                text = if (isImporting) "Importing..." else "Import ($selectedCount)",
                onClick = onImportClick,
                enabled = selectedCount > 0 && !isImporting,
                modifier = Modifier.width(160.dp)
            )
        }
    }
}

@Composable
private fun ImportSummaryDialog(
    result: StatementImportResult,
    onDismiss: () -> Unit,
    onViewHistory: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = DemoGreen,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Import Complete",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "File: ${result.fileName}",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = TextSecondary,
                        fontWeight = FontWeight.SemiBold
                    )
                )

                Spacer(modifier = Modifier.height(4.dp))

                SummaryRow("Total Rows Detected", result.totalRowsDetected.toString())
                SummaryRow("Successfully Imported", "${result.successfullyImported} transactions", DemoGreen)
                SummaryRow("Duplicates Skipped", "${result.duplicatesSkipped} rows", WarningAmber)
                if (result.invalidSkipped > 0) {
                    SummaryRow("Invalid Rows Skipped", "${result.invalidSkipped} rows", ErrorRed)
                }
                SummaryRow("Total Expenses Added", PaiseUtils.formatPaiseToCurrency(result.totalDebitPaise), PrimaryBlue)
            }
        },
        confirmButton = {
            PrimaryButton(
                text = "View in History",
                onClick = onViewHistory
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Done", color = TextPrimary)
            }
        },
        containerColor = BackgroundWhite,
        shape = CardShape
    )
}

@Composable
private fun SummaryRow(
    label: String,
    value: String,
    valueColor: Color = TextPrimary
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary))
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(
                fontWeight = FontWeight.Bold,
                color = valueColor
            )
        )
    }
}

private fun getFileName(contentResolver: android.content.ContentResolver, uri: Uri): String? {
    var name: String? = null
    contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (nameIndex != -1 && cursor.moveToFirst()) {
            name = cursor.getString(nameIndex)
        }
    }
    return name
}

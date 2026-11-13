package com.upisegregator.app.ui.screens.analytics

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.upisegregator.app.domain.analytics.AnalyticsPeriod
import com.upisegregator.app.domain.analytics.HighestSpendingDay
import com.upisegregator.app.domain.analytics.PreviousPeriodComparison
import com.upisegregator.app.domain.analytics.SpendingSummary
import com.upisegregator.app.ui.components.PrimaryButton
import com.upisegregator.app.ui.components.SecondaryButton
import com.upisegregator.app.ui.theme.AccentTeal
import com.upisegregator.app.ui.theme.AccentTealLight
import com.upisegregator.app.ui.theme.BackgroundWhite
import com.upisegregator.app.ui.theme.BorderLight
import com.upisegregator.app.ui.theme.CardShape
import com.upisegregator.app.ui.theme.DemoGreen
import com.upisegregator.app.ui.theme.DemoGreenDark
import com.upisegregator.app.ui.theme.DemoGreenLight
import com.upisegregator.app.ui.theme.ErrorRed
import com.upisegregator.app.ui.theme.ErrorRedLight
import com.upisegregator.app.ui.theme.InputFieldShape
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

/**
 * Analytics Dashboard Overview Screen (Milestone 7 — Stage 3).
 *
 * Provides a clean overview of user debit spending for predefined and custom date periods:
 * - Total Eligible Spending (formatted INR from paise)
 * - Transactions Count (successful debits only)
 * - Average Daily Spending (preserving all calendar days)
 * - Previous Period Comparison (with explicit increase/decrease or insufficient history state)
 * - Highest Spending Day
 * - Loading, Empty, Error, and Session-Unavailable states.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AnalyticsViewModel = viewModel(factory = AnalyticsViewModel.Factory)
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()

    // Date picker dialog state for custom date range
    var showStartDatePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }

    // Start Date Picker Dialog
    if (showStartDatePicker) {
        val initialStart = uiState.selectedCustomStartDate ?: System.currentTimeMillis()
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialStart)
        DatePickerDialog(
            onDismissRequest = { showStartDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { pickedStart ->
                            val currentEnd = uiState.selectedCustomEndDate ?: System.currentTimeMillis()
                            viewModel.setCustomDateRange(pickedStart, currentEnd)
                        }
                        showStartDatePicker = false
                    }
                ) {
                    Text("OK", color = PrimaryBlue, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showStartDatePicker = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    // End Date Picker Dialog
    if (showEndDatePicker) {
        val initialEnd = uiState.selectedCustomEndDate ?: System.currentTimeMillis()
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialEnd)
        DatePickerDialog(
            onDismissRequest = { showEndDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { pickedEnd ->
                            val currentStart = uiState.selectedCustomStartDate ?: (pickedEnd - 7L * 24 * 60 * 60 * 1000)
                            viewModel.setCustomDateRange(currentStart, pickedEnd)
                        }
                        showEndDatePicker = false
                    }
                ) {
                    Text("OK", color = PrimaryBlue, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEndDatePicker = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Analytics",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        )
                        Text(
                            text = "Spending overview & trend summary",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = TextSecondary,
                                fontSize = 12.sp
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
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh Analytics",
                            tint = PrimaryBlue
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
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(BackgroundWhite)
        ) {
            // Subtle loading indicator when refreshing existing data
            if (uiState.isLoading && uiState.spendingSummary != null) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = PrimaryBlue,
                    trackColor = PrimaryBlueLight
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // ── Period Selector ────────────────────────────────────────────
                PeriodSelectorSection(
                    selectedPeriod = uiState.selectedPeriod,
                    onSelectPeriod = { period ->
                        if (period == AnalyticsPeriod.CUSTOM) {
                            val now = System.currentTimeMillis()
                            val existingStart = uiState.selectedCustomStartDate ?: (now - 6L * 24 * 60 * 60 * 1000)
                            val existingEnd = uiState.selectedCustomEndDate ?: now
                            viewModel.setCustomDateRange(existingStart, existingEnd)
                        } else {
                            viewModel.selectPeriod(period)
                        }
                    }
                )

                // ── Custom Date Range Picker Controls ─────────────────────────
                if (uiState.isCustomPeriod) {
                    CustomDateRangeSelector(
                        startDateMs = uiState.selectedCustomStartDate,
                        endDateMs = uiState.selectedCustomEndDate,
                        onPickStartDate = { showStartDatePicker = true },
                        onPickEndDate = { showEndDatePicker = true }
                    )
                }

                // ── Active Filter Indicator (if filtered) ──────────────────────
                if (uiState.isFiltered) {
                    ActiveFilterBadge(
                        selectedCategory = uiState.selectedCategory,
                        selectedPaymentMethod = uiState.selectedPaymentMethod,
                        onClearFilters = { viewModel.resetFilters() }
                    )
                }

                // ── Error Banner ───────────────────────────────────────────────
                if (uiState.errorMessage != null && uiState.errorMessage != "Session unavailable") {
                    ErrorStateCard(
                        errorMessage = uiState.errorMessage ?: "Unknown error",
                        onRetry = { viewModel.refresh() }
                    )
                }

                // ── Session Unavailable State ──────────────────────────────────
                if (uiState.errorMessage == "Session unavailable") {
                    SessionUnavailableCard(onNavigateBack = onNavigateBack)
                }

                // ── Main Content Based On Loading / Empty / Summary States ────
                when {
                    uiState.isLoading && uiState.spendingSummary == null -> {
                        LoadingStateOverview()
                    }
                    !uiState.isLoading && !uiState.hasSpending && uiState.errorMessage == null -> {
                        EmptyStateCard(isFiltered = uiState.isFiltered)
                    }
                    uiState.spendingSummary != null -> {
                        val summary = uiState.spendingSummary!!

                        // Overview Metric Cards
                        OverviewMetricsSection(
                            summary = summary,
                            comparison = uiState.previousPeriodComparison
                        )

                        // Highest Spending Day Card
                        HighestSpendingDayCard(highestSpendingDay = uiState.highestSpendingDay)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// UI Subcomponents
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Segmented row of period selector chips.
 */
@Composable
private fun PeriodSelectorSection(
    selectedPeriod: AnalyticsPeriod,
    onSelectPeriod: (AnalyticsPeriod) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val periods = listOf(
        AnalyticsPeriod.THIS_WEEK to "This Week",
        AnalyticsPeriod.THIS_MONTH to "This Month",
        AnalyticsPeriod.LAST_MONTH to "Last Month",
        AnalyticsPeriod.CUSTOM to "Custom"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        periods.forEach { (period, label) ->
            val isSelected = selectedPeriod == period
            Box(
                modifier = Modifier
                    .clip(PillShape)
                    .background(if (isSelected) PrimaryBlue else SurfaceLight)
                    .border(
                        BorderStroke(1.dp, if (isSelected) PrimaryBlue else BorderLight),
                        PillShape
                    )
                    .clickable { onSelectPeriod(period) }
                    .padding(horizontal = 16.dp, vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) BackgroundWhite else TextSecondary,
                        fontSize = 13.sp
                    )
                )
            }
        }
    }
}

/**
 * Interactive card displaying start date and end date buttons with calendar dialogs.
 */
@Composable
private fun CustomDateRangeSelector(
    startDateMs: Long?,
    endDateMs: Long?,
    onPickStartDate: () -> Unit,
    onPickEndDate: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = SurfaceLight),
        border = BorderStroke(1.dp, BorderLight)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = "CUSTOM DATE RANGE",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = TextMuted,
                    letterSpacing = 0.8.sp
                )
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Start Date Field
                DatePickField(
                    label = "From",
                    millis = startDateMs,
                    onClick = onPickStartDate,
                    modifier = Modifier.weight(1f)
                )

                // End Date Field
                DatePickField(
                    label = "To",
                    millis = endDateMs,
                    onClick = onPickEndDate,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun DatePickField(
    label: String,
    millis: Long?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dateText = remember(millis) {
        if (millis != null) {
            SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(millis))
        } else {
            "Select date"
        }
    }

    Box(
        modifier = modifier
            .clip(InputFieldShape)
            .background(BackgroundWhite)
            .border(BorderStroke(1.dp, BorderLight), InputFieldShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Default.CalendarToday,
                contentDescription = null,
                tint = PrimaryBlue,
                modifier = Modifier.size(16.dp)
            )
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = TextMuted,
                        fontSize = 10.sp
                    )
                )
                Text(
                    text = dateText,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp
                    )
                )
            }
        }
    }
}

/**
 * Filter badge indicating that active filters are constraining the overview metrics.
 */
@Composable
private fun ActiveFilterBadge(
    selectedCategory: String?,
    selectedPaymentMethod: String?,
    onClearFilters: () -> Unit,
    modifier: Modifier = Modifier
) {
    val filterText = listOfNotNull(
        selectedCategory?.let { "Category: $it" },
        selectedPaymentMethod?.let { "Method: $it" }
    ).joinToString(" • ")

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(PillShape)
            .background(AccentTealLight)
            .border(BorderStroke(1.dp, AccentTeal.copy(alpha = 0.3f)), PillShape)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = AccentTeal,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = filterText,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = AccentTeal,
                    fontSize = 12.sp
                )
            )
        }
        IconButton(
            onClick = onClearFilters,
            modifier = Modifier.size(24.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Clear Filters",
                tint = AccentTeal,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

/**
 * Section containing the four key overview metrics in a clean 2x2 card layout.
 */
@Composable
private fun OverviewMetricsSection(
    summary: SpendingSummary,
    comparison: PreviousPeriodComparison?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Row 1: Total Spending & Transactions Count
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MetricCard(
                title = "TOTAL SPENDING",
                value = PaiseUtils.formatPaiseToCurrency(summary.totalSpendingPaise),
                subtitle = "Confirmed debit spending",
                icon = Icons.Default.Payments,
                iconTint = PrimaryBlue,
                iconBg = PrimaryBlueLight,
                modifier = Modifier.weight(1f)
            )

            MetricCard(
                title = "TRANSACTIONS",
                value = "${summary.transactionCount}",
                subtitle = "Eligible debits",
                icon = Icons.AutoMirrored.Filled.ReceiptLong,
                iconTint = AccentTeal,
                iconBg = AccentTealLight,
                modifier = Modifier.weight(1f)
            )
        }

        // Row 2: Average Daily Spending & Previous Period Comparison
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MetricCard(
                title = "DAILY AVERAGE",
                value = PaiseUtils.formatPaiseToCurrency(summary.averageDailySpendingPaise),
                subtitle = "Over ${summary.calendarDays} calendar ${if (summary.calendarDays == 1L) "day" else "days"}",
                icon = Icons.Default.CalendarToday,
                iconTint = DemoGreen,
                iconBg = DemoGreenLight,
                modifier = Modifier.weight(1f)
            )

            ComparisonMetricCard(
                comparison = comparison,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * Standard Metric Card for high-level statistics.
 */
@Composable
private fun MetricCard(
    title: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    iconTint: Color,
    iconBg: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = BackgroundWhite),
        border = BorderStroke(1.dp, BorderLight)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = TextMuted,
                        letterSpacing = 0.6.sp,
                        fontSize = 11.sp
                    )
                )
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(PillShape)
                        .background(iconBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.ExtraBold,
                    color = TextPrimary,
                    fontSize = 18.sp
                )
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = TextSecondary,
                    fontSize = 11.sp
                )
            )
        }
    }
}

/**
 * Specialized Metric Card for Previous Period Comparison.
 */
@Composable
private fun ComparisonMetricCard(
    comparison: PreviousPeriodComparison?,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = BackgroundWhite),
        border = BorderStroke(1.dp, BorderLight)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "VS PREVIOUS",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = TextMuted,
                        letterSpacing = 0.6.sp,
                        fontSize = 11.sp
                    )
                )

                val (icon, tint, bg) = when (comparison) {
                    is PreviousPeriodComparison.Available -> {
                        when {
                            comparison.changePercent > 0 -> Triple(Icons.AutoMirrored.Filled.TrendingUp, ErrorRed, ErrorRedLight)
                            comparison.changePercent < 0 -> Triple(Icons.AutoMirrored.Filled.TrendingDown, DemoGreen, DemoGreenLight)
                            else -> Triple(Icons.Default.Info, TextSecondary, SurfaceLight)
                        }
                    }
                    else -> Triple(Icons.Default.Info, TextMuted, SurfaceLight)
                }

                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(PillShape)
                        .background(bg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            when (comparison) {
                is PreviousPeriodComparison.Available -> {
                    val sign = if (comparison.changePercent > 0) "+" else ""
                    val valueColor = when {
                        comparison.changePercent > 0 -> ErrorRed
                        comparison.changePercent < 0 -> DemoGreenDark
                        else -> TextPrimary
                    }

                    Text(
                        text = "$sign${comparison.changePercent}%",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = valueColor,
                            fontSize = 18.sp
                        )
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "Prev: ${PaiseUtils.formatPaiseToCurrency(comparison.previousSpendingPaise)}",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                    )
                }
                is PreviousPeriodComparison.InsufficientHistory, null -> {
                    Text(
                        text = "No Baseline",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextMuted,
                            fontSize = 16.sp
                        )
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "Zero previous spending",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                    )
                }
            }
        }
    }
}

/**
 * Card highlighting the highest spending day in the selected period.
 */
@Composable
private fun HighestSpendingDayCard(
    highestSpendingDay: HighestSpendingDay?,
    modifier: Modifier = Modifier
) {
    val hasPeak = highestSpendingDay != null && highestSpendingDay.totalPaise > 0L

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = BackgroundWhite),
        border = BorderStroke(1.dp, BorderLight)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "HIGHEST SPENDING DAY",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = TextMuted,
                        letterSpacing = 0.8.sp
                    )
                )

                Spacer(modifier = Modifier.height(6.dp))

                if (hasPeak) {
                    val dateFormatted = try {
                        val parser = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                        val formatter = SimpleDateFormat("EEEE, dd MMMM yyyy", Locale.getDefault())
                        val parsed = parser.parse(highestSpendingDay!!.dateLabel)
                        if (parsed != null) formatter.format(parsed) else highestSpendingDay.dateLabel
                    } catch (_: Exception) {
                        highestSpendingDay!!.dateLabel
                    }

                    Text(
                        text = PaiseUtils.formatPaiseToCurrency(highestSpendingDay!!.totalPaise),
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = TextPrimary
                        )
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = dateFormatted,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    )
                } else {
                    Text(
                        text = "No spending spike",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = TextMuted
                        )
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = "No debit transactions recorded in this period",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                    )
                }
            }

            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(PillShape)
                    .background(if (hasPeak) PrimaryBlueLight else SurfaceLight),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = null,
                    tint = if (hasPeak) PrimaryBlue else TextMuted,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

/**
 * Loading state skeleton / progress indicator.
 */
@Composable
private fun LoadingStateOverview(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(220.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(36.dp),
                color = PrimaryBlue,
                strokeWidth = 3.dp
            )
            Text(
                text = "Calculating period analytics…",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            )
        }
    }
}

/**
 * Friendly empty state card when there are zero eligible transactions.
 */
@Composable
private fun EmptyStateCard(
    isFiltered: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = SurfaceLight),
        border = BorderStroke(1.dp, BorderLight)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(PillShape)
                    .background(PrimaryBlueLight),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ReceiptLong,
                    contentDescription = null,
                    tint = PrimaryBlue,
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = if (isFiltered) "No Matching Spending Found" else "No Spending in This Period",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = if (isFiltered) {
                    "No successful debit transactions match the active category or payment method filters for this period."
                } else {
                    "There are no successful debit transactions recorded for this period. Spending via UPI or manual expenses will appear here."
                },
                style = MaterialTheme.typography.bodySmall.copy(
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp
                ),
                modifier = Modifier.padding(horizontal = 12.dp)
            )
        }
    }
}

/**
 * Error state card with retry action.
 */
@Composable
private fun ErrorStateCard(
    errorMessage: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = ErrorRedLight),
        border = BorderStroke(1.dp, ErrorRed.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = ErrorRed,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "Failed to load analytics",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = ErrorRed
                    )
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = TextPrimary,
                    fontSize = 12.sp
                )
            )

            Spacer(modifier = Modifier.height(10.dp))

            PrimaryButton(
                text = "Retry",
                onClick = onRetry,
                containerColor = ErrorRed,
                modifier = Modifier.height(40.dp)
            )
        }
    }
}

/**
 * State card presented when user session is not available.
 */
@Composable
private fun SessionUnavailableCard(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = SurfaceLight),
        border = BorderStroke(1.dp, BorderLight)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Session Unavailable",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Please log in with a user account or enter Demo Mode to view analytics.",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = TextSecondary,
                    textAlign = TextAlign.Center
                )
            )

            Spacer(modifier = Modifier.height(14.dp))

            SecondaryButton(
                text = "Return",
                onClick = onNavigateBack,
                modifier = Modifier.height(44.dp)
            )
        }
    }
}

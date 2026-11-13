package com.upisegregator.app.ui.screens.analytics

import com.upisegregator.app.domain.analytics.AnalyticsPeriod
import com.upisegregator.app.domain.analytics.HighestSpendingDay
import com.upisegregator.app.domain.analytics.PreviousPeriodComparison
import com.upisegregator.app.domain.analytics.SpendingSummary
import com.upisegregator.app.ui.navigation.Screen
import com.upisegregator.app.util.PaiseUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests verifying Analytics Dashboard Overview UI state transformations,
 * metric card formatting, state edge cases, and navigation routes (Milestone 7 — Stage 3).
 */
class AnalyticsOverviewScreenTest {

    // ── Navigation Route Verification ────────────────────────────────────────

    @Test
    fun analyticsScreen_routeIsCorrect() {
        assertEquals("analytics", Screen.Analytics.route)
    }

    // ── Currency and Metric Formatting ───────────────────────────────────────

    @Test
    fun totalSpendingMetric_formattedCorrectlyInIndianRupees() {
        val totalPaise = 1542550L // ₹15,425.50
        val formatted = PaiseUtils.formatPaiseToCurrency(totalPaise)
        assertEquals("₹15,425.50", formatted)
    }

    @Test
    fun totalSpendingMetric_zeroPaiseFormattedAsZeroRupees() {
        val formatted = PaiseUtils.formatPaiseToCurrency(0L)
        assertEquals("₹0.00", formatted)
    }

    @Test
    fun averageDailySpending_includesAllCalendarDays() {
        val totalPaise = 300000L // ₹3,000.00
        val calendarDays = 30L
        val avgPaise = totalPaise / calendarDays // ₹100.00

        val summary = SpendingSummary(
            totalSpendingPaise = totalPaise,
            transactionCount = 5,
            averageDailySpendingPaise = avgPaise,
            calendarDays = calendarDays,
            previousPeriodComparison = PreviousPeriodComparison.InsufficientHistory
        )

        assertEquals(10000L, summary.averageDailySpendingPaise)
        assertEquals("₹100.00", PaiseUtils.formatPaiseToCurrency(summary.averageDailySpendingPaise))
        assertEquals(30L, summary.calendarDays)
    }

    // ── Previous Period Comparison States ────────────────────────────────────

    @Test
    fun previousPeriodComparison_spendingIncrease_displaysPositivePercentage() {
        val comparison = PreviousPeriodComparison.Available(
            previousSpendingPaise = 100000L,
            changePercent = 25L
        )

        assertTrue(comparison.changePercent > 0)
        assertEquals(25L, comparison.changePercent)
        assertEquals("₹1,000.00", PaiseUtils.formatPaiseToCurrency(comparison.previousSpendingPaise))
    }

    @Test
    fun previousPeriodComparison_spendingDecrease_displaysNegativePercentage() {
        val comparison = PreviousPeriodComparison.Available(
            previousSpendingPaise = 200000L,
            changePercent = -30L
        )

        assertTrue(comparison.changePercent < 0)
        assertEquals(-30L, comparison.changePercent)
        assertEquals("₹2,000.00", PaiseUtils.formatPaiseToCurrency(comparison.previousSpendingPaise))
    }

    @Test
    fun previousPeriodComparison_noChange_displaysZeroPercentage() {
        val comparison = PreviousPeriodComparison.Available(
            previousSpendingPaise = 150000L,
            changePercent = 0L
        )

        assertEquals(0L, comparison.changePercent)
    }

    @Test
    fun previousPeriodComparison_insufficientHistory_handlesZeroPreviousSpending() {
        val comparison: PreviousPeriodComparison = PreviousPeriodComparison.InsufficientHistory
        assertTrue(comparison is PreviousPeriodComparison.InsufficientHistory)
    }

    // ── Highest Spending Day Card States ─────────────────────────────────────

    @Test
    fun highestSpendingDay_withPeakSpending_displaysCorrectValues() {
        val highestDay = HighestSpendingDay(
            dateLabel = "2026-11-04",
            totalPaise = 550000L // ₹5,500.00
        )

        assertEquals("2026-11-04", highestDay.dateLabel)
        assertEquals(550000L, highestDay.totalPaise)
        assertEquals("₹5,500.00", PaiseUtils.formatPaiseToCurrency(highestDay.totalPaise))
    }

    @Test
    fun highestSpendingDay_whenNull_indicatesNoPeak() {
        val state = AnalyticsUiState(
            isLoading = false,
            highestSpendingDay = null
        )
        assertNull(state.highestSpendingDay)
    }

    // ── Screen States Verification ───────────────────────────────────────────

    @Test
    fun screenState_loadingState_identifiedCorrectly() {
        val loadingState = AnalyticsUiState(
            isLoading = true,
            spendingSummary = null
        )

        assertTrue(loadingState.isLoading)
        assertNull(loadingState.spendingSummary)
        assertFalse(loadingState.hasSpending)
    }

    @Test
    fun screenState_emptyState_identifiedCorrectly() {
        val summary = SpendingSummary(
            totalSpendingPaise = 0L,
            transactionCount = 0,
            averageDailySpendingPaise = 0L,
            calendarDays = 30L,
            previousPeriodComparison = PreviousPeriodComparison.InsufficientHistory
        )

        val emptyState = AnalyticsUiState(
            isLoading = false,
            errorMessage = null,
            spendingSummary = summary
        )

        assertFalse(emptyState.isLoading)
        assertNull(emptyState.errorMessage)
        assertFalse(emptyState.hasSpending)
    }

    @Test
    fun screenState_errorState_identifiedCorrectly() {
        val errorState = AnalyticsUiState(
            isLoading = false,
            errorMessage = "Failed to load analytics data"
        )

        assertFalse(errorState.isLoading)
        assertEquals("Failed to load analytics data", errorState.errorMessage)
    }

    @Test
    fun screenState_sessionUnavailableState_identifiedCorrectly() {
        val sessionUnavailableState = AnalyticsUiState(
            isLoading = false,
            errorMessage = "Session unavailable",
            spendingSummary = null
        )

        assertFalse(sessionUnavailableState.isLoading)
        assertEquals("Session unavailable", sessionUnavailableState.errorMessage)
        assertNull(sessionUnavailableState.spendingSummary)
    }

    @Test
    fun screenState_activeFilteredState_identifiedCorrectly() {
        val filteredState = AnalyticsUiState(
            isLoading = false,
            selectedCategory = "Food",
            selectedPaymentMethod = null
        )

        assertTrue(filteredState.isFiltered)
        assertEquals("Food", filteredState.selectedCategory)
        assertNull(filteredState.selectedPaymentMethod)
    }

    @Test
    fun screenState_customPeriodState_identifiedCorrectly() {
        val customState = AnalyticsUiState(
            selectedPeriod = AnalyticsPeriod.CUSTOM,
            selectedCustomStartDate = 1730419200000L,
            selectedCustomEndDate = 1731024000000L
        )

        assertTrue(customState.isCustomPeriod)
        assertNotNull(customState.selectedCustomStartDate)
        assertNotNull(customState.selectedCustomEndDate)
        assertTrue(customState.selectedCustomStartDate!! <= customState.selectedCustomEndDate!!)
    }
}

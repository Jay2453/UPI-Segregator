package com.upisegregator.app.domain.analytics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Tests for analytics domain model validation and constraints.
 */
class AnalyticsDomainModelsTest {

    @Test
    fun resolvedDateRange_validRange_createsSuccessfully() {
        val range = ResolvedDateRange(startMs = 1000L, endMs = 2000L, calendarDays = 1L)
        assertEquals(1000L, range.startMs)
        assertEquals(2000L, range.endMs)
        assertEquals(1L, range.calendarDays)
    }

    @Test
    fun resolvedDateRange_startAfterEnd_throwsException() {
        assertThrows(IllegalArgumentException::class.java) {
            ResolvedDateRange(startMs = 2000L, endMs = 1000L, calendarDays = 1L)
        }
    }

    @Test
    fun resolvedDateRange_zeroDays_throwsException() {
        assertThrows(IllegalArgumentException::class.java) {
            ResolvedDateRange(startMs = 1000L, endMs = 2000L, calendarDays = 0L)
        }
    }

    @Test
    fun resolvedDateRange_negativeDays_throwsException() {
        assertThrows(IllegalArgumentException::class.java) {
            ResolvedDateRange(startMs = 1000L, endMs = 2000L, calendarDays = -1L)
        }
    }

    @Test
    fun resolvedDateRange_sameStartAndEnd_validIfOneDay() {
        val range = ResolvedDateRange(startMs = 1000L, endMs = 1000L, calendarDays = 1L)
        assertEquals(1000L, range.startMs)
    }

    @Test
    fun spendingSummary_zeroSpending_createsSuccessfully() {
        val summary = SpendingSummary(
            totalSpendingPaise = 0L,
            transactionCount = 0,
            averageDailySpendingPaise = 0L,
            calendarDays = 7L,
            previousPeriodComparison = PreviousPeriodComparison.InsufficientHistory
        )
        assertEquals(0L, summary.totalSpendingPaise)
        assertEquals(0, summary.transactionCount)
    }

    @Test
    fun previousPeriodComparison_available_holdsValues() {
        val comparison = PreviousPeriodComparison.Available(
            previousSpendingPaise = 50000L,
            changePercent = 25L
        )
        assertEquals(50000L, comparison.previousSpendingPaise)
        assertEquals(25L, comparison.changePercent)
    }

    @Test
    fun categorySpending_percentageCalculation() {
        val spending = CategorySpending(
            normalizedCategory = "food",
            displayName = "Food",
            totalPaise = 25000L,
            transactionCount = 5,
            percentage = 50L // 25000 out of 50000 total
        )
        assertEquals(50L, spending.percentage)
    }
}

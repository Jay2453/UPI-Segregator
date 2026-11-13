package com.upisegregator.app.domain.analytics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [PreviousPeriodComparison] logic extracted for unit verification.
 *
 * The actual comparison is computed in AnalyticsRepositoryImpl; here we
 * test the formula and edge cases in isolation.
 */
class PreviousPeriodComparisonTest {

    /**
     * Computes the comparison result the same way AnalyticsRepositoryImpl does.
     */
    private fun computeComparison(
        currentSpendingPaise: Long,
        previousSpendingPaise: Long
    ): PreviousPeriodComparison {
        return if (previousSpendingPaise == 0L) {
            PreviousPeriodComparison.InsufficientHistory
        } else {
            val changePercent = ((currentSpendingPaise - previousSpendingPaise) * 100L) / previousSpendingPaise
            PreviousPeriodComparison.Available(
                previousSpendingPaise = previousSpendingPaise,
                changePercent = changePercent
            )
        }
    }

    @Test
    fun zeroPreviousSpending_returnsInsufficientHistory() {
        val result = computeComparison(currentSpendingPaise = 50000L, previousSpendingPaise = 0L)
        assertTrue(result is PreviousPeriodComparison.InsufficientHistory)
    }

    @Test
    fun bothZero_returnsInsufficientHistory() {
        val result = computeComparison(currentSpendingPaise = 0L, previousSpendingPaise = 0L)
        assertTrue(result is PreviousPeriodComparison.InsufficientHistory)
    }

    @Test
    fun spendingIncreased_positivePercent() {
        // Previous: ₹100 (10000 paise), Current: ₹150 (15000 paise) → +50%
        val result = computeComparison(15000L, 10000L) as PreviousPeriodComparison.Available
        assertEquals(50L, result.changePercent)
        assertEquals(10000L, result.previousSpendingPaise)
    }

    @Test
    fun spendingDecreased_negativePercent() {
        // Previous: ₹200 (20000 paise), Current: ₹100 (10000 paise) → -50%
        val result = computeComparison(10000L, 20000L) as PreviousPeriodComparison.Available
        assertEquals(-50L, result.changePercent)
    }

    @Test
    fun spendingSame_zeroPercent() {
        val result = computeComparison(10000L, 10000L) as PreviousPeriodComparison.Available
        assertEquals(0L, result.changePercent)
    }

    @Test
    fun currentZeroPreviousNonZero_negativeHundredPercent() {
        val result = computeComparison(0L, 10000L) as PreviousPeriodComparison.Available
        assertEquals(-100L, result.changePercent)
    }

    @Test
    fun spendingDoubled_hundredPercent() {
        val result = computeComparison(20000L, 10000L) as PreviousPeriodComparison.Available
        assertEquals(100L, result.changePercent)
    }

    @Test
    fun smallChange_integerTruncation() {
        // Previous: 10000, Current: 10001 → 0.01% → truncated to 0%
        val result = computeComparison(10001L, 10000L) as PreviousPeriodComparison.Available
        assertEquals(0L, result.changePercent)
    }

    @Test
    fun largeValues_noOverflow() {
        // Test with large paise values (₹1,00,00,000 = 1 crore)
        val oneCrore = 100_000_000_00L // 1 crore in paise
        val twoCrore = 200_000_000_00L
        val result = computeComparison(twoCrore, oneCrore) as PreviousPeriodComparison.Available
        assertEquals(100L, result.changePercent)
    }
}

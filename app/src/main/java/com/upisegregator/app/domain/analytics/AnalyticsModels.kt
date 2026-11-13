package com.upisegregator.app.domain.analytics

/**
 * Domain models for the Analytics Dashboard (Milestone 7).
 *
 * All monetary values are Long integer paise (1 INR = 100 paise).
 * These models are presentation-ready and do not depend on Room entities.
 */

// ─── Date Period ────────────────────────────────────────────────────────────

/**
 * Represents a selectable analytics time period.
 */
enum class AnalyticsPeriod {
    THIS_WEEK,
    THIS_MONTH,
    LAST_MONTH,
    CUSTOM
}

/**
 * Inclusive millisecond boundaries for a resolved date range.
 *
 * @property startMs Start of the first calendar day (00:00:00.000 device-local).
 * @property endMs   End of the last calendar day (23:59:59.999 device-local).
 * @property calendarDays Number of calendar days in the range (inclusive).
 */
data class ResolvedDateRange(
    val startMs: Long,
    val endMs: Long,
    val calendarDays: Long
) {
    init {
        require(startMs <= endMs) { "startMs ($startMs) must be <= endMs ($endMs)" }
        require(calendarDays >= 1L) { "calendarDays must be at least 1, was $calendarDays" }
    }
}

// ─── Summary ────────────────────────────────────────────────────────────────

/**
 * High-level spending summary for the selected period.
 */
data class SpendingSummary(
    /** Total spending in paise. */
    val totalSpendingPaise: Long,
    /** Count of successful debit transactions. */
    val transactionCount: Int,
    /** Average daily spending in paise (totalSpendingPaise / calendarDays). */
    val averageDailySpendingPaise: Long,
    /** Number of calendar days in the selected period. */
    val calendarDays: Long,
    /** Comparison with the previous equivalent period. */
    val previousPeriodComparison: PreviousPeriodComparison
)

/**
 * Comparison to the previous equivalent period.
 */
sealed interface PreviousPeriodComparison {
    /**
     * Sufficient data exists; both current and previous period spending are available.
     *
     * @property previousSpendingPaise Total spending in the previous period (paise).
     * @property changePercent         Percentage change: ((current − previous) / previous) × 100.
     *                                  Positive = spending increased, negative = decreased.
     */
    data class Available(
        val previousSpendingPaise: Long,
        val changePercent: Long
    ) : PreviousPeriodComparison

    /**
     * Previous period had zero spending — percentage change is undefined.
     * UI should display "No data for previous period" or similar.
     */
    data object InsufficientHistory : PreviousPeriodComparison
}

// ─── Breakdowns ─────────────────────────────────────────────────────────────

/**
 * Spending breakdown by category.
 */
data class CategorySpending(
    /** Normalised key (lowercase, trimmed). */
    val normalizedCategory: String,
    /** Human-readable display name. */
    val displayName: String,
    /** Total spending in paise. */
    val totalPaise: Long,
    /** Number of transactions. */
    val transactionCount: Int,
    /** Percentage of total spending. */
    val percentage: Long
)

/**
 * Spending breakdown by payment method.
 */
data class PaymentMethodSpending(
    val paymentMethod: String,
    val totalPaise: Long,
    val transactionCount: Int,
    val percentage: Long
)

/**
 * Spending breakdown by recipient.
 */
data class RecipientSpending(
    val recipientName: String,
    val totalPaise: Long,
    val transactionCount: Int,
    val percentage: Long
)

// ─── Trends ─────────────────────────────────────────────────────────────────

/**
 * Daily spending bucket.
 */
data class DailySpending(
    /** Date label (yyyy-MM-dd). */
    val dateLabel: String,
    /** Total spending on this day in paise. */
    val totalPaise: Long
)

/**
 * Weekly spending bucket.
 */
data class WeeklySpending(
    /** Week label (e.g. "Nov 04 – Nov 10"). */
    val weekLabel: String,
    /** Total spending in the week in paise. */
    val totalPaise: Long
)

/**
 * Monthly spending bucket.
 */
data class MonthlySpending(
    /** Month label (e.g. "Nov 2026"). */
    val monthLabel: String,
    /** Total spending in the month in paise. */
    val totalPaise: Long
)

/**
 * The highest-spending day in the selected period.
 */
data class HighestSpendingDay(
    val dateLabel: String,
    val totalPaise: Long
)

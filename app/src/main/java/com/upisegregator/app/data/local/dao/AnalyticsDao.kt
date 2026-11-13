package com.upisegregator.app.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import com.upisegregator.app.data.local.entity.PaymentMethod
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object dedicated to analytics aggregation queries.
 *
 * CRITICAL INVARIANTS:
 * - Every spending query filters by `transactionStatus = 'SUCCESS' AND direction = 'DEBIT'`.
 * - Every query is scoped to `userId` to isolate demo / authenticated user data.
 * - All transaction sources (MANUAL, UPI_INTENT, CSV_IMPORT, PDF_IMPORT, DEMO) are included;
 *   only status + direction + userId are filtered.
 * - Category grouping is case-insensitive using `LOWER(TRIM(category))`.
 * - All monetary amounts are Long integer paise.
 */
@Dao
interface AnalyticsDao {

    // ─── Summary Aggregates ────────────────────────────────────────────────

    /**
     * Total spending (paise) for the given user within the date range [startMs, endMs] inclusive.
     */
    @Query(
        """
        SELECT COALESCE(SUM(amountPaise), 0)
        FROM transactions
        WHERE userId = :userId
          AND transactionStatus = 'SUCCESS'
          AND direction = 'DEBIT'
          AND transactionDate >= :startMs
          AND transactionDate <= :endMs
        """
    )
    fun getTotalSpending(userId: String, startMs: Long, endMs: Long): Flow<Long>

    /**
     * Suspend variant of [getTotalSpending] for one-shot calculations (e.g. previous period).
     */
    @Query(
        """
        SELECT COALESCE(SUM(amountPaise), 0)
        FROM transactions
        WHERE userId = :userId
          AND transactionStatus = 'SUCCESS'
          AND direction = 'DEBIT'
          AND transactionDate >= :startMs
          AND transactionDate <= :endMs
        """
    )
    suspend fun getTotalSpendingSuspend(userId: String, startMs: Long, endMs: Long): Long

    /**
     * Count of successful debit transactions in [startMs, endMs].
     */
    @Query(
        """
        SELECT COUNT(*)
        FROM transactions
        WHERE userId = :userId
          AND transactionStatus = 'SUCCESS'
          AND direction = 'DEBIT'
          AND transactionDate >= :startMs
          AND transactionDate <= :endMs
        """
    )
    fun getTransactionCount(userId: String, startMs: Long, endMs: Long): Flow<Int>

    /**
     * Suspend variant of [getTransactionCount] for one-shot calculations.
     */
    @Query(
        """
        SELECT COUNT(*)
        FROM transactions
        WHERE userId = :userId
          AND transactionStatus = 'SUCCESS'
          AND direction = 'DEBIT'
          AND transactionDate >= :startMs
          AND transactionDate <= :endMs
        """
    )
    suspend fun getTransactionCountSuspend(userId: String, startMs: Long, endMs: Long): Int

    // ─── Breakdown Queries ─────────────────────────────────────────────────

    /**
     * Category-wise spending breakdown, grouped case-insensitively.
     *
     * Returns [CategorySpendingRow] with the normalised key (`LOWER(TRIM(category))`)
     * and a representative display name (the raw `category` value from any matching row).
     * Ordered by total descending.
     */
    @Query(
        """
        SELECT LOWER(TRIM(category)) AS normalizedCategory,
               category              AS displayName,
               SUM(amountPaise)      AS totalPaise,
               COUNT(*)              AS transactionCount
        FROM transactions
        WHERE userId = :userId
          AND transactionStatus = 'SUCCESS'
          AND direction = 'DEBIT'
          AND transactionDate >= :startMs
          AND transactionDate <= :endMs
        GROUP BY normalizedCategory
        ORDER BY totalPaise DESC
        """
    )
    fun getCategoryBreakdown(
        userId: String,
        startMs: Long,
        endMs: Long
    ): Flow<List<CategorySpendingRow>>

    /**
     * Payment-method-wise spending breakdown, ordered by total descending.
     */
    @Query(
        """
        SELECT paymentMethod       AS paymentMethod,
               SUM(amountPaise)    AS totalPaise,
               COUNT(*)            AS transactionCount
        FROM transactions
        WHERE userId = :userId
          AND transactionStatus = 'SUCCESS'
          AND direction = 'DEBIT'
          AND transactionDate >= :startMs
          AND transactionDate <= :endMs
        GROUP BY paymentMethod
        ORDER BY totalPaise DESC
        """
    )
    fun getPaymentMethodBreakdown(
        userId: String,
        startMs: Long,
        endMs: Long
    ): Flow<List<PaymentMethodSpendingRow>>

    /**
     * Top-N recipient-wise spending breakdown, ordered by total descending.
     */
    @Query(
        """
        SELECT recipientName       AS recipientName,
               SUM(amountPaise)    AS totalPaise,
               COUNT(*)            AS transactionCount
        FROM transactions
        WHERE userId = :userId
          AND transactionStatus = 'SUCCESS'
          AND direction = 'DEBIT'
          AND transactionDate >= :startMs
          AND transactionDate <= :endMs
        GROUP BY recipientName
        ORDER BY totalPaise DESC
        LIMIT :limit
        """
    )
    fun getRecipientBreakdown(
        userId: String,
        startMs: Long,
        endMs: Long,
        limit: Int = 10
    ): Flow<List<RecipientSpendingRow>>

    // ─── Trend Queries ─────────────────────────────────────────────────────

    /**
     * Daily spending bucketed by local-date string (yyyy-MM-dd).
     *
     * SQLite does not natively know the device timezone, so bucketing is done
     * at the repository / domain layer. This query returns raw per-transaction
     * timestamps and amounts for the period so the repository can bucket them
     * correctly using the device-local timezone.
     */
    @Query(
        """
        SELECT transactionDate AS timestampMs,
               amountPaise     AS amountPaise
        FROM transactions
        WHERE userId = :userId
          AND transactionStatus = 'SUCCESS'
          AND direction = 'DEBIT'
          AND transactionDate >= :startMs
          AND transactionDate <= :endMs
        ORDER BY transactionDate ASC
        """
    )
    suspend fun getSpendingTimestamps(
        userId: String,
        startMs: Long,
        endMs: Long
    ): List<TimestampAmountRow>

    /**
     * Reactive stream of all eligible debit transactions for the period.
     * Room automatically re-emits whenever matching records in [transactions] change.
     */
    @Query(
        """
        SELECT transactionDate,
               amountPaise,
               category,
               paymentMethod,
               recipientName
        FROM transactions
        WHERE userId = :userId
          AND transactionStatus = 'SUCCESS'
          AND direction = 'DEBIT'
          AND transactionDate >= :startMs
          AND transactionDate <= :endMs
        ORDER BY transactionDate ASC
        """
    )
    fun getEligibleTransactionsFlow(
        userId: String,
        startMs: Long,
        endMs: Long
    ): Flow<List<EligibleTransactionRow>>
}

// ─── Projection Data Classes ────────────────────────────────────────────────

/**
 * Raw eligible transaction row for reactive multi-dimensional analytics.
 */
data class EligibleTransactionRow(
    val transactionDate: Long,
    val amountPaise: Long,
    val category: String,
    val paymentMethod: PaymentMethod,
    val recipientName: String
)

/**
 * Row returned by category breakdown query.
 */
data class CategorySpendingRow(
    val normalizedCategory: String,
    val displayName: String,
    val totalPaise: Long,
    val transactionCount: Int
)

/**
 * Row returned by payment method breakdown query.
 */
data class PaymentMethodSpendingRow(
    val paymentMethod: String,
    val totalPaise: Long,
    val transactionCount: Int
)

/**
 * Row returned by recipient breakdown query.
 */
data class RecipientSpendingRow(
    val recipientName: String,
    val totalPaise: Long,
    val transactionCount: Int
)

/**
 * Raw timestamp + amount row for client-side local-date bucketing.
 */
data class TimestampAmountRow(
    val timestampMs: Long,
    val amountPaise: Long
)

package com.upisegregator.app.data.local.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.upisegregator.app.data.local.database.AppDatabase
import com.upisegregator.app.data.local.entity.PaymentMethod
import com.upisegregator.app.data.local.entity.TransactionDirection
import com.upisegregator.app.data.local.entity.TransactionEntity
import com.upisegregator.app.data.local.entity.TransactionSource
import com.upisegregator.app.data.local.entity.TransactionStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar
import java.util.TimeZone
import java.util.UUID

/**
 * Integration tests for [AnalyticsDao] using Robolectric + in-memory Room database.
 *
 * Verifies:
 * - Only SUCCESS + DEBIT transactions count as spending.
 * - User isolation between demo and real users.
 * - All eligible sources (MANUAL, UPI_INTENT, CSV_IMPORT, PDF_IMPORT, DEMO) are included.
 * - Category grouping is case-insensitive.
 * - Recipient limit is respected.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class AnalyticsDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var analyticsDao: AnalyticsDao
    private lateinit var transactionDao: TransactionDao

    private val tz = TimeZone.getTimeZone("Asia/Kolkata")
    private val testUserId = "test_user_123"
    private val demoUserId = "demo_user"

    // Date range: Nov 1-7, 2024
    private val rangeStartMs: Long by lazy {
        calendarMs(2024, Calendar.NOVEMBER, 1, 0, 0, 0)
    }
    private val rangeEndMs: Long by lazy {
        calendarMs(2024, Calendar.NOVEMBER, 7, 23, 59, 59)
    }

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        analyticsDao = db.analyticsDao()
        transactionDao = db.transactionDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    // ─── Total Spending ────────────────────────────────────────────────────

    @Test
    fun getTotalSpending_onlySuccessDebitTransactions() = runBlocking {
        // Insert various status/direction combinations
        insertTx(status = TransactionStatus.SUCCESS, direction = TransactionDirection.DEBIT, amount = 10000L)
        insertTx(status = TransactionStatus.SUCCESS, direction = TransactionDirection.DEBIT, amount = 5000L)
        insertTx(status = TransactionStatus.SUCCESS, direction = TransactionDirection.CREDIT, amount = 20000L) // excluded
        insertTx(status = TransactionStatus.PENDING, direction = TransactionDirection.DEBIT, amount = 3000L)  // excluded
        insertTx(status = TransactionStatus.FAILED, direction = TransactionDirection.DEBIT, amount = 7000L)   // excluded
        insertTx(status = TransactionStatus.CANCELLED, direction = TransactionDirection.DEBIT, amount = 4000L) // excluded
        insertTx(status = TransactionStatus.UNKNOWN, direction = TransactionDirection.DEBIT, amount = 2000L)  // excluded

        val total = analyticsDao.getTotalSpending(testUserId, rangeStartMs, rangeEndMs).first()
        assertEquals(15000L, total) // Only 10000 + 5000
    }

    @Test
    fun getTotalSpending_noTransactions_returnsZero() = runBlocking {
        val total = analyticsDao.getTotalSpending(testUserId, rangeStartMs, rangeEndMs).first()
        assertEquals(0L, total)
    }

    // ─── User Isolation ────────────────────────────────────────────────────

    @Test
    fun getTotalSpending_isolatesUserFromDemo() = runBlocking {
        insertTx(userId = testUserId, amount = 10000L)
        insertTx(userId = demoUserId, amount = 20000L)

        val userTotal = analyticsDao.getTotalSpending(testUserId, rangeStartMs, rangeEndMs).first()
        val demoTotal = analyticsDao.getTotalSpending(demoUserId, rangeStartMs, rangeEndMs).first()

        assertEquals(10000L, userTotal)
        assertEquals(20000L, demoTotal)
    }

    @Test
    fun getTotalSpending_differentUsers_completeIsolation() = runBlocking {
        insertTx(userId = "user_a", amount = 5000L)
        insertTx(userId = "user_b", amount = 8000L)

        val totalA = analyticsDao.getTotalSpending("user_a", rangeStartMs, rangeEndMs).first()
        val totalB = analyticsDao.getTotalSpending("user_b", rangeStartMs, rangeEndMs).first()
        val totalC = analyticsDao.getTotalSpending("user_c", rangeStartMs, rangeEndMs).first()

        assertEquals(5000L, totalA)
        assertEquals(8000L, totalB)
        assertEquals(0L, totalC)
    }

    // ─── All Sources Included ──────────────────────────────────────────────

    @Test
    fun getTotalSpending_includesAllTransactionSources() = runBlocking {
        insertTx(source = TransactionSource.MANUAL, amount = 1000L)
        insertTx(source = TransactionSource.UPI_INTENT, amount = 2000L)
        insertTx(source = TransactionSource.CSV_IMPORT, amount = 3000L)
        insertTx(source = TransactionSource.PDF_IMPORT, amount = 4000L)
        insertTx(source = TransactionSource.DEMO, amount = 5000L)

        val total = analyticsDao.getTotalSpending(testUserId, rangeStartMs, rangeEndMs).first()
        assertEquals(15000L, total) // All sources included
    }

    // ─── Transaction Count ─────────────────────────────────────────────────

    @Test
    fun getTransactionCount_onlySuccessDebit() = runBlocking {
        insertTx(status = TransactionStatus.SUCCESS, direction = TransactionDirection.DEBIT)
        insertTx(status = TransactionStatus.SUCCESS, direction = TransactionDirection.DEBIT)
        insertTx(status = TransactionStatus.SUCCESS, direction = TransactionDirection.CREDIT) // excluded
        insertTx(status = TransactionStatus.FAILED, direction = TransactionDirection.DEBIT)   // excluded

        val count = analyticsDao.getTransactionCount(testUserId, rangeStartMs, rangeEndMs).first()
        assertEquals(2, count)
    }

    // ─── Category Breakdown ────────────────────────────────────────────────

    @Test
    fun getCategoryBreakdown_caseInsensitiveGrouping() = runBlocking {
        insertTx(category = "Food", amount = 1000L)
        insertTx(category = "food", amount = 2000L)
        insertTx(category = "FOOD", amount = 3000L)
        insertTx(category = " Food ", amount = 4000L)
        insertTx(category = "Transport", amount = 5000L)

        val breakdown = analyticsDao.getCategoryBreakdown(testUserId, rangeStartMs, rangeEndMs).first()

        assertEquals(2, breakdown.size)

        val foodRow = breakdown.find { it.normalizedCategory == "food" }!!
        assertEquals(10000L, foodRow.totalPaise)
        assertEquals(4, foodRow.transactionCount)

        val transportRow = breakdown.find { it.normalizedCategory == "transport" }!!
        assertEquals(5000L, transportRow.totalPaise)
        assertEquals(1, transportRow.transactionCount)
    }

    @Test
    fun getCategoryBreakdown_orderedByTotalDescending() = runBlocking {
        insertTx(category = "Small", amount = 100L)
        insertTx(category = "Large", amount = 99900L)
        insertTx(category = "Medium", amount = 5000L)

        val breakdown = analyticsDao.getCategoryBreakdown(testUserId, rangeStartMs, rangeEndMs).first()

        assertEquals(3, breakdown.size)
        assertEquals("large", breakdown[0].normalizedCategory)
        assertEquals("medium", breakdown[1].normalizedCategory)
        assertEquals("small", breakdown[2].normalizedCategory)
    }

    // ─── Payment Method Breakdown ──────────────────────────────────────────

    @Test
    fun getPaymentMethodBreakdown_groupsByMethod() = runBlocking {
        insertTx(paymentMethod = PaymentMethod.UPI, amount = 3000L)
        insertTx(paymentMethod = PaymentMethod.UPI, amount = 2000L)
        insertTx(paymentMethod = PaymentMethod.CASH, amount = 1000L)

        val breakdown = analyticsDao.getPaymentMethodBreakdown(testUserId, rangeStartMs, rangeEndMs).first()

        assertEquals(2, breakdown.size)
        val upiRow = breakdown.find { it.paymentMethod == "UPI" }!!
        assertEquals(5000L, upiRow.totalPaise)
        assertEquals(2, upiRow.transactionCount)
    }

    // ─── Recipient Breakdown ───────────────────────────────────────────────

    @Test
    fun getRecipientBreakdown_respectsLimit() = runBlocking {
        // Insert 15 distinct recipients
        for (i in 1..15) {
            insertTx(recipientName = "Recipient_$i", amount = (i * 1000L))
        }

        val breakdown = analyticsDao.getRecipientBreakdown(testUserId, rangeStartMs, rangeEndMs, 10).first()
        assertEquals(10, breakdown.size)

        // Should be ordered by totalPaise descending
        assertEquals("Recipient_15", breakdown[0].recipientName)
    }

    @Test
    fun getRecipientBreakdown_defaultLimit10() = runBlocking {
        for (i in 1..12) {
            insertTx(recipientName = "Merchant_$i", amount = 1000L)
        }

        val breakdown = analyticsDao.getRecipientBreakdown(testUserId, rangeStartMs, rangeEndMs).first()
        assertEquals(10, breakdown.size)
    }

    // ─── Spending Timestamps ───────────────────────────────────────────────

    @Test
    fun getSpendingTimestamps_returnsOnlySuccessDebit() = runBlocking {
        val timestamp = calendarMs(2024, Calendar.NOVEMBER, 3, 14, 30, 0)
        insertTx(
            status = TransactionStatus.SUCCESS,
            direction = TransactionDirection.DEBIT,
            amount = 5000L,
            date = timestamp
        )
        insertTx(
            status = TransactionStatus.FAILED,
            direction = TransactionDirection.DEBIT,
            amount = 3000L,
            date = timestamp
        )

        val timestamps = analyticsDao.getSpendingTimestamps(testUserId, rangeStartMs, rangeEndMs)
        assertEquals(1, timestamps.size)
        assertEquals(5000L, timestamps[0].amountPaise)
    }

    @Test
    fun getSpendingTimestamps_orderedByDateAscending() = runBlocking {
        val day1 = calendarMs(2024, Calendar.NOVEMBER, 1, 10, 0, 0)
        val day3 = calendarMs(2024, Calendar.NOVEMBER, 3, 10, 0, 0)
        val day2 = calendarMs(2024, Calendar.NOVEMBER, 2, 10, 0, 0)

        insertTx(amount = 1000L, date = day3) // Insert out of order
        insertTx(amount = 2000L, date = day1)
        insertTx(amount = 3000L, date = day2)

        val timestamps = analyticsDao.getSpendingTimestamps(testUserId, rangeStartMs, rangeEndMs)

        assertEquals(3, timestamps.size)
        assertTrue(timestamps[0].timestampMs <= timestamps[1].timestampMs)
        assertTrue(timestamps[1].timestampMs <= timestamps[2].timestampMs)
    }

    // ─── Date Range Boundaries ─────────────────────────────────────────────

    @Test
    fun getTotalSpending_excludesOutOfRangeTransactions() = runBlocking {
        val beforeRange = calendarMs(2024, Calendar.OCTOBER, 31, 23, 59, 59)
        val afterRange = calendarMs(2024, Calendar.NOVEMBER, 8, 0, 0, 0)
        val inRange = calendarMs(2024, Calendar.NOVEMBER, 4, 12, 0, 0)

        insertTx(amount = 1000L, date = beforeRange)
        insertTx(amount = 2000L, date = inRange)
        insertTx(amount = 3000L, date = afterRange)

        val total = analyticsDao.getTotalSpending(testUserId, rangeStartMs, rangeEndMs).first()
        assertEquals(2000L, total) // Only in-range
    }

    @Test
    fun getTotalSpending_includesBoundaryTransactions() = runBlocking {
        insertTx(amount = 1000L, date = rangeStartMs) // Exact start boundary
        insertTx(amount = 2000L, date = rangeEndMs)   // Exact end boundary

        val total = analyticsDao.getTotalSpending(testUserId, rangeStartMs, rangeEndMs).first()
        assertEquals(3000L, total)
    }

    // ─── Suspend Variants ──────────────────────────────────────────────────

    @Test
    fun getTotalSpendingSuspend_matchesFlowVariant() = runBlocking {
        insertTx(amount = 5000L)
        insertTx(amount = 3000L)

        val flowResult = analyticsDao.getTotalSpending(testUserId, rangeStartMs, rangeEndMs).first()
        val suspendResult = analyticsDao.getTotalSpendingSuspend(testUserId, rangeStartMs, rangeEndMs)

        assertEquals(flowResult, suspendResult)
        assertEquals(8000L, suspendResult)
    }

    @Test
    fun getTransactionCountSuspend_matchesFlowVariant() = runBlocking {
        insertTx()
        insertTx()

        val flowResult = analyticsDao.getTransactionCount(testUserId, rangeStartMs, rangeEndMs).first()
        val suspendResult = analyticsDao.getTransactionCountSuspend(testUserId, rangeStartMs, rangeEndMs)

        assertEquals(flowResult, suspendResult)
        assertEquals(2, suspendResult)
    }

    // ─── Reactive Eligible Transactions Flow ────────────────────────────────

    @Test
    fun getEligibleTransactionsFlow_onlySuccessDebit() = runBlocking {
        insertTx(status = TransactionStatus.SUCCESS, direction = TransactionDirection.DEBIT, amount = 10000L)
        insertTx(status = TransactionStatus.SUCCESS, direction = TransactionDirection.CREDIT, amount = 50000L) // excluded
        insertTx(status = TransactionStatus.PENDING, direction = TransactionDirection.DEBIT, amount = 20000L)  // excluded
        insertTx(status = TransactionStatus.FAILED, direction = TransactionDirection.DEBIT, amount = 30000L)   // excluded

        val rows = analyticsDao.getEligibleTransactionsFlow(testUserId, rangeStartMs, rangeEndMs).first()
        assertEquals(1, rows.size)
        assertEquals(10000L, rows[0].amountPaise)
    }

    @Test
    fun getEligibleTransactionsFlow_automaticallyEmitsOnNewInsert() = runBlocking {
        insertTx(amount = 5000L)

        val flow = analyticsDao.getEligibleTransactionsFlow(testUserId, rangeStartMs, rangeEndMs)
        val initialRows = flow.first()
        assertEquals(1, initialRows.size)

        // Insert new eligible transaction
        insertTx(amount = 15000L)

        val updatedRows = flow.first()
        assertEquals(2, updatedRows.size)
        assertEquals(20000L, updatedRows.sumOf { it.amountPaise })
    }

    @Test
    fun getEligibleTransactionsFlow_ignoresCreditAndPending() = runBlocking {
        insertTx(amount = 5000L)
        val flow = analyticsDao.getEligibleTransactionsFlow(testUserId, rangeStartMs, rangeEndMs)
        assertEquals(1, flow.first().size)

        // Insert CREDIT and PENDING
        insertTx(status = TransactionStatus.SUCCESS, direction = TransactionDirection.CREDIT, amount = 99999L)
        insertTx(status = TransactionStatus.PENDING, direction = TransactionDirection.DEBIT, amount = 88888L)

        val rowsAfter = flow.first()
        assertEquals(1, rowsAfter.size)
        assertEquals(5000L, rowsAfter[0].amountPaise)
    }

    @Test
    fun getEligibleTransactionsFlow_isolatesUser() = runBlocking {
        insertTx(userId = testUserId, amount = 5000L)
        insertTx(userId = "other_user", amount = 10000L)

        val userRows = analyticsDao.getEligibleTransactionsFlow(testUserId, rangeStartMs, rangeEndMs).first()
        val otherRows = analyticsDao.getEligibleTransactionsFlow("other_user", rangeStartMs, rangeEndMs).first()

        assertEquals(1, userRows.size)
        assertEquals(5000L, userRows[0].amountPaise)
        assertEquals(1, otherRows.size)
        assertEquals(10000L, otherRows[0].amountPaise)
    }

    // ─── Helper ─────────────────────────────────────────────────────────────

    private suspend fun insertTx(
        userId: String = testUserId,
        status: TransactionStatus = TransactionStatus.SUCCESS,
        direction: TransactionDirection = TransactionDirection.DEBIT,
        amount: Long = 1000L,
        category: String = "General",
        paymentMethod: PaymentMethod = PaymentMethod.UPI,
        recipientName: String = "Test Recipient",
        source: TransactionSource = TransactionSource.MANUAL,
        date: Long = calendarMs(2024, Calendar.NOVEMBER, 3, 10, 0, 0)
    ) {
        transactionDao.insertTransaction(
            TransactionEntity(
                transactionId = UUID.randomUUID().toString(),
                amountPaise = amount,
                recipientName = recipientName,
                paymentMethod = paymentMethod,
                category = category,
                transactionDate = date,
                transactionStatus = status,
                transactionSource = source,
                userId = userId,
                direction = direction
            )
        )
    }

    private fun calendarMs(
        year: Int, month: Int, day: Int,
        hour: Int, minute: Int, second: Int
    ): Long {
        return Calendar.getInstance(tz).apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month)
            set(Calendar.DAY_OF_MONTH, day)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, second)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
}

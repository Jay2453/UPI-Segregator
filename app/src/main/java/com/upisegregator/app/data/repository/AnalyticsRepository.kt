package com.upisegregator.app.data.repository

import com.upisegregator.app.data.local.dao.AnalyticsDao
import com.upisegregator.app.domain.analytics.AnalyticsPeriod
import com.upisegregator.app.domain.analytics.CategoryNormalizer
import com.upisegregator.app.domain.analytics.CategorySpending
import com.upisegregator.app.domain.analytics.DailySpending
import com.upisegregator.app.domain.analytics.DateRangeHelper
import com.upisegregator.app.domain.analytics.HighestSpendingDay
import com.upisegregator.app.domain.analytics.MonthlySpending
import com.upisegregator.app.domain.analytics.PaymentMethodSpending
import com.upisegregator.app.domain.analytics.PreviousPeriodComparison
import com.upisegregator.app.domain.analytics.RecipientSpending
import com.upisegregator.app.domain.analytics.ResolvedDateRange
import com.upisegregator.app.domain.analytics.SpendingSummary
import com.upisegregator.app.domain.analytics.WeeklySpending
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * Projection representing an eligible transaction record for analytics processing.
 */
data class EligibleTransactionAnalyticsRow(
    val transactionDate: Long,
    val amountPaise: Long,
    val category: String,
    val paymentMethod: String,
    val recipientName: String
)

/**
 * Unified, atomically consistent analytics data snapshot for a specific date range.
 * Emitted reactively whenever any matching transactions change in the database.
 */
data class PeriodAnalyticsData(
    val range: ResolvedDateRange,
    val summary: SpendingSummary,
    val allTransactions: List<EligibleTransactionAnalyticsRow>,
    val categories: List<CategorySpending>,
    val paymentMethods: List<PaymentMethodSpending>,
    val recipients: List<RecipientSpending>,
    val dailyTrend: List<DailySpending>,
    val weeklyTrend: List<WeeklySpending>,
    val monthlyTrend: List<MonthlySpending>,
    val highestDay: HighestSpendingDay?
)

/**
 * Repository interface for analytics data access.
 */
interface AnalyticsRepository {

    /**
     * Observes complete analytics data for [userId] in [range] reactively.
     * All summary, breakdowns, trends, and comparison values update together
     * whenever transactions in the database are inserted, updated, or deleted.
     */
    fun getPeriodAnalytics(
        userId: String,
        range: ResolvedDateRange,
        timeZone: TimeZone = TimeZone.getDefault()
    ): Flow<PeriodAnalyticsData>

    /**
     * Observes the spending summary for the given period.
     */
    fun getSpendingSummary(
        userId: String,
        range: ResolvedDateRange,
        timeZone: TimeZone = TimeZone.getDefault()
    ): Flow<SpendingSummary>

    /**
     * Observes category-wise spending breakdown.
     */
    fun getCategoryBreakdown(
        userId: String,
        range: ResolvedDateRange
    ): Flow<List<CategorySpending>>

    /**
     * Observes payment-method-wise spending breakdown.
     */
    fun getPaymentMethodBreakdown(
        userId: String,
        range: ResolvedDateRange
    ): Flow<List<PaymentMethodSpending>>

    /**
     * Observes recipient-wise spending breakdown.
     */
    fun getRecipientBreakdown(
        userId: String,
        range: ResolvedDateRange,
        limit: Int = 10
    ): Flow<List<RecipientSpending>>

    /**
     * Returns daily spending trend for the date range.
     * Includes days with zero spending.
     */
    suspend fun getDailySpendingTrend(
        userId: String,
        range: ResolvedDateRange,
        timeZone: TimeZone = TimeZone.getDefault()
    ): List<DailySpending>

    /**
     * Returns weekly spending trend for the date range.
     */
    suspend fun getWeeklySpendingTrend(
        userId: String,
        range: ResolvedDateRange,
        timeZone: TimeZone = TimeZone.getDefault()
    ): List<WeeklySpending>

    /**
     * Returns monthly spending trend for the date range.
     */
    suspend fun getMonthlySpendingTrend(
        userId: String,
        range: ResolvedDateRange,
        timeZone: TimeZone = TimeZone.getDefault()
    ): List<MonthlySpending>

    /**
     * Returns the highest-spending day in the date range, or null if no spending.
     */
    suspend fun getHighestSpendingDay(
        userId: String,
        range: ResolvedDateRange,
        timeZone: TimeZone = TimeZone.getDefault()
    ): HighestSpendingDay?
}

/**
 * Implementation of [AnalyticsRepository] backed by [AnalyticsDao].
 *
 * Business logic (bucketing, percentage calculations, previous-period comparison)
 * lives here in the data/repository layer rather than in Composables.
 */
class AnalyticsRepositoryImpl(
    private val analyticsDao: AnalyticsDao
) : AnalyticsRepository {

    override fun getPeriodAnalytics(
        userId: String,
        range: ResolvedDateRange,
        timeZone: TimeZone
    ): Flow<PeriodAnalyticsData> {
        val previousRange = DateRangeHelper.previousPeriod(range, timeZone)

        return combine(
            analyticsDao.getEligibleTransactionsFlow(userId, range.startMs, range.endMs),
            analyticsDao.getTotalSpending(userId, previousRange.startMs, previousRange.endMs)
        ) { rows, previousSpending ->
            val mappedRows = rows.map { row ->
                EligibleTransactionAnalyticsRow(
                    transactionDate = row.transactionDate,
                    amountPaise = row.amountPaise,
                    category = row.category,
                    paymentMethod = row.paymentMethod.name,
                    recipientName = row.recipientName
                )
            }

            val totalSpending = mappedRows.sumOf { it.amountPaise }
            val count = mappedRows.size
            val avgDaily = if (range.calendarDays > 0L) totalSpending / range.calendarDays else 0L

            val comparison = if (previousSpending == 0L) {
                PreviousPeriodComparison.InsufficientHistory
            } else {
                val changePercent = ((totalSpending - previousSpending) * 100L) / previousSpending
                PreviousPeriodComparison.Available(
                    previousSpendingPaise = previousSpending,
                    changePercent = changePercent
                )
            }

            val summary = SpendingSummary(
                totalSpendingPaise = totalSpending,
                transactionCount = count,
                averageDailySpendingPaise = avgDaily,
                calendarDays = range.calendarDays,
                previousPeriodComparison = comparison
            )

            // Category breakdown grouped case-insensitively
            val categoryGroups = mappedRows.groupBy { CategoryNormalizer.normalizeKey(it.category) }
            val categories = categoryGroups.map { (normalizedKey, items) ->
                val groupTotal = items.sumOf { it.amountPaise }
                val bestName = CategoryNormalizer.bestDisplayName(items.map { it.category })
                CategorySpending(
                    normalizedCategory = normalizedKey,
                    displayName = bestName,
                    totalPaise = groupTotal,
                    transactionCount = items.size,
                    percentage = if (totalSpending > 0L) (groupTotal * 100L) / totalSpending else 0L
                )
            }.sortedByDescending { it.totalPaise }

            // Payment method breakdown
            val paymentGroups = mappedRows.groupBy { it.paymentMethod }
            val paymentMethods = paymentGroups.map { (method, items) ->
                val groupTotal = items.sumOf { it.amountPaise }
                PaymentMethodSpending(
                    paymentMethod = method,
                    totalPaise = groupTotal,
                    transactionCount = items.size,
                    percentage = if (totalSpending > 0L) (groupTotal * 100L) / totalSpending else 0L
                )
            }.sortedByDescending { it.totalPaise }

            // Top recipient breakdown (limit 10)
            val recipientGroups = mappedRows.groupBy { it.recipientName }
            val recipients = recipientGroups.map { (name, items) ->
                val groupTotal = items.sumOf { it.amountPaise }
                RecipientSpending(
                    recipientName = name,
                    totalPaise = groupTotal,
                    transactionCount = items.size,
                    percentage = if (totalSpending > 0L) (groupTotal * 100L) / totalSpending else 0L
                )
            }.sortedByDescending { it.totalPaise }.take(10)

            // Daily, weekly, monthly trends bucketed by device-local timezone
            val dailyTrend = computeDailyTrend(mappedRows, range, timeZone)
            val weeklyTrend = computeWeeklyTrend(dailyTrend, timeZone)
            val monthlyTrend = computeMonthlyTrend(dailyTrend, timeZone)
            val highestDay = computeHighestDay(dailyTrend)

            PeriodAnalyticsData(
                range = range,
                summary = summary,
                allTransactions = mappedRows,
                categories = categories,
                paymentMethods = paymentMethods,
                recipients = recipients,
                dailyTrend = dailyTrend,
                weeklyTrend = weeklyTrend,
                monthlyTrend = monthlyTrend,
                highestDay = highestDay
            )
        }
    }

    override fun getSpendingSummary(
        userId: String,
        range: ResolvedDateRange,
        timeZone: TimeZone
    ): Flow<SpendingSummary> {
        return analyticsDao.getTotalSpending(userId, range.startMs, range.endMs)
            .map { totalPaise ->
                val txCount = analyticsDao.getTransactionCountSuspend(
                    userId, range.startMs, range.endMs
                )
                val avgDaily = if (range.calendarDays > 0L) {
                    totalPaise / range.calendarDays
                } else {
                    0L
                }

                val previousRange = DateRangeHelper.previousPeriod(range, timeZone)
                val previousSpending = analyticsDao.getTotalSpendingSuspend(
                    userId, previousRange.startMs, previousRange.endMs
                )

                val comparison = if (previousSpending == 0L) {
                    PreviousPeriodComparison.InsufficientHistory
                } else {
                    val changePercent = ((totalPaise - previousSpending) * 100L) / previousSpending
                    PreviousPeriodComparison.Available(
                        previousSpendingPaise = previousSpending,
                        changePercent = changePercent
                    )
                }

                SpendingSummary(
                    totalSpendingPaise = totalPaise,
                    transactionCount = txCount,
                    averageDailySpendingPaise = avgDaily,
                    calendarDays = range.calendarDays,
                    previousPeriodComparison = comparison
                )
            }
    }

    override fun getCategoryBreakdown(
        userId: String,
        range: ResolvedDateRange
    ): Flow<List<CategorySpending>> {
        return analyticsDao.getCategoryBreakdown(userId, range.startMs, range.endMs)
            .map { rows ->
                val totalSpending = rows.sumOf { it.totalPaise }
                rows.map { row ->
                    CategorySpending(
                        normalizedCategory = row.normalizedCategory.ifBlank {
                            CategoryNormalizer.normalizeKey(null)
                        },
                        displayName = CategoryNormalizer.displayName(
                            row.displayName.ifBlank { null }
                        ),
                        totalPaise = row.totalPaise,
                        transactionCount = row.transactionCount,
                        percentage = if (totalSpending > 0L) {
                            (row.totalPaise * 100L) / totalSpending
                        } else {
                            0L
                        }
                    )
                }
            }
    }

    override fun getPaymentMethodBreakdown(
        userId: String,
        range: ResolvedDateRange
    ): Flow<List<PaymentMethodSpending>> {
        return analyticsDao.getPaymentMethodBreakdown(userId, range.startMs, range.endMs)
            .map { rows ->
                val totalSpending = rows.sumOf { it.totalPaise }
                rows.map { row ->
                    PaymentMethodSpending(
                        paymentMethod = row.paymentMethod,
                        totalPaise = row.totalPaise,
                        transactionCount = row.transactionCount,
                        percentage = if (totalSpending > 0L) {
                            (row.totalPaise * 100L) / totalSpending
                        } else {
                            0L
                        }
                    )
                }
            }
    }

    override fun getRecipientBreakdown(
        userId: String,
        range: ResolvedDateRange,
        limit: Int
    ): Flow<List<RecipientSpending>> {
        return analyticsDao.getRecipientBreakdown(userId, range.startMs, range.endMs, limit)
            .map { rows ->
                val totalSpending = rows.sumOf { it.totalPaise }
                rows.map { row ->
                    RecipientSpending(
                        recipientName = row.recipientName,
                        totalPaise = row.totalPaise,
                        transactionCount = row.transactionCount,
                        percentage = if (totalSpending > 0L) {
                            (row.totalPaise * 100L) / totalSpending
                        } else {
                            0L
                        }
                    )
                }
            }
    }

    override suspend fun getDailySpendingTrend(
        userId: String,
        range: ResolvedDateRange,
        timeZone: TimeZone
    ): List<DailySpending> {
        val rawRows = analyticsDao.getSpendingTimestamps(userId, range.startMs, range.endMs)
        val mapped = rawRows.map {
            EligibleTransactionAnalyticsRow(
                transactionDate = it.timestampMs,
                amountPaise = it.amountPaise,
                category = "",
                paymentMethod = "",
                recipientName = ""
            )
        }
        return computeDailyTrend(mapped, range, timeZone)
    }

    override suspend fun getWeeklySpendingTrend(
        userId: String,
        range: ResolvedDateRange,
        timeZone: TimeZone
    ): List<WeeklySpending> {
        val dailyTrend = getDailySpendingTrend(userId, range, timeZone)
        return computeWeeklyTrend(dailyTrend, timeZone)
    }

    override suspend fun getMonthlySpendingTrend(
        userId: String,
        range: ResolvedDateRange,
        timeZone: TimeZone
    ): List<MonthlySpending> {
        val dailyTrend = getDailySpendingTrend(userId, range, timeZone)
        return computeMonthlyTrend(dailyTrend, timeZone)
    }

    override suspend fun getHighestSpendingDay(
        userId: String,
        range: ResolvedDateRange,
        timeZone: TimeZone
    ): HighestSpendingDay? {
        val dailyTrend = getDailySpendingTrend(userId, range, timeZone)
        return computeHighestDay(dailyTrend)
    }
}

// ─── Trend Computation Helpers ──────────────────────────────────────────────

fun computeDailyTrend(
    rows: List<EligibleTransactionAnalyticsRow>,
    range: ResolvedDateRange,
    timeZone: TimeZone
): List<DailySpending> {
    val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
        this.timeZone = timeZone
    }

    val bucketMap = mutableMapOf<String, Long>()
    for (row in rows) {
        val dateKey = dateFormat.format(row.transactionDate)
        bucketMap[dateKey] = (bucketMap[dateKey] ?: 0L) + row.amountPaise
    }

    val result = mutableListOf<DailySpending>()
    val cal = Calendar.getInstance(timeZone).apply {
        timeInMillis = range.startMs
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val endCal = Calendar.getInstance(timeZone).apply {
        timeInMillis = range.endMs
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }

    while (!cal.after(endCal)) {
        val dateKey = dateFormat.format(cal.time)
        result.add(DailySpending(dateLabel = dateKey, totalPaise = bucketMap[dateKey] ?: 0L))
        cal.add(Calendar.DAY_OF_YEAR, 1)
    }
    return result
}

fun computeWeeklyTrend(
    dailyTrend: List<DailySpending>,
    timeZone: TimeZone
): List<WeeklySpending> {
    if (dailyTrend.isEmpty()) return emptyList()

    val dayFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
        this.timeZone = timeZone
    }
    val labelFormat = SimpleDateFormat("MMM dd", Locale.US).apply {
        this.timeZone = timeZone
    }

    val weeks = mutableListOf<WeeklySpending>()
    var weekStart: String? = null
    var weekEnd: String? = null
    var weekTotal = 0L

    for ((index, day) in dailyTrend.withIndex()) {
        if (weekStart == null) weekStart = day.dateLabel
        weekEnd = day.dateLabel
        weekTotal += day.totalPaise

        val date = dayFormat.parse(day.dateLabel)!!
        val cal = Calendar.getInstance(timeZone).apply { time = date }
        val isLastDayOfWeek = cal.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY
        val isLastDay = index == dailyTrend.size - 1

        if (isLastDayOfWeek || isLastDay) {
            val startDate = dayFormat.parse(weekStart!!)!!
            val endDate = dayFormat.parse(weekEnd!!)!!
            val label = "${labelFormat.format(startDate)} – ${labelFormat.format(endDate)}"
            weeks.add(WeeklySpending(weekLabel = label, totalPaise = weekTotal))
            weekStart = null
            weekEnd = null
            weekTotal = 0L
        }
    }
    return weeks
}

fun computeMonthlyTrend(
    dailyTrend: List<DailySpending>,
    timeZone: TimeZone
): List<MonthlySpending> {
    if (dailyTrend.isEmpty()) return emptyList()

    val dayFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
        this.timeZone = timeZone
    }
    val monthFormat = SimpleDateFormat("MMM yyyy", Locale.US).apply {
        this.timeZone = timeZone
    }

    val monthMap = linkedMapOf<String, Long>()
    for (day in dailyTrend) {
        val date = dayFormat.parse(day.dateLabel)!!
        val monthKey = monthFormat.format(date)
        monthMap[monthKey] = (monthMap[monthKey] ?: 0L) + day.totalPaise
    }

    return monthMap.map { (label, total) ->
        MonthlySpending(monthLabel = label, totalPaise = total)
    }
}

fun computeHighestDay(dailyTrend: List<DailySpending>): HighestSpendingDay? {
    val maxDay = dailyTrend.maxByOrNull { it.totalPaise } ?: return null
    if (maxDay.totalPaise == 0L) return null
    return HighestSpendingDay(dateLabel = maxDay.dateLabel, totalPaise = maxDay.totalPaise)
}

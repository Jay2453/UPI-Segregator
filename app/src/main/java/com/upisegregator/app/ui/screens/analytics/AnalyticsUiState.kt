package com.upisegregator.app.ui.screens.analytics

import com.upisegregator.app.domain.analytics.AnalyticsPeriod
import com.upisegregator.app.domain.analytics.CategorySpending
import com.upisegregator.app.domain.analytics.DailySpending
import com.upisegregator.app.domain.analytics.HighestSpendingDay
import com.upisegregator.app.domain.analytics.MonthlySpending
import com.upisegregator.app.domain.analytics.PaymentMethodSpending
import com.upisegregator.app.domain.analytics.PreviousPeriodComparison
import com.upisegregator.app.domain.analytics.RecipientSpending
import com.upisegregator.app.domain.analytics.SpendingSummary
import com.upisegregator.app.domain.analytics.WeeklySpending

/**
 * Immutable UI state for the Analytics Dashboard screen (Milestone 7).
 *
 * All monetary values are Long integer paise (1 INR = 100 paise).
 * Redundant derived fields are kept minimal and exposed via read-only properties.
 */
data class AnalyticsUiState(
    /** Indicates whether analytics data or filter changes are currently loading. */
    val isLoading: Boolean = true,

    /** Human-readable error message if an operation or fetch failed, null otherwise. */
    val errorMessage: String? = null,

    /** Currently selected analytics time period. Defaults to THIS_MONTH. */
    val selectedPeriod: AnalyticsPeriod = AnalyticsPeriod.THIS_MONTH,

    /** Start timestamp for custom date range (inclusive), null if predefined period. */
    val selectedCustomStartDate: Long? = null,

    /** End timestamp for custom date range (inclusive), null if predefined period. */
    val selectedCustomEndDate: Long? = null,

    /** Active category filter, null if no category filter applied. */
    val selectedCategory: String? = null,

    /** Active payment method filter, null if no payment method filter applied. */
    val selectedPaymentMethod: String? = null,

    /** High-level spending summary for the active period and filters, null if not loaded. */
    val spendingSummary: SpendingSummary? = null,

    /** Comparison to the previous equivalent period, null if not loaded. */
    val previousPeriodComparison: PreviousPeriodComparison? = null,

    /** Category-wise spending breakdown, filtered if [selectedCategory] is set. */
    val categorySpending: List<CategorySpending> = emptyList(),

    /** Payment-method-wise spending breakdown, filtered if [selectedPaymentMethod] is set. */
    val paymentMethodSpending: List<PaymentMethodSpending> = emptyList(),

    /** Top recipient-wise spending breakdown. */
    val recipientSpending: List<RecipientSpending> = emptyList(),

    /** Daily spending trend for the selected period (including zero-spending days). */
    val dailySpending: List<DailySpending> = emptyList(),

    /** Weekly spending trend for the selected period. */
    val weeklySpending: List<WeeklySpending> = emptyList(),

    /** Monthly spending trend for the selected period. */
    val monthlySpending: List<MonthlySpending> = emptyList(),

    /** The highest spending day in the selected period, null if zero spending. */
    val highestSpendingDay: HighestSpendingDay? = null,

    /** All categories present in the selected period for filter selection menus. */
    val availableCategories: List<String> = emptyList(),

    /** All payment methods present in the selected period for filter selection menus. */
    val availablePaymentMethods: List<String> = emptyList()
) {
    /** True if the user has any debit spending in the selected period. */
    val hasSpending: Boolean get() = (spendingSummary?.totalSpendingPaise ?: 0L) > 0L

    /** True if the selected period is a custom date range. */
    val isCustomPeriod: Boolean get() = selectedPeriod == AnalyticsPeriod.CUSTOM

    /** True if any category or payment-method filter is currently active. */
    val isFiltered: Boolean get() = selectedCategory != null || selectedPaymentMethod != null
}

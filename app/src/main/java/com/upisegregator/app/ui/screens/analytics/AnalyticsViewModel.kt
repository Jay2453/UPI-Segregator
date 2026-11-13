package com.upisegregator.app.ui.screens.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.upisegregator.app.UpiSegregatorApp
import com.upisegregator.app.data.demo.DemoDataManager
import com.upisegregator.app.data.repository.AnalyticsRepository
import com.upisegregator.app.data.repository.EligibleTransactionAnalyticsRow
import com.upisegregator.app.data.repository.PeriodAnalyticsData
import com.upisegregator.app.data.repository.computeDailyTrend
import com.upisegregator.app.data.repository.computeHighestDay
import com.upisegregator.app.data.repository.computeMonthlyTrend
import com.upisegregator.app.data.repository.computeWeeklyTrend
import com.upisegregator.app.data.session.SessionManager
import com.upisegregator.app.data.session.UserSession
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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.util.TimeZone

/**
 * Internal result type capturing the state of an analytics fetch operation.
 */
private sealed interface FetchResult {
    data object Loading : FetchResult
    data object SessionUnavailable : FetchResult
    data class ValidationError(val message: String) : FetchResult
    data class Error(val message: String) : FetchResult
    data class Success(val periodData: PeriodAnalyticsData) : FetchResult
}

private data class FilteredSnapshot(
    val summary: SpendingSummary,
    val categories: List<CategorySpending>,
    val paymentMethods: List<PaymentMethodSpending>,
    val recipients: List<RecipientSpending>,
    val dailyTrend: List<DailySpending>,
    val weeklyTrend: List<WeeklySpending>,
    val monthlyTrend: List<MonthlySpending>,
    val highestDay: HighestSpendingDay?
)

/**
 * Composite key driving analytics re-queries when session or date range changes.
 */
private data class QueryKey(
    val session: UserSession,
    val period: AnalyticsPeriod,
    val customRange: Pair<Long, Long>?,
    val refreshCount: Int,
    val validationError: String?
)

/**
 * ViewModel managing the Analytics Dashboard screen state (Milestone 7 - Stage 2).
 *
 * Invariants:
 * - Scoped strictly to the active user ID resolved from [SessionManager].
 * - Automatically isolates authenticated user data and demo mode transactions.
 * - Reactive: Uses [flatMapLatest] to observe repository updates and cancel obsolete requests
 *   on rapid period or filter changes.
 * - Exposes a single, immutable [AnalyticsUiState] via [uiState].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AnalyticsViewModel(
    private val sessionManager: SessionManager,
    private val analyticsRepository: AnalyticsRepository,
    private val timeZone: TimeZone = TimeZone.getDefault(),
    sharingStarted: SharingStarted = SharingStarted.WhileSubscribed(5000)
) : ViewModel() {

    // ── Input StateFlows ───────────────────────────────────────────────────

    private val _selectedPeriod = MutableStateFlow(AnalyticsPeriod.THIS_MONTH)
    private val _customDateRange = MutableStateFlow<Pair<Long, Long>?>(null)
    private val _selectedCategory = MutableStateFlow<String?>(null)
    private val _selectedPaymentMethod = MutableStateFlow<String?>(null)
    private val _refreshTrigger = MutableStateFlow(0)
    private val _validationError = MutableStateFlow<String?>(null)

    // ── Reactive Query Pipeline ────────────────────────────────────────────

    private val queryKeyFlow: Flow<QueryKey> = combine(
        sessionManager.sessionFlow,
        _selectedPeriod,
        _customDateRange,
        _refreshTrigger,
        _validationError
    ) { session, period, customRange, refreshCount, validationError ->
        QueryKey(session, period, customRange, refreshCount, validationError)
    }

    private val fetchResultFlow: Flow<FetchResult> = queryKeyFlow
        .flatMapLatest { key ->
            if (key.validationError != null) {
                return@flatMapLatest flowOf(FetchResult.ValidationError(key.validationError))
            }

            val userId = resolveActiveUserId(key.session)
            if (userId == null) {
                return@flatMapLatest flowOf(FetchResult.SessionUnavailable)
            }

            flow {
                emit(FetchResult.Loading)

                val resolvedRange = try {
                    DateRangeHelper.resolvePeriod(
                        period = key.period,
                        customStartMs = key.customRange?.first,
                        customEndMs = key.customRange?.second,
                        timeZone = timeZone
                    )
                } catch (e: Exception) {
                    emit(FetchResult.Error(e.message ?: "Failed to resolve date range"))
                    return@flow
                }

                // Reactively observe unified period analytics (re-emits on any DB transaction changes)
                val repositoryFlow = analyticsRepository.getPeriodAnalytics(userId, resolvedRange, timeZone)
                    .map { periodData ->
                        FetchResult.Success(periodData)
                    }
                    .catch { e ->
                        emit(FetchResult.Error(e.message ?: "Failed to load analytics"))
                    }

                emitAll(repositoryFlow)
            }.catch { e ->
                emit(FetchResult.Error(e.message ?: "Failed to load analytics"))
            }
        }

    // ── Public Observable State ────────────────────────────────────────────

    val uiState: StateFlow<AnalyticsUiState> = combine(
        fetchResultFlow,
        _selectedPeriod,
        _customDateRange,
        _selectedCategory,
        _selectedPaymentMethod
    ) { fetchResult, selectedPeriod, customRange, selectedCategory, selectedPaymentMethod ->
        when (fetchResult) {
            is FetchResult.Loading -> {
                AnalyticsUiState(
                    isLoading = true,
                    errorMessage = null,
                    selectedPeriod = selectedPeriod,
                    selectedCustomStartDate = customRange?.first,
                    selectedCustomEndDate = customRange?.second,
                    selectedCategory = selectedCategory,
                    selectedPaymentMethod = selectedPaymentMethod
                )
            }
            is FetchResult.SessionUnavailable -> {
                AnalyticsUiState(
                    isLoading = false,
                    errorMessage = "Session unavailable",
                    selectedPeriod = selectedPeriod,
                    selectedCustomStartDate = customRange?.first,
                    selectedCustomEndDate = customRange?.second,
                    selectedCategory = selectedCategory,
                    selectedPaymentMethod = selectedPaymentMethod,
                    spendingSummary = null,
                    previousPeriodComparison = null,
                    categorySpending = emptyList(),
                    paymentMethodSpending = emptyList(),
                    recipientSpending = emptyList(),
                    dailySpending = emptyList(),
                    weeklySpending = emptyList(),
                    monthlySpending = emptyList(),
                    highestSpendingDay = null,
                    availableCategories = emptyList(),
                    availablePaymentMethods = emptyList()
                )
            }
            is FetchResult.ValidationError -> {
                AnalyticsUiState(
                    isLoading = false,
                    errorMessage = fetchResult.message,
                    selectedPeriod = selectedPeriod,
                    selectedCustomStartDate = customRange?.first,
                    selectedCustomEndDate = customRange?.second,
                    selectedCategory = selectedCategory,
                    selectedPaymentMethod = selectedPaymentMethod
                )
            }
            is FetchResult.Error -> {
                AnalyticsUiState(
                    isLoading = false,
                    errorMessage = fetchResult.message,
                    selectedPeriod = selectedPeriod,
                    selectedCustomStartDate = customRange?.first,
                    selectedCustomEndDate = customRange?.second,
                    selectedCategory = selectedCategory,
                    selectedPaymentMethod = selectedPaymentMethod
                )
            }
            is FetchResult.Success -> {
                val periodData = fetchResult.periodData

                // Available options always reflect the full, unfiltered dataset for this period
                val availableCategories = periodData.categories.map { it.displayName }
                val availablePaymentMethods = periodData.paymentMethods.map { it.paymentMethod }

                val hasCategoryFilter = selectedCategory != null
                val hasPaymentMethodFilter = selectedPaymentMethod != null
                val isFiltered = hasCategoryFilter || hasPaymentMethodFilter

                val snapshot = if (isFiltered) {
                    // Exact intersection (AND) filtering
                    val matching = periodData.allTransactions.filter { tx ->
                        val categoryMatch = selectedCategory == null ||
                            CategoryNormalizer.normalizeKey(tx.category) == CategoryNormalizer.normalizeKey(selectedCategory)
                        val paymentMethodMatch = selectedPaymentMethod == null ||
                            tx.paymentMethod.equals(selectedPaymentMethod, ignoreCase = true)
                        categoryMatch && paymentMethodMatch
                    }

                    val totalPaise = matching.sumOf { it.amountPaise }
                    val txCount = matching.size
                    val avgDaily = if (periodData.range.calendarDays > 0L) totalPaise / periodData.range.calendarDays else 0L

                    val filteredSummary = SpendingSummary(
                        totalSpendingPaise = totalPaise,
                        transactionCount = txCount,
                        averageDailySpendingPaise = avgDaily,
                        calendarDays = periodData.range.calendarDays,
                        previousPeriodComparison = PreviousPeriodComparison.InsufficientHistory
                    )

                    val catGroups = matching.groupBy { CategoryNormalizer.normalizeKey(it.category) }
                    val catList = catGroups.map { (key, items) ->
                        val groupTotal = items.sumOf { it.amountPaise }
                        val bestName = CategoryNormalizer.bestDisplayName(items.map { it.category })
                        CategorySpending(
                            normalizedCategory = key,
                            displayName = bestName,
                            totalPaise = groupTotal,
                            transactionCount = items.size,
                            percentage = if (totalPaise > 0L) (groupTotal * 100L) / totalPaise else 0L
                        )
                    }.sortedByDescending { it.totalPaise }

                    val pmGroups = matching.groupBy { it.paymentMethod }
                    val pmList = pmGroups.map { (method, items) ->
                        val groupTotal = items.sumOf { it.amountPaise }
                        PaymentMethodSpending(
                            paymentMethod = method,
                            totalPaise = groupTotal,
                            transactionCount = items.size,
                            percentage = if (totalPaise > 0L) (groupTotal * 100L) / totalPaise else 0L
                        )
                    }.sortedByDescending { it.totalPaise }

                    val recGroups = matching.groupBy { it.recipientName }
                    val recList = recGroups.map { (name, items) ->
                        val groupTotal = items.sumOf { it.amountPaise }
                        RecipientSpending(
                            recipientName = name,
                            totalPaise = groupTotal,
                            transactionCount = items.size,
                            percentage = if (totalPaise > 0L) (groupTotal * 100L) / totalPaise else 0L
                        )
                    }.sortedByDescending { it.totalPaise }.take(10)

                    val daily = computeDailyTrend(matching, periodData.range, timeZone)
                    val weekly = computeWeeklyTrend(daily, timeZone)
                    val monthly = computeMonthlyTrend(daily, timeZone)
                    val highest = computeHighestDay(daily)

                    FilteredSnapshot(
                        summary = filteredSummary,
                        categories = catList,
                        paymentMethods = pmList,
                        recipients = recList,
                        dailyTrend = daily,
                        weeklyTrend = weekly,
                        monthlyTrend = monthly,
                        highestDay = highest
                    )
                } else {
                    FilteredSnapshot(
                        summary = periodData.summary,
                        categories = periodData.categories,
                        paymentMethods = periodData.paymentMethods,
                        recipients = periodData.recipients,
                        dailyTrend = periodData.dailyTrend,
                        weeklyTrend = periodData.weeklyTrend,
                        monthlyTrend = periodData.monthlyTrend,
                        highestDay = periodData.highestDay
                    )
                }

                AnalyticsUiState(
                    isLoading = false,
                    errorMessage = null,
                    selectedPeriod = selectedPeriod,
                    selectedCustomStartDate = customRange?.first,
                    selectedCustomEndDate = customRange?.second,
                    selectedCategory = selectedCategory,
                    selectedPaymentMethod = selectedPaymentMethod,
                    spendingSummary = snapshot.summary,
                    previousPeriodComparison = snapshot.summary.previousPeriodComparison,
                    categorySpending = snapshot.categories,
                    paymentMethodSpending = snapshot.paymentMethods,
                    recipientSpending = snapshot.recipients,
                    dailySpending = snapshot.dailyTrend,
                    weeklySpending = snapshot.weeklyTrend,
                    monthlySpending = snapshot.monthlyTrend,
                    highestSpendingDay = snapshot.highestDay,
                    availableCategories = availableCategories,
                    availablePaymentMethods = availablePaymentMethods
                )
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = sharingStarted,
        initialValue = AnalyticsUiState(isLoading = true)
    )

    // ── User Actions: Period & Date Range Selection ────────────────────────

    /**
     * Changes the analytics period (THIS_WEEK, THIS_MONTH, LAST_MONTH, CUSTOM).
     * If switching away from CUSTOM, existing custom date range is cleared.
     */
    fun selectPeriod(period: AnalyticsPeriod) {
        _validationError.value = null
        if (period != AnalyticsPeriod.CUSTOM) {
            _customDateRange.value = null
        }
        _selectedPeriod.value = period
    }

    /**
     * Sets a custom date range with inclusive boundaries.
     * Validates that [startDateMs] <= [endDateMs].
     */
    fun setCustomDateRange(startDateMs: Long, endDateMs: Long) {
        if (startDateMs > endDateMs) {
            _validationError.value = "Start date cannot be after end date"
            return
        }
        _validationError.value = null
        _customDateRange.value = Pair(startDateMs, endDateMs)
        _selectedPeriod.value = AnalyticsPeriod.CUSTOM
    }

    // ── User Actions: Filter Management ────────────────────────────────────

    /**
     * Selects a category to filter the analytics data.
     */
    fun selectCategory(category: String) {
        if (category.isBlank()) {
            clearCategoryFilter()
            return
        }
        _selectedCategory.value = category.trim()
    }

    /**
     * Clears the current category filter.
     */
    fun clearCategoryFilter() {
        _selectedCategory.value = null
    }

    /**
     * Selects a payment method to filter the analytics data.
     */
    fun selectPaymentMethod(paymentMethod: String) {
        if (paymentMethod.isBlank()) {
            clearPaymentMethodFilter()
            return
        }
        _selectedPaymentMethod.value = paymentMethod.trim()
    }

    /**
     * Clears the current payment method filter.
     */
    fun clearPaymentMethodFilter() {
        _selectedPaymentMethod.value = null
    }

    /**
     * Resets all category and payment method filters.
     */
    fun resetFilters() {
        _selectedCategory.value = null
        _selectedPaymentMethod.value = null
        _validationError.value = null
    }

    /**
     * Manually triggers a reload of the current period analytics.
     */
    fun refresh() {
        _refreshTrigger.update { it + 1 }
    }

    /**
     * Clears any active error message.
     */
    fun clearError() {
        _validationError.value = null
    }

    companion object {
        /**
         * Resolves the active user ID from the user session.
         * Returns null if the session is logged out / guest.
         */
        fun resolveActiveUserId(session: UserSession): String? {
            return when {
                session.isLoggedIn && !session.userId.isNullOrBlank() -> session.userId
                session.isDemoMode -> session.userId ?: DemoDataManager.DEMO_USER_ID
                else -> null
            }
        }

        /**
         * ViewModelProvider Factory wiring dependencies from [AppContainer].
         */
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as UpiSegregatorApp
                AnalyticsViewModel(
                    sessionManager = app.container.sessionManager,
                    analyticsRepository = app.container.analyticsRepository
                )
            }
        }
    }
}

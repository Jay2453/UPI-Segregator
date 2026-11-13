package com.upisegregator.app.ui.screens.analytics

import com.upisegregator.app.data.local.entity.UserEntity
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/**
 * Unit tests for [AnalyticsViewModel] covering:
 * - State management and initial loading
 * - Reactive Room update consistency
 * - Intersection-based combined category + payment-method filtering
 * - Stale result prevention and cancellation
 * - Session and demo isolation
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AnalyticsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private val tz: TimeZone = TimeZone.getTimeZone("Asia/Kolkata")
    private lateinit var fakeSessionManager: FakeSessionManager
    private lateinit var fakeAnalyticsRepository: FakeAnalyticsRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeSessionManager = FakeSessionManager(
            initialSession = UserSession(isLoggedIn = true, userId = "user_real_1")
        )
        fakeAnalyticsRepository = FakeAnalyticsRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): AnalyticsViewModel {
        return AnalyticsViewModel(
            sessionManager = fakeSessionManager,
            analyticsRepository = fakeAnalyticsRepository,
            timeZone = tz,
            sharingStarted = SharingStarted.Eagerly
        )
    }

    // ─── 1. Initial Loading State ──────────────────────────────────────────

    @Test
    fun initialLoadingState_startsInLoadingState() = testScope.runTest {
        fakeAnalyticsRepository.emissionDelayMs = 1000L
        val viewModel = createViewModel()

        val initialState = viewModel.uiState.value
        assertTrue("Expected initial state to have isLoading = true", initialState.isLoading)
        assertNull("Expected no error message on init", initialState.errorMessage)
        assertEquals(AnalyticsPeriod.THIS_MONTH, initialState.selectedPeriod)
        assertNull(initialState.selectedCategory)
        assertNull(initialState.selectedPaymentMethod)
        assertNull(initialState.spendingSummary)
    }

    // ─── 2. Successful Analytics Loading ───────────────────────────────────

    @Test
    fun successfulAnalyticsLoading_emitsSuccessState() = testScope.runTest {
        val viewModel = createViewModel()
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse("Expected isLoading = false after fetch", state.isLoading)
        assertNull("Expected no error", state.errorMessage)
        assertNotNull("Expected spendingSummary to be present", state.spendingSummary)
        assertEquals(50000L, state.spendingSummary?.totalSpendingPaise)
        assertEquals(2, state.spendingSummary?.transactionCount)
        assertTrue(state.hasSpending)

        // Verify breakdowns
        assertEquals(2, state.categorySpending.size)
        assertEquals(2, state.paymentMethodSpending.size)
        assertEquals(2, state.recipientSpending.size)
        assertEquals(2, state.availableCategories.size)
        assertTrue(state.availableCategories.contains("Food"))
        assertTrue(state.availableCategories.contains("Transport"))
        assertTrue(state.availablePaymentMethods.contains("UPI"))
        assertTrue(state.availablePaymentMethods.contains("CASH"))
    }

    // ─── 3. Empty Analytics Results ────────────────────────────────────────

    @Test
    fun emptyAnalyticsResults_handlesZeroSpendingGracefully() = testScope.runTest {
        fakeAnalyticsRepository.transactionsFlow.value = emptyList()

        val viewModel = createViewModel()
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertNull(state.errorMessage)
        assertNotNull(state.spendingSummary)
        assertEquals(0L, state.spendingSummary?.totalSpendingPaise)
        assertEquals(0, state.spendingSummary?.transactionCount)
        assertFalse(state.hasSpending)
        assertTrue(state.categorySpending.isEmpty())
        assertTrue(state.availableCategories.isEmpty())
        assertNull(state.highestSpendingDay)
    }

    // ─── 4. Repository Error Handling ──────────────────────────────────────

    @Test
    fun repositoryErrorHandling_emitsErrorMessage() = testScope.runTest {
        fakeAnalyticsRepository.shouldFail = true
        fakeAnalyticsRepository.failureMessage = "Disk I/O failure"

        val viewModel = createViewModel()
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals("Disk I/O failure", state.errorMessage)
    }

    // ─── 5. THIS_WEEK Selection ────────────────────────────────────────────

    @Test
    fun thisWeekSelection_resolvesWeekAndFetchesData() = testScope.runTest {
        val viewModel = createViewModel()
        testScheduler.advanceUntilIdle()

        viewModel.selectPeriod(AnalyticsPeriod.THIS_WEEK)
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(AnalyticsPeriod.THIS_WEEK, state.selectedPeriod)
        assertNotNull(fakeAnalyticsRepository.lastRequestedRange)
        val days = fakeAnalyticsRepository.lastRequestedRange?.calendarDays ?: 0L
        assertTrue("THIS_WEEK calendar days must be between 1 and 7", days in 1L..7L)
        assertNull(state.selectedCustomStartDate)
        assertNull(state.selectedCustomEndDate)
    }

    // ─── 6. THIS_MONTH Selection ───────────────────────────────────────────

    @Test
    fun thisMonthSelection_resolvesMonthAndFetchesData() = testScope.runTest {
        val viewModel = createViewModel()
        testScheduler.advanceUntilIdle()

        viewModel.selectPeriod(AnalyticsPeriod.THIS_WEEK)
        testScheduler.advanceUntilIdle()

        viewModel.selectPeriod(AnalyticsPeriod.THIS_MONTH)
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(AnalyticsPeriod.THIS_MONTH, state.selectedPeriod)
        val days = fakeAnalyticsRepository.lastRequestedRange?.calendarDays ?: 0L
        assertTrue("THIS_MONTH calendar days must be between 1 and 31", days in 1L..31L)
    }

    // ─── 7. LAST_MONTH Selection ───────────────────────────────────────────

    @Test
    fun lastMonthSelection_resolvesLastMonthAndFetchesData() = testScope.runTest {
        val viewModel = createViewModel()
        testScheduler.advanceUntilIdle()

        viewModel.selectPeriod(AnalyticsPeriod.LAST_MONTH)
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(AnalyticsPeriod.LAST_MONTH, state.selectedPeriod)
        val days = fakeAnalyticsRepository.lastRequestedRange?.calendarDays ?: 0L
        assertTrue("LAST_MONTH calendar days must be between 28 and 31", days in 28L..31L)
    }

    // ─── 8. Custom Date-Range Selection ────────────────────────────────────

    @Test
    fun customDateRangeSelection_validRange_resolvesBoundaries() = testScope.runTest {
        val viewModel = createViewModel()
        testScheduler.advanceUntilIdle()

        val startMs = calendarMs(2026, Calendar.OCTOBER, 1, 10, 0, 0)
        val endMs = calendarMs(2026, Calendar.OCTOBER, 10, 18, 0, 0)

        viewModel.setCustomDateRange(startMs, endMs)
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(AnalyticsPeriod.CUSTOM, state.selectedPeriod)
        assertTrue(state.isCustomPeriod)
        assertEquals(startMs, state.selectedCustomStartDate)
        assertEquals(endMs, state.selectedCustomEndDate)
        assertNull(state.errorMessage)

        assertEquals(10L, fakeAnalyticsRepository.lastRequestedRange?.calendarDays)
    }

    // ─── 9. Invalid Custom Date Ranges ─────────────────────────────────────

    @Test
    fun invalidCustomDateRange_setsValidationErrorWithoutQuerying() = testScope.runTest {
        val viewModel = createViewModel()
        testScheduler.advanceUntilIdle()

        fakeAnalyticsRepository.lastRequestedRange = null

        val startMs = calendarMs(2026, Calendar.OCTOBER, 15, 0, 0, 0)
        val endMs = calendarMs(2026, Calendar.OCTOBER, 5, 0, 0, 0) // start > end

        viewModel.setCustomDateRange(startMs, endMs)
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNotNull(state.errorMessage)
        assertEquals("Start date cannot be after end date", state.errorMessage)
        assertNull("Repository should NOT have been queried with invalid range", fakeAnalyticsRepository.lastRequestedRange)
    }

    // ─── 10. Category Filter Selection ─────────────────────────────────────

    @Test
    fun categoryFilterSelection_filtersCategoryBreakdownCaseInsensitively() = testScope.runTest {
        val viewModel = createViewModel()
        testScheduler.advanceUntilIdle()

        viewModel.selectCategory("food")
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("food", state.selectedCategory)
        assertTrue(state.isFiltered)

        // Filtered category breakdown must only contain Food
        assertEquals(1, state.categorySpending.size)
        assertEquals("Food", state.categorySpending[0].displayName)
        assertEquals(30000L, state.categorySpending[0].totalPaise)

        // Available categories list must remain complete for the filter menu
        assertEquals(2, state.availableCategories.size)
        assertTrue(state.availableCategories.contains("Food"))
        assertTrue(state.availableCategories.contains("Transport"))

        // Spending summary reflects the filtered category
        assertEquals(30000L, state.spendingSummary?.totalSpendingPaise)
        assertEquals(1, state.spendingSummary?.transactionCount)
    }

    // ─── 11. Clearing Category Filters ─────────────────────────────────────

    @Test
    fun clearingCategoryFilter_restoresFullCategoryList() = testScope.runTest {
        val viewModel = createViewModel()
        testScheduler.advanceUntilIdle()

        viewModel.selectCategory("Food")
        testScheduler.advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.categorySpending.size)

        viewModel.clearCategoryFilter()
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNull(state.selectedCategory)
        assertEquals(2, state.categorySpending.size)
        assertEquals(50000L, state.spendingSummary?.totalSpendingPaise)
    }

    // ─── 12. Payment-Method Filter Selection ───────────────────────────────

    @Test
    fun paymentMethodFilterSelection_filtersPaymentMethods() = testScope.runTest {
        val viewModel = createViewModel()
        testScheduler.advanceUntilIdle()

        viewModel.selectPaymentMethod("UPI")
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("UPI", state.selectedPaymentMethod)
        assertTrue(state.isFiltered)

        // Filtered payment methods must only contain UPI
        assertEquals(1, state.paymentMethodSpending.size)
        assertEquals("UPI", state.paymentMethodSpending[0].paymentMethod)
        assertEquals(30000L, state.paymentMethodSpending[0].totalPaise)

        // Available payment methods list must remain complete
        assertEquals(2, state.availablePaymentMethods.size)

        // Summary reflects UPI spending
        assertEquals(30000L, state.spendingSummary?.totalSpendingPaise)
        assertEquals(1, state.spendingSummary?.transactionCount)
    }

    // ─── 13. Resetting Filters ─────────────────────────────────────────────

    @Test
    fun resetFilters_clearsAllFilters() = testScope.runTest {
        val viewModel = createViewModel()
        testScheduler.advanceUntilIdle()

        viewModel.selectCategory("Food")
        viewModel.selectPaymentMethod("UPI")
        testScheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isFiltered)

        viewModel.resetFilters()
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNull(state.selectedCategory)
        assertNull(state.selectedPaymentMethod)
        assertFalse(state.isFiltered)
        assertEquals(2, state.categorySpending.size)
        assertEquals(2, state.paymentMethodSpending.size)
        assertEquals(50000L, state.spendingSummary?.totalSpendingPaise)
    }

    // ─── 14. Rapid Filter Changes & Stale Result Prevention ────────────────

    @Test
    fun rapidFilterChanges_preventsStaleResults() = testScope.runTest {
        val viewModel = createViewModel()
        testScheduler.advanceUntilIdle()

        viewModel.selectPeriod(AnalyticsPeriod.THIS_WEEK)
        viewModel.selectPeriod(AnalyticsPeriod.THIS_MONTH)
        viewModel.selectPeriod(AnalyticsPeriod.LAST_MONTH)
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(AnalyticsPeriod.LAST_MONTH, state.selectedPeriod)
        assertFalse(state.isLoading)
    }

    // ─── 15. Session/User Changes ──────────────────────────────────────────

    @Test
    fun sessionUserChanges_switchesUserDataWithoutLeak() = testScope.runTest {
        val viewModel = createViewModel()
        testScheduler.advanceUntilIdle()
        assertEquals("user_real_1", fakeAnalyticsRepository.lastRequestedUserId)

        // Switch to user 2
        fakeSessionManager.sessionState.value = UserSession(isLoggedIn = true, userId = "user_real_2")
        testScheduler.advanceUntilIdle()

        assertEquals("user_real_2", fakeAnalyticsRepository.lastRequestedUserId)

        // Switch to logged out / guest
        fakeSessionManager.sessionState.value = UserSession(isLoggedIn = false, userId = null)
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("Session unavailable", state.errorMessage)
        assertNull(state.spendingSummary)
        assertTrue(state.categorySpending.isEmpty())
    }

    // ─── 16. Demo-Mode User Isolation ──────────────────────────────────────

    @Test
    fun demoModeUserIsolation_scopesStrictlyToDemoUser() = testScope.runTest {
        fakeSessionManager.sessionState.value = UserSession(isLoggedIn = false, isDemoMode = true, userId = "demo_user")
        val viewModel = createViewModel()
        testScheduler.advanceUntilIdle()

        assertEquals("demo_user", fakeAnalyticsRepository.lastRequestedUserId)

        fakeSessionManager.sessionState.value = UserSession(isLoggedIn = true, isDemoMode = false, userId = "real_user_xyz")
        testScheduler.advanceUntilIdle()

        assertEquals("real_user_xyz", fakeAnalyticsRepository.lastRequestedUserId)
    }

    // ─── 17. Cancellation Behavior ─────────────────────────────────────────

    @Test
    fun cancellationBehavior_cancelsInFlightRequestsOnPeriodChange() = testScope.runTest {
        fakeAnalyticsRepository.emissionDelayMs = 500L
        val viewModel = createViewModel()

        viewModel.selectPeriod(AnalyticsPeriod.THIS_WEEK)
        testScheduler.advanceTimeBy(100L)

        viewModel.selectPeriod(AnalyticsPeriod.LAST_MONTH)
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(AnalyticsPeriod.LAST_MONTH, state.selectedPeriod)
        assertFalse(state.isLoading)
    }

    // ─── 18. Reactive: New Eligible Transaction Updates All Sections ──────

    @Test
    fun newEligibleTransaction_automaticallyUpdatesAllAnalyticsSections() = testScope.runTest {
        val viewModel = createViewModel()
        testScheduler.advanceUntilIdle()

        val initialSummary = viewModel.uiState.value.spendingSummary
        assertEquals(50000L, initialSummary?.totalSpendingPaise)
        assertEquals(2, initialSummary?.transactionCount)

        // Insert new eligible transaction: ₹100 Food via UPI on Nov 3
        val nov3Date = calendarMs(2026, Calendar.NOVEMBER, 3, 12, 0, 0)
        fakeAnalyticsRepository.insertTransaction(
            amountPaise = 10000L,
            category = "Food",
            paymentMethod = "UPI",
            recipientName = "Zomato",
            date = nov3Date,
            status = "SUCCESS",
            direction = "DEBIT"
        )
        testScheduler.advanceUntilIdle()

        val updatedState = viewModel.uiState.value

        // 1. Summary and count update automatically
        assertEquals(60000L, updatedState.spendingSummary?.totalSpendingPaise)
        assertEquals(3, updatedState.spendingSummary?.transactionCount)

        // 2. Category breakdown updates automatically (Food increased from 30000 to 40000)
        val foodCat = updatedState.categorySpending.find { it.displayName == "Food" }
        assertNotNull(foodCat)
        assertEquals(40000L, foodCat?.totalPaise)
        assertEquals(2, foodCat?.transactionCount)

        // 3. Payment-method breakdown updates automatically (UPI increased from 30000 to 40000)
        val upiMethod = updatedState.paymentMethodSpending.find { it.paymentMethod == "UPI" }
        assertNotNull(upiMethod)
        assertEquals(40000L, upiMethod?.totalPaise)
        assertEquals(2, upiMethod?.transactionCount)

        // 4. Recipient breakdown updates automatically (Zomato present)
        val zomato = updatedState.recipientSpending.find { it.recipientName == "Zomato" }
        assertNotNull(zomato)
        assertEquals(10000L, zomato?.totalPaise)

        // 5. Daily and highest spending updates
        assertTrue(updatedState.dailySpending.any { it.totalPaise == 10000L })
    }

    // ─── 19. Reactive: Ineligible Transactions Do Not Affect Spending ─────

    @Test
    fun ineligibleTransaction_doesNotUpdateSpendingTotals() = testScope.runTest {
        val viewModel = createViewModel()
        testScheduler.advanceUntilIdle()

        assertEquals(50000L, viewModel.uiState.value.spendingSummary?.totalSpendingPaise)
        assertEquals(2, viewModel.uiState.value.spendingSummary?.transactionCount)

        // Insert CREDIT transaction (refund/income)
        fakeAnalyticsRepository.insertTransaction(
            amountPaise = 50000L,
            category = "Salary",
            paymentMethod = "BANK_TRANSFER",
            recipientName = "Employer",
            status = "SUCCESS",
            direction = "CREDIT"
        )

        // Insert PENDING transaction
        fakeAnalyticsRepository.insertTransaction(
            amountPaise = 25000L,
            category = "Food",
            paymentMethod = "UPI",
            recipientName = "Cafe",
            status = "PENDING",
            direction = "DEBIT"
        )

        // Insert FAILED transaction
        fakeAnalyticsRepository.insertTransaction(
            amountPaise = 15000L,
            category = "Shopping",
            paymentMethod = "CARD",
            recipientName = "Mall",
            status = "FAILED",
            direction = "DEBIT"
        )
        testScheduler.advanceUntilIdle()

        // Totals must remain unchanged
        val state = viewModel.uiState.value
        assertEquals(50000L, state.spendingSummary?.totalSpendingPaise)
        assertEquals(2, state.spendingSummary?.transactionCount)
    }

    // ─── 20. Combined Filter: Category + Payment Method (Intersection AND) ─

    @Test
    fun categoryAndPaymentMethodCombinedFiltering_usesExactIntersection() = testScope.runTest {
        val viewModel = createViewModel()
        testScheduler.advanceUntilIdle()

        // Dataset has:
        // Food via UPI: 30000L
        // Transport via CASH: 20000L

        // Select Food + UPI -> matches Food via UPI (30000L)
        viewModel.selectCategory("Food")
        viewModel.selectPaymentMethod("UPI")
        testScheduler.advanceUntilIdle()

        var state = viewModel.uiState.value
        assertEquals(30000L, state.spendingSummary?.totalSpendingPaise)
        assertEquals(1, state.spendingSummary?.transactionCount)
        assertEquals(1, state.categorySpending.size)
        assertEquals("Food", state.categorySpending[0].displayName)
        assertEquals(1, state.paymentMethodSpending.size)
        assertEquals("UPI", state.paymentMethodSpending[0].paymentMethod)

        // Select Food + CASH -> intersection is EMPTY (no Food was paid via CASH)
        // Must NOT calculate by adding Food (30000L) + CASH (20000L) = 50000L!
        viewModel.selectPaymentMethod("CASH")
        testScheduler.advanceUntilIdle()

        state = viewModel.uiState.value
        assertEquals(0L, state.spendingSummary?.totalSpendingPaise)
        assertEquals(0, state.spendingSummary?.transactionCount)
        assertTrue(state.categorySpending.isEmpty())
        assertTrue(state.paymentMethodSpending.isEmpty())

        // Select Transport + CASH -> matches Transport via CASH (20000L)
        viewModel.selectCategory("Transport")
        testScheduler.advanceUntilIdle()

        state = viewModel.uiState.value
        assertEquals(20000L, state.spendingSummary?.totalSpendingPaise)
        assertEquals(1, state.spendingSummary?.transactionCount)
        assertEquals("Transport", state.categorySpending[0].displayName)
        assertEquals("CASH", state.paymentMethodSpending[0].paymentMethod)
    }

    // ─── 21. Clearing Category While Retaining Payment Method Filter ───────

    @Test
    fun clearingCategoryFilter_retainsPaymentMethodFilter() = testScope.runTest {
        val viewModel = createViewModel()
        testScheduler.advanceUntilIdle()

        // Filter by Food + CASH (intersection empty = 0L)
        viewModel.selectCategory("Food")
        viewModel.selectPaymentMethod("CASH")
        testScheduler.advanceUntilIdle()
        assertEquals(0L, viewModel.uiState.value.spendingSummary?.totalSpendingPaise)

        // Clear category filter -> only CASH remains active!
        viewModel.clearCategoryFilter()
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNull(state.selectedCategory)
        assertEquals("CASH", state.selectedPaymentMethod)
        assertTrue(state.isFiltered)

        // Must show all CASH transactions (Transport: 20000L)
        assertEquals(20000L, state.spendingSummary?.totalSpendingPaise)
        assertEquals(1, state.spendingSummary?.transactionCount)
        assertEquals(1, state.paymentMethodSpending.size)
        assertEquals("CASH", state.paymentMethodSpending[0].paymentMethod)
    }

    // ─── 22. Clearing Payment Method While Retaining Category Filter ───────

    @Test
    fun clearingPaymentMethodFilter_retainsCategoryFilter() = testScope.runTest {
        val viewModel = createViewModel()
        testScheduler.advanceUntilIdle()

        // Filter by Food + CASH (intersection empty = 0L)
        viewModel.selectCategory("Food")
        viewModel.selectPaymentMethod("CASH")
        testScheduler.advanceUntilIdle()
        assertEquals(0L, viewModel.uiState.value.spendingSummary?.totalSpendingPaise)

        // Clear payment method filter -> only Food remains active!
        viewModel.clearPaymentMethodFilter()
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("Food", state.selectedCategory)
        assertNull(state.selectedPaymentMethod)
        assertTrue(state.isFiltered)

        // Must show all Food transactions (Food via UPI: 30000L)
        assertEquals(30000L, state.spendingSummary?.totalSpendingPaise)
        assertEquals(1, state.spendingSummary?.transactionCount)
        assertEquals(1, state.categorySpending.size)
        assertEquals("Food", state.categorySpending[0].displayName)
    }

    // ─── 23. Resetting Both Filters ────────────────────────────────────────

    @Test
    fun resettingBothFilters_restoresUnfilteredAnalyticsAndOptions() = testScope.runTest {
        val viewModel = createViewModel()
        testScheduler.advanceUntilIdle()

        viewModel.selectCategory("Food")
        viewModel.selectPaymentMethod("UPI")
        testScheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isFiltered)

        viewModel.resetFilters()
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNull(state.selectedCategory)
        assertNull(state.selectedPaymentMethod)
        assertFalse(state.isFiltered)

        // Full totals restored
        assertEquals(50000L, state.spendingSummary?.totalSpendingPaise)
        assertEquals(2, state.spendingSummary?.transactionCount)
        assertEquals(2, state.categorySpending.size)
        assertEquals(2, state.paymentMethodSpending.size)
        assertEquals(2, state.availableCategories.size)
        assertEquals(2, state.availablePaymentMethods.size)
    }

    // ─── Helpers ───────────────────────────────────────────────────────────

    private fun calendarMs(year: Int, month: Int, day: Int, hour: Int, min: Int, sec: Int): Long {
        return Calendar.getInstance(tz).apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month)
            set(Calendar.DAY_OF_MONTH, day)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, min)
            set(Calendar.SECOND, sec)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
}

// ─── Test Fakes ─────────────────────────────────────────────────────────────

private class FakeSessionManager(
    initialSession: UserSession = UserSession(isLoggedIn = true, userId = "user_test_1")
) : SessionManager {

    val sessionState = MutableStateFlow(initialSession)
    override val sessionFlow: Flow<UserSession> = sessionState

    override suspend fun startUserSession(user: UserEntity) {
        sessionState.value = UserSession(isLoggedIn = true, userId = user.userId, email = user.email, fullName = user.fullName)
    }

    override suspend fun startDemoSession() {
        sessionState.value = UserSession(isLoggedIn = false, isDemoMode = true, userId = "demo_user", email = "demo@upisegregator.local", fullName = "Demo User")
    }

    override suspend fun clearSession() {
        sessionState.value = UserSession()
    }

    override suspend fun setActivePaymentTransactionId(transactionId: String) {}
    override suspend fun getActivePaymentTransactionId(): String? = null
    override suspend fun clearActivePaymentTransactionId() {}
}

private class FakeAnalyticsRepository : AnalyticsRepository {

    var shouldFail: Boolean = false
    var failureMessage: String = "Repository error"
    var emissionDelayMs: Long = 0L

    var lastRequestedUserId: String? = null
    var lastRequestedRange: ResolvedDateRange? = null
    var lastRequestedTimeZone: TimeZone? = null

    // Mutable list of eligible transactions for reactive updates and filter tests
    val transactionsFlow = MutableStateFlow<List<EligibleTransactionAnalyticsRow>>(
        listOf(
            EligibleTransactionAnalyticsRow(
                transactionDate = 1730419200000L, // Nov 1
                amountPaise = 30000L,
                category = "Food",
                paymentMethod = "UPI",
                recipientName = "Swiggy"
            ),
            EligibleTransactionAnalyticsRow(
                transactionDate = 1730505600000L, // Nov 2
                amountPaise = 20000L,
                category = "Transport",
                paymentMethod = "CASH",
                recipientName = "Uber"
            )
        )
    )

    fun insertTransaction(
        amountPaise: Long,
        category: String,
        paymentMethod: String,
        recipientName: String,
        date: Long = System.currentTimeMillis(),
        status: String = "SUCCESS",
        direction: String = "DEBIT"
    ) {
        if (status == "SUCCESS" && direction == "DEBIT") {
            val current = transactionsFlow.value.toMutableList()
            current.add(
                EligibleTransactionAnalyticsRow(
                    transactionDate = date,
                    amountPaise = amountPaise,
                    category = category,
                    paymentMethod = paymentMethod,
                    recipientName = recipientName
                )
            )
            transactionsFlow.value = current
        }
    }

    override fun getPeriodAnalytics(
        userId: String,
        range: ResolvedDateRange,
        timeZone: TimeZone
    ): Flow<PeriodAnalyticsData> = flow {
        if (emissionDelayMs > 0L) delay(emissionDelayMs)
        if (shouldFail) throw RuntimeException(failureMessage)
        lastRequestedUserId = userId
        lastRequestedRange = range
        lastRequestedTimeZone = timeZone

        transactionsFlow.collect { txs ->
            val total = txs.sumOf { it.amountPaise }
            val count = txs.size
            val avg = if (range.calendarDays > 0L) total / range.calendarDays else 0L

            val summary = SpendingSummary(
                totalSpendingPaise = total,
                transactionCount = count,
                averageDailySpendingPaise = avg,
                calendarDays = range.calendarDays,
                previousPeriodComparison = PreviousPeriodComparison.Available(
                    previousSpendingPaise = 40000L,
                    changePercent = 25L
                )
            )

            val catGroups = txs.groupBy { CategoryNormalizer.normalizeKey(it.category) }
            val categories = catGroups.map { (key, items) ->
                val catTotal = items.sumOf { it.amountPaise }
                val name = CategoryNormalizer.bestDisplayName(items.map { it.category })
                CategorySpending(
                    normalizedCategory = key,
                    displayName = name,
                    totalPaise = catTotal,
                    transactionCount = items.size,
                    percentage = if (total > 0L) (catTotal * 100L) / total else 0L
                )
            }.sortedByDescending { it.totalPaise }

            val pmGroups = txs.groupBy { it.paymentMethod }
            val paymentMethods = pmGroups.map { (method, items) ->
                val pmTotal = items.sumOf { it.amountPaise }
                PaymentMethodSpending(
                    paymentMethod = method,
                    totalPaise = pmTotal,
                    transactionCount = items.size,
                    percentage = if (total > 0L) (pmTotal * 100L) / total else 0L
                )
            }.sortedByDescending { it.totalPaise }

            val recGroups = txs.groupBy { it.recipientName }
            val recipients = recGroups.map { (name, items) ->
                val recTotal = items.sumOf { it.amountPaise }
                RecipientSpending(
                    recipientName = name,
                    totalPaise = recTotal,
                    transactionCount = items.size,
                    percentage = if (total > 0L) (recTotal * 100L) / total else 0L
                )
            }.sortedByDescending { it.totalPaise }.take(10)

            val daily = computeDailyTrend(txs, range, timeZone)
            val weekly = computeWeeklyTrend(daily, timeZone)
            val monthly = computeMonthlyTrend(daily, timeZone)
            val highest = computeHighestDay(daily)

            emit(
                PeriodAnalyticsData(
                    range = range,
                    summary = summary,
                    allTransactions = txs,
                    categories = categories,
                    paymentMethods = paymentMethods,
                    recipients = recipients,
                    dailyTrend = daily,
                    weeklyTrend = weekly,
                    monthlyTrend = monthly,
                    highestDay = highest
                )
            )
        }
    }

    override fun getSpendingSummary(
        userId: String,
        range: ResolvedDateRange,
        timeZone: TimeZone
    ): Flow<SpendingSummary> = flow {
        if (emissionDelayMs > 0L) delay(emissionDelayMs)
        if (shouldFail) throw RuntimeException(failureMessage)
        emit(
            SpendingSummary(
                totalSpendingPaise = 50000L,
                transactionCount = 2,
                averageDailySpendingPaise = 7142L,
                calendarDays = range.calendarDays,
                previousPeriodComparison = PreviousPeriodComparison.Available(40000L, 25L)
            )
        )
    }

    override fun getCategoryBreakdown(
        userId: String,
        range: ResolvedDateRange
    ): Flow<List<CategorySpending>> = flow {
        if (emissionDelayMs > 0L) delay(emissionDelayMs)
        if (shouldFail) throw RuntimeException(failureMessage)
        emit(emptyList())
    }

    override fun getPaymentMethodBreakdown(
        userId: String,
        range: ResolvedDateRange
    ): Flow<List<PaymentMethodSpending>> = flow {
        if (emissionDelayMs > 0L) delay(emissionDelayMs)
        if (shouldFail) throw RuntimeException(failureMessage)
        emit(emptyList())
    }

    override fun getRecipientBreakdown(
        userId: String,
        range: ResolvedDateRange,
        limit: Int
    ): Flow<List<RecipientSpending>> = flow {
        if (emissionDelayMs > 0L) delay(emissionDelayMs)
        if (shouldFail) throw RuntimeException(failureMessage)
        emit(emptyList())
    }

    override suspend fun getDailySpendingTrend(
        userId: String,
        range: ResolvedDateRange,
        timeZone: TimeZone
    ): List<DailySpending> = emptyList()

    override suspend fun getWeeklySpendingTrend(
        userId: String,
        range: ResolvedDateRange,
        timeZone: TimeZone
    ): List<WeeklySpending> = emptyList()

    override suspend fun getMonthlySpendingTrend(
        userId: String,
        range: ResolvedDateRange,
        timeZone: TimeZone
    ): List<MonthlySpending> = emptyList()

    override suspend fun getHighestSpendingDay(
        userId: String,
        range: ResolvedDateRange,
        timeZone: TimeZone
    ): HighestSpendingDay? = null
}

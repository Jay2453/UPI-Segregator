package com.upisegregator.app.domain.analytics

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/**
 * Tests for [DateRangeHelper] — DST-safe calendar-day counting
 * and period resolution.
 */
class DateRangeHelperTest {

    private lateinit var originalTimeZone: TimeZone

    @Before
    fun setUp() {
        originalTimeZone = TimeZone.getDefault()
    }

    @After
    fun tearDown() {
        TimeZone.setDefault(originalTimeZone)
    }

    // ─── calendarDaysInPeriod ───────────────────────────────────────────────

    @Test
    fun calendarDaysInPeriod_normal7DayPeriod_returns7() {
        val tz = TimeZone.getTimeZone("America/New_York")
        // Monday Nov 4, 2024 00:00 to Sunday Nov 10, 2024 23:59:59.999
        val startMs = calendarMs(2024, Calendar.NOVEMBER, 4, 0, 0, 0, tz)
        val endMs = calendarMs(2024, Calendar.NOVEMBER, 10, 23, 59, 59, tz)

        assertEquals(7L, DateRangeHelper.calendarDaysInPeriod(startMs, endMs, tz))
    }

    @Test
    fun calendarDaysInPeriod_singleDay_returns1() {
        val tz = TimeZone.getTimeZone("America/New_York")
        val startMs = calendarMs(2024, Calendar.MARCH, 15, 0, 0, 0, tz)
        val endMs = calendarMs(2024, Calendar.MARCH, 15, 23, 59, 59, tz)

        assertEquals(1L, DateRangeHelper.calendarDaysInPeriod(startMs, endMs, tz))
    }

    @Test
    fun calendarDaysInPeriod_monthBoundary_correctCount() {
        val tz = TimeZone.getTimeZone("America/New_York")
        // Oct 28 to Nov 3, 2024 = 7 days
        val startMs = calendarMs(2024, Calendar.OCTOBER, 28, 0, 0, 0, tz)
        val endMs = calendarMs(2024, Calendar.NOVEMBER, 3, 23, 59, 59, tz)

        assertEquals(7L, DateRangeHelper.calendarDaysInPeriod(startMs, endMs, tz))
    }

    @Test
    fun calendarDaysInPeriod_decemberToJanuaryRollover_correctCount() {
        val tz = TimeZone.getTimeZone("America/New_York")
        // Dec 29, 2024 to Jan 4, 2025 = 7 days
        val startMs = calendarMs(2024, Calendar.DECEMBER, 29, 0, 0, 0, tz)
        val endMs = calendarMs(2025, Calendar.JANUARY, 4, 23, 59, 59, tz)

        assertEquals(7L, DateRangeHelper.calendarDaysInPeriod(startMs, endMs, tz))
    }

    @Test
    fun calendarDaysInPeriod_leapYearFebruary_29Days() {
        val tz = TimeZone.getTimeZone("America/New_York")
        // Feb 1 to Feb 29, 2024 (leap year) = 29 days
        val startMs = calendarMs(2024, Calendar.FEBRUARY, 1, 0, 0, 0, tz)
        val endMs = calendarMs(2024, Calendar.FEBRUARY, 29, 23, 59, 59, tz)

        assertEquals(29L, DateRangeHelper.calendarDaysInPeriod(startMs, endMs, tz))
    }

    @Test
    fun calendarDaysInPeriod_nonLeapYearFebruary_28Days() {
        val tz = TimeZone.getTimeZone("America/New_York")
        // Feb 1 to Feb 28, 2023 (non-leap year) = 28 days
        val startMs = calendarMs(2023, Calendar.FEBRUARY, 1, 0, 0, 0, tz)
        val endMs = calendarMs(2023, Calendar.FEBRUARY, 28, 23, 59, 59, tz)

        assertEquals(28L, DateRangeHelper.calendarDaysInPeriod(startMs, endMs, tz))
    }

    @Test
    fun calendarDaysInPeriod_springDstTransition_correctCount() {
        // Spring forward: March 10, 2024 in America/New_York — day is 23 hours
        val tz = TimeZone.getTimeZone("America/New_York")
        // March 9 to March 11, 2024 = 3 days (even though one day has 23 hours)
        val startMs = calendarMs(2024, Calendar.MARCH, 9, 0, 0, 0, tz)
        val endMs = calendarMs(2024, Calendar.MARCH, 11, 23, 59, 59, tz)

        assertEquals(3L, DateRangeHelper.calendarDaysInPeriod(startMs, endMs, tz))
    }

    @Test
    fun calendarDaysInPeriod_autumnDstTransition_correctCount() {
        // Fall back: November 3, 2024 in America/New_York — day is 25 hours
        val tz = TimeZone.getTimeZone("America/New_York")
        // Nov 2 to Nov 4, 2024 = 3 days (even though one day has 25 hours)
        val startMs = calendarMs(2024, Calendar.NOVEMBER, 2, 0, 0, 0, tz)
        val endMs = calendarMs(2024, Calendar.NOVEMBER, 4, 23, 59, 59, tz)

        assertEquals(3L, DateRangeHelper.calendarDaysInPeriod(startMs, endMs, tz))
    }

    @Test
    fun calendarDaysInPeriod_springDstTransitionWeek_7Days() {
        // Full week spanning spring DST: March 7-13, 2024
        val tz = TimeZone.getTimeZone("America/New_York")
        val startMs = calendarMs(2024, Calendar.MARCH, 7, 0, 0, 0, tz)
        val endMs = calendarMs(2024, Calendar.MARCH, 13, 23, 59, 59, tz)

        assertEquals(7L, DateRangeHelper.calendarDaysInPeriod(startMs, endMs, tz))
    }

    @Test
    fun calendarDaysInPeriod_autumnDstTransitionWeek_7Days() {
        // Full week spanning autumn DST: Oct 31 - Nov 6, 2024
        val tz = TimeZone.getTimeZone("America/New_York")
        val startMs = calendarMs(2024, Calendar.OCTOBER, 31, 0, 0, 0, tz)
        val endMs = calendarMs(2024, Calendar.NOVEMBER, 6, 23, 59, 59, tz)

        assertEquals(7L, DateRangeHelper.calendarDaysInPeriod(startMs, endMs, tz))
    }

    @Test
    fun calendarDaysInPeriod_reversedRange_throwsException() {
        val tz = TimeZone.getTimeZone("America/New_York")
        val startMs = calendarMs(2024, Calendar.MARCH, 15, 0, 0, 0, tz)
        val endMs = calendarMs(2024, Calendar.MARCH, 10, 23, 59, 59, tz)

        assertThrows(IllegalArgumentException::class.java) {
            DateRangeHelper.calendarDaysInPeriod(startMs, endMs, tz)
        }
    }

    @Test
    fun calendarDaysInPeriod_sameTimestamp_returns1() {
        val tz = TimeZone.getTimeZone("America/New_York")
        val ts = calendarMs(2024, Calendar.JUNE, 15, 12, 30, 0, tz)

        assertEquals(1L, DateRangeHelper.calendarDaysInPeriod(ts, ts, tz))
    }

    @Test
    fun calendarDaysInPeriod_fullMonthJanuary_31Days() {
        val tz = TimeZone.getTimeZone("America/New_York")
        val startMs = calendarMs(2024, Calendar.JANUARY, 1, 0, 0, 0, tz)
        val endMs = calendarMs(2024, Calendar.JANUARY, 31, 23, 59, 59, tz)

        assertEquals(31L, DateRangeHelper.calendarDaysInPeriod(startMs, endMs, tz))
    }

    // ─── Period Resolution ─────────────────────────────────────────────────

    @Test
    fun resolveLastMonth_returnsFullPreviousMonth() {
        val tz = TimeZone.getTimeZone("Asia/Kolkata")
        TimeZone.setDefault(tz)

        val range = DateRangeHelper.resolvePeriod(AnalyticsPeriod.LAST_MONTH, timeZone = tz)

        // Should cover the entire previous month
        val nowCal = Calendar.getInstance(tz)
        val expectedCal = Calendar.getInstance(tz).apply {
            add(Calendar.MONTH, -1)
        }
        val expectedDays = expectedCal.getActualMaximum(Calendar.DAY_OF_MONTH).toLong()
        assertEquals(expectedDays, range.calendarDays)
    }

    @Test
    fun resolveCustom_validRange_returnsCorrectBoundaries() {
        val tz = TimeZone.getTimeZone("Asia/Kolkata")
        val startMs = calendarMs(2024, Calendar.MARCH, 1, 10, 30, 0, tz)
        val endMs = calendarMs(2024, Calendar.MARCH, 7, 15, 45, 0, tz)

        val range = DateRangeHelper.resolvePeriod(
            AnalyticsPeriod.CUSTOM, startMs, endMs, tz
        )

        assertEquals(7L, range.calendarDays)
        // Start should be 00:00:00.000 of March 1
        val startCal = Calendar.getInstance(tz).apply { timeInMillis = range.startMs }
        assertEquals(0, startCal.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, startCal.get(Calendar.MINUTE))
        // End should be 23:59:59.999 of March 7
        val endCal = Calendar.getInstance(tz).apply { timeInMillis = range.endMs }
        assertEquals(23, endCal.get(Calendar.HOUR_OF_DAY))
        assertEquals(59, endCal.get(Calendar.MINUTE))
    }

    @Test
    fun resolveCustom_reversedRange_throwsException() {
        val tz = TimeZone.getTimeZone("Asia/Kolkata")
        val startMs = calendarMs(2024, Calendar.MARCH, 10, 0, 0, 0, tz)
        val endMs = calendarMs(2024, Calendar.MARCH, 5, 0, 0, 0, tz)

        assertThrows(IllegalArgumentException::class.java) {
            DateRangeHelper.resolvePeriod(AnalyticsPeriod.CUSTOM, startMs, endMs, tz)
        }
    }

    @Test
    fun resolveCustom_singleDay_returns1Day() {
        val tz = TimeZone.getTimeZone("Asia/Kolkata")
        val ts = calendarMs(2024, Calendar.JUNE, 15, 14, 0, 0, tz)

        val range = DateRangeHelper.resolvePeriod(AnalyticsPeriod.CUSTOM, ts, ts, tz)
        assertEquals(1L, range.calendarDays)
    }

    // ─── Previous Period ────────────────────────────────────────────────────

    @Test
    fun previousPeriod_7DayRange_returns7DayPreviousPeriod() {
        val tz = TimeZone.getTimeZone("Asia/Kolkata")
        val current = ResolvedDateRange(
            startMs = calendarMs(2024, Calendar.NOVEMBER, 4, 0, 0, 0, tz),
            endMs = calendarMs(2024, Calendar.NOVEMBER, 10, 23, 59, 59, tz),
            calendarDays = 7L
        )

        val previous = DateRangeHelper.previousPeriod(current, tz)
        assertEquals(7L, previous.calendarDays)
        // Previous period should end on Nov 3
        val endCal = Calendar.getInstance(tz).apply { timeInMillis = previous.endMs }
        assertEquals(3, endCal.get(Calendar.DAY_OF_MONTH))
        assertEquals(Calendar.NOVEMBER, endCal.get(Calendar.MONTH))
    }

    @Test
    fun previousPeriod_crossingYearBoundary_correctDays() {
        val tz = TimeZone.getTimeZone("America/New_York")
        val current = ResolvedDateRange(
            startMs = calendarMs(2025, Calendar.JANUARY, 1, 0, 0, 0, tz),
            endMs = calendarMs(2025, Calendar.JANUARY, 7, 23, 59, 59, tz),
            calendarDays = 7L
        )

        val previous = DateRangeHelper.previousPeriod(current, tz)
        assertEquals(7L, previous.calendarDays)
        // Should end Dec 31, 2024
        val endCal = Calendar.getInstance(tz).apply { timeInMillis = previous.endMs }
        assertEquals(31, endCal.get(Calendar.DAY_OF_MONTH))
        assertEquals(Calendar.DECEMBER, endCal.get(Calendar.MONTH))
        assertEquals(2024, endCal.get(Calendar.YEAR))
    }

    @Test
    fun previousPeriod_singleDay_returnsSinglePreviousDay() {
        val tz = TimeZone.getTimeZone("Asia/Kolkata")
        val current = ResolvedDateRange(
            startMs = calendarMs(2024, Calendar.MARCH, 15, 0, 0, 0, tz),
            endMs = calendarMs(2024, Calendar.MARCH, 15, 23, 59, 59, tz),
            calendarDays = 1L
        )

        val previous = DateRangeHelper.previousPeriod(current, tz)
        assertEquals(1L, previous.calendarDays)
        val cal = Calendar.getInstance(tz).apply { timeInMillis = previous.startMs }
        assertEquals(14, cal.get(Calendar.DAY_OF_MONTH))
    }

    // ─── DST-aware Period Resolution ────────────────────────────────────────

    @Test
    fun resolveCustom_spanningSpringDst_correctDayCount() {
        val tz = TimeZone.getTimeZone("America/New_York")
        // March 8 to March 12, 2024 (spans DST spring forward on March 10)
        val startMs = calendarMs(2024, Calendar.MARCH, 8, 0, 0, 0, tz)
        val endMs = calendarMs(2024, Calendar.MARCH, 12, 23, 59, 59, tz)

        val range = DateRangeHelper.resolvePeriod(AnalyticsPeriod.CUSTOM, startMs, endMs, tz)
        assertEquals(5L, range.calendarDays)
    }

    @Test
    fun resolveCustom_spanningAutumnDst_correctDayCount() {
        val tz = TimeZone.getTimeZone("America/New_York")
        // Nov 1 to Nov 5, 2024 (spans DST fall back on Nov 3)
        val startMs = calendarMs(2024, Calendar.NOVEMBER, 1, 0, 0, 0, tz)
        val endMs = calendarMs(2024, Calendar.NOVEMBER, 5, 23, 59, 59, tz)

        val range = DateRangeHelper.resolvePeriod(AnalyticsPeriod.CUSTOM, startMs, endMs, tz)
        assertEquals(5L, range.calendarDays)
    }

    // ─── THIS_WEEK and THIS_MONTH ──────────────────────────────────────────

    @Test
    fun resolveThisWeek_calendarDaysAtLeast1() {
        val tz = TimeZone.getTimeZone("Asia/Kolkata")
        val range = DateRangeHelper.resolvePeriod(AnalyticsPeriod.THIS_WEEK, timeZone = tz)
        assertTrue("THIS_WEEK should have >= 1 day", range.calendarDays >= 1L)
        assertTrue("THIS_WEEK should have <= 7 days", range.calendarDays <= 7L)
    }

    @Test
    fun resolveThisMonth_calendarDaysAtLeast1() {
        val tz = TimeZone.getTimeZone("Asia/Kolkata")
        val range = DateRangeHelper.resolvePeriod(AnalyticsPeriod.THIS_MONTH, timeZone = tz)
        assertTrue("THIS_MONTH should have >= 1 day", range.calendarDays >= 1L)
        assertTrue("THIS_MONTH should have <= 31 days", range.calendarDays <= 31L)
    }

    // ─── Helper ─────────────────────────────────────────────────────────────

    private fun calendarMs(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int,
        second: Int,
        tz: TimeZone
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

package com.upisegregator.app.domain.analytics

import java.util.Calendar
import java.util.TimeZone

/**
 * DST-safe date range calculation helper.
 *
 * All boundaries use the device-local timezone and inclusive calendar dates.
 * Calendar-day counts use [Calendar] arithmetic (DAY_OF_YEAR increments),
 * NOT elapsed-millisecond division, so 23-hour and 25-hour DST days are
 * counted correctly.
 */
object DateRangeHelper {

    /**
     * Resolves an [AnalyticsPeriod] to concrete millisecond boundaries.
     *
     * @param period       The period to resolve.
     * @param customStartMs Start timestamp for CUSTOM periods (ignored otherwise).
     * @param customEndMs   End timestamp for CUSTOM periods (ignored otherwise).
     * @param timeZone      Timezone to use (defaults to device-local).
     * @return A [ResolvedDateRange] with inclusive boundaries and calendar-day count.
     */
    fun resolvePeriod(
        period: AnalyticsPeriod,
        customStartMs: Long? = null,
        customEndMs: Long? = null,
        timeZone: TimeZone = TimeZone.getDefault()
    ): ResolvedDateRange {
        val now = Calendar.getInstance(timeZone)
        return when (period) {
            AnalyticsPeriod.THIS_WEEK -> resolveThisWeek(now, timeZone)
            AnalyticsPeriod.THIS_MONTH -> resolveThisMonth(now, timeZone)
            AnalyticsPeriod.LAST_MONTH -> resolveLastMonth(now, timeZone)
            AnalyticsPeriod.CUSTOM -> {
                requireNotNull(customStartMs) { "customStartMs required for CUSTOM period" }
                requireNotNull(customEndMs) { "customEndMs required for CUSTOM period" }
                resolveCustom(customStartMs, customEndMs, timeZone)
            }
        }
    }

    /**
     * Calculates the previous equivalent period for comparison.
     *
     * The previous period has the same number of calendar days and ends
     * immediately before the current period starts.
     */
    fun previousPeriod(
        currentRange: ResolvedDateRange,
        timeZone: TimeZone = TimeZone.getDefault()
    ): ResolvedDateRange {
        val endCal = Calendar.getInstance(timeZone).apply {
            timeInMillis = currentRange.startMs
            // Go back 1 day from current start → last day of previous period
            add(Calendar.DAY_OF_YEAR, -1)
        }
        val endMs = endOfDay(endCal)

        val startCal = Calendar.getInstance(timeZone).apply {
            timeInMillis = endMs
            // Go back (calendarDays - 1) to get the first day
            add(Calendar.DAY_OF_YEAR, -(currentRange.calendarDays.toInt() - 1))
        }
        val startMs = startOfDay(startCal)

        val days = calendarDaysInPeriod(startMs, endMs, timeZone)
        return ResolvedDateRange(startMs = startMs, endMs = endMs, calendarDays = days)
    }

    /**
     * Counts the number of calendar days between two timestamps (inclusive).
     *
     * Uses [Calendar.DAY_OF_YEAR] increments — DST-safe.
     *
     * @throws IllegalArgumentException if startMs > endMs.
     */
    fun calendarDaysInPeriod(
        startMs: Long,
        endMs: Long,
        timeZone: TimeZone = TimeZone.getDefault()
    ): Long {
        require(startMs <= endMs) {
            "startMs ($startMs) must be <= endMs ($endMs)"
        }

        val startCal = Calendar.getInstance(timeZone).apply {
            timeInMillis = startMs
            // Normalise to start of day
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val endCal = Calendar.getInstance(timeZone).apply {
            timeInMillis = endMs
            // Normalise to start of day
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        var days = 0L
        while (!startCal.after(endCal)) {
            days++
            startCal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return days
    }

    // ─── Private Helpers ────────────────────────────────────────────────────

    private fun resolveThisWeek(now: Calendar, tz: TimeZone): ResolvedDateRange {
        val startCal = now.clone() as Calendar
        startCal.set(Calendar.DAY_OF_WEEK, startCal.firstDayOfWeek)
        val startMs = startOfDay(startCal)
        val endMs = endOfDay(now)
        val days = calendarDaysInPeriod(startMs, endMs, tz)
        return ResolvedDateRange(startMs = startMs, endMs = endMs, calendarDays = days)
    }

    private fun resolveThisMonth(now: Calendar, tz: TimeZone): ResolvedDateRange {
        val startCal = now.clone() as Calendar
        startCal.set(Calendar.DAY_OF_MONTH, 1)
        val startMs = startOfDay(startCal)
        val endMs = endOfDay(now)
        val days = calendarDaysInPeriod(startMs, endMs, tz)
        return ResolvedDateRange(startMs = startMs, endMs = endMs, calendarDays = days)
    }

    private fun resolveLastMonth(now: Calendar, tz: TimeZone): ResolvedDateRange {
        val startCal = now.clone() as Calendar
        startCal.add(Calendar.MONTH, -1)
        startCal.set(Calendar.DAY_OF_MONTH, 1)
        val startMs = startOfDay(startCal)

        val endCal = startCal.clone() as Calendar
        endCal.set(Calendar.DAY_OF_MONTH, endCal.getActualMaximum(Calendar.DAY_OF_MONTH))
        val endMs = endOfDay(endCal)

        val days = calendarDaysInPeriod(startMs, endMs, tz)
        return ResolvedDateRange(startMs = startMs, endMs = endMs, calendarDays = days)
    }

    private fun resolveCustom(startMs: Long, endMs: Long, tz: TimeZone): ResolvedDateRange {
        val resolvedStartMs = startOfDayFromTimestamp(startMs, tz)
        val resolvedEndMs = endOfDayFromTimestamp(endMs, tz)
        require(resolvedStartMs <= resolvedEndMs) {
            "Custom start ($startMs) must be <= end ($endMs)"
        }
        val days = calendarDaysInPeriod(resolvedStartMs, resolvedEndMs, tz)
        return ResolvedDateRange(
            startMs = resolvedStartMs,
            endMs = resolvedEndMs,
            calendarDays = days
        )
    }

    /** Set calendar to 00:00:00.000 and return millis. */
    private fun startOfDay(cal: Calendar): Long {
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    /** Set calendar to 23:59:59.999 and return millis. */
    private fun endOfDay(cal: Calendar): Long {
        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        cal.set(Calendar.MILLISECOND, 999)
        return cal.timeInMillis
    }

    private fun startOfDayFromTimestamp(ms: Long, tz: TimeZone): Long {
        val cal = Calendar.getInstance(tz).apply { timeInMillis = ms }
        return startOfDay(cal)
    }

    private fun endOfDayFromTimestamp(ms: Long, tz: TimeZone): Long {
        val cal = Calendar.getInstance(tz).apply { timeInMillis = ms }
        return endOfDay(cal)
    }
}

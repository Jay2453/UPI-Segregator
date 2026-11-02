package com.upisegregator.app.data.importer.csv

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Flexible date parser for varied bank statement date representations.
 */
object DateFormats {

    private val supportedPatterns = listOf(
        // Date with time
        "dd/MM/yyyy HH:mm:ss",
        "dd-MM-yyyy HH:mm:ss",
        "dd/MM/yyyy hh:mm:ss a",
        "dd-MM-yyyy hh:mm:ss a",
        "yyyy-MM-dd HH:mm:ss",
        "dd/MM/yyyy HH:mm",
        "dd-MM-yyyy HH:mm",

        // Date only
        "dd/MM/yyyy",
        "dd-MM-yyyy",
        "dd.MM.yyyy",
        "yyyy-MM-dd",
        "dd/MM/yy",
        "dd-MM-yy",
        "dd.MM.yy",

        // Month names
        "dd MMM yyyy",
        "dd-MMM-yyyy",
        "dd MMM, yyyy",
        "dd-MMM-yy",
        "dd MMM yy",
        "d MMM yyyy",
        "d-MMM-yyyy",
        "dd MMMM yyyy",
        "dd-MMMM-yyyy"
    )

    private val displayFormat = SimpleDateFormat("dd MMM yyyy", Locale.US).apply {
        timeZone = TimeZone.getDefault()
    }

    /**
     * Parses a date string into an epoch timestamp in milliseconds and a canonical display string.
     * Returns null if no format matches.
     */
    fun parseDate(rawDate: String): Pair<Long, String>? {
        val cleaned = rawDate.trim()
            .replace(Regex("\\s+"), " ")
            .replace("'", "")
            .trim()

        if (cleaned.isBlank()) return null

        for (pattern in supportedPatterns) {
            try {
                val parser = SimpleDateFormat(pattern, Locale.US).apply {
                    isLenient = false
                }
                val date = parser.parse(cleaned)
                if (date != null) {
                    val formatted = displayFormat.format(date)
                    return Pair(date.time, formatted)
                }
            } catch (_: Exception) {
                // Try next pattern
            }
        }

        return null
    }
}

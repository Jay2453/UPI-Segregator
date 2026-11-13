package com.upisegregator.app.domain.analytics

import java.util.Locale

/**
 * Normalises and formats category strings for analytics display.
 *
 * RULES:
 * - Stored category values are NEVER modified.
 * - Normalisation is done in memory for grouping and display only.
 * - Null, blank, or whitespace-only categories are mapped to "Other".
 * - Grouping key is `LOWER(TRIM(category))` (matching the SQL query).
 * - Display name uses title-case as a fallback when no better name is available.
 */
object CategoryNormalizer {

    private const val DEFAULT_CATEGORY = "Other"

    /**
     * Returns the grouping key for a category value.
     * Matches the SQL expression `LOWER(TRIM(category))`.
     */
    fun normalizeKey(category: String?): String {
        if (category.isNullOrBlank()) return DEFAULT_CATEGORY.lowercase(Locale.ROOT)
        return category.trim().lowercase(Locale.ROOT)
    }

    /**
     * Returns a human-readable display name for a category.
     *
     * - Null / blank → "Other"
     * - Already mixed-case or properly capitalised → preserved as-is after trimming.
     * - ALL CAPS or all lowercase → title-cased.
     */
    fun displayName(category: String?): String {
        if (category.isNullOrBlank()) return DEFAULT_CATEGORY
        val trimmed = category.trim()
        if (trimmed.isEmpty()) return DEFAULT_CATEGORY

        // If the category is already mixed-case (not all-upper or all-lower),
        // preserve it as the user/import intended.
        val isAllUpper = trimmed == trimmed.uppercase(Locale.ROOT)
        val isAllLower = trimmed == trimmed.lowercase(Locale.ROOT)

        return if (isAllUpper || isAllLower) {
            titleCase(trimmed)
        } else {
            trimmed
        }
    }

    /**
     * Picks the best display name from a group of raw category values
     * that all share the same normalised key.
     *
     * Preference: first non-blank mixed-case value, then first non-blank value,
     * then "Other".
     */
    fun bestDisplayName(rawValues: List<String?>): String {
        val nonBlank = rawValues.mapNotNull { it?.trim()?.takeIf { s -> s.isNotEmpty() } }
        if (nonBlank.isEmpty()) return DEFAULT_CATEGORY

        // Prefer a mixed-case value
        val mixedCase = nonBlank.firstOrNull { v ->
            v != v.uppercase(Locale.ROOT) && v != v.lowercase(Locale.ROOT)
        }
        if (mixedCase != null) return mixedCase

        // Fall back to title-casing the first non-blank value
        return titleCase(nonBlank.first())
    }

    /**
     * Title-cases a string: first letter of each word uppercase, rest lowercase.
     */
    private fun titleCase(input: String): String {
        return input.split("\\s+".toRegex()).joinToString(" ") { word ->
            word.lowercase(Locale.ROOT).replaceFirstChar { c ->
                if (c.isLetter()) c.titlecase(Locale.ROOT) else c.toString()
            }
        }
    }
}

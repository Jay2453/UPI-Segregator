package com.upisegregator.app.data.importer.csv

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Monetary precision parser for statement imports.
 *
 * Invariant: Never use Float or Double.
 * Converts decimal currency strings directly into integer paise (1 INR = 100 Paise).
 */
object AmountParser {

    /**
     * Parses a string containing rupees (e.g. "₹1,250.50", " 1500 ", "-45.00", "Rs. 99.99") into integer paise.
     * Returns null if unparseable, empty, or not a valid number.
     */
    fun parseToPaise(rawAmount: String?): Long? {
        if (rawAmount.isNullOrBlank()) return null

        val cleaned = rawAmount.trim()
            .replace("₹", "")
            .replace("INR", "", ignoreCase = true)
            .replace("Rs.", "", ignoreCase = true)
            .replace("Rs", "", ignoreCase = true)
            .replace(",", "")
            .replace("'", "")
            .trim()

        if (cleaned.isEmpty()) return null

        return try {
            val decimal = BigDecimal(cleaned)
            val absDecimal = decimal.abs()
            // Require reasonable scale (cents/paise up to 2 decimal places)
            val paiseDecimal = absDecimal.multiply(BigDecimal(100)).setScale(0, RoundingMode.HALF_UP)
            paiseDecimal.longValueExact()
        } catch (_: Exception) {
            null
        }
    }
}

package com.upisegregator.app.util

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale

/**
 * Monetary precision utilities for UPI Segregator.
 *
 * INVARIANT: All financial amounts in the database and core business logic
 * MUST be stored and computed as integer numbers of paise (1 INR = 100 Paise)
 * using [Long]. Never use Float or Double for storing or calculating balances.
 */
object PaiseUtils {

    private val indianLocale = Locale("en", "IN")

    /**
     * Result of validating a rupee amount string with BigDecimal precision.
     */
    sealed class AmountValidationResult {
        data class Valid(
            val amountPaise: Long,
            val formattedRupees: String,
            val decimal: BigDecimal
        ) : AmountValidationResult()

        data class Invalid(val reason: String) : AmountValidationResult()
    }

    /**
     * Comprehensive BigDecimal validation of rupee amounts.
     * Enforces:
     * - Non-blank, parseable decimal number
     * - Strictly positive (> 0)
     * - Decimal precision: at most 2 decimal places
     * - Currency code: strictly "INR" if currency is supplied
     */
    fun validateRupeeAmount(
        amountString: String?,
        currency: String? = "INR"
    ): AmountValidationResult {
        val trimmed = amountString?.trim()?.replace(",", "")?.replace("₹", "")?.trim()
        if (trimmed.isNullOrBlank()) {
            return AmountValidationResult.Invalid("Amount cannot be empty")
        }

        if (!currency.isNullOrBlank() && !currency.equals("INR", ignoreCase = true)) {
            return AmountValidationResult.Invalid("Unsupported currency '$currency'. Only INR payments are supported.")
        }

        val decimal = try {
            BigDecimal(trimmed)
        } catch (_: NumberFormatException) {
            return AmountValidationResult.Invalid("Invalid amount format: '$amountString'")
        }

        if (decimal <= BigDecimal.ZERO) {
            return AmountValidationResult.Invalid("Amount must be strictly positive")
        }

        // Check decimal precision: at most 2 decimal places
        val stripped = decimal.stripTrailingZeros()
        val effectiveScale = if (stripped.scale() < 0) 0 else stripped.scale()
        if (effectiveScale > 2) {
            return AmountValidationResult.Invalid("Amount cannot have more than 2 decimal places")
        }

        val paiseDecimal = decimal.multiply(BigDecimal(100)).setScale(0, RoundingMode.UNNECESSARY)
        val paise = try {
            paiseDecimal.longValueExact()
        } catch (_: ArithmeticException) {
            return AmountValidationResult.Invalid("Amount exceeds maximum supported limit")
        }

        val formattedRupees = paiseToRupeesString(paise)
        return AmountValidationResult.Valid(paise, formattedRupees, decimal)
    }

    /**
     * Converts a rupee string (e.g., "120", "120.50", " 1500.75 ") into integer paise.
     * Throws [IllegalArgumentException] if format is invalid or amount is negative.
     */
    fun rupeesToPaise(rupeesString: String): Long {
        val cleaned = rupeesString.trim().replace(",", "").replace("₹", "").trim()
        require(cleaned.isNotEmpty()) { "Amount cannot be empty" }

        val decimal = try {
            BigDecimal(cleaned)
        } catch (e: NumberFormatException) {
            throw IllegalArgumentException("Invalid amount format: $rupeesString", e)
        }

        require(decimal.scale() <= 2) { "Amount cannot have more than 2 decimal places (sub-paise not supported)" }
        require(decimal > BigDecimal.ZERO) { "Amount must be strictly positive" }

        val paiseDecimal = decimal.multiply(BigDecimal(100)).setScale(0, RoundingMode.UNNECESSARY)
        return paiseDecimal.longValueExact()
    }

    /**
     * Safely converts rupee string to paise, returning null if invalid or <= 0.
     */
    fun rupeesToPaiseOrNull(rupeesString: String?): Long? {
        return when (val v = validateRupeeAmount(rupeesString)) {
            is AmountValidationResult.Valid -> v.amountPaise
            is AmountValidationResult.Invalid -> null
        }
    }

    /**
     * Converts integer paise to a rupee decimal string (e.g. 12550 -> "125.50").
     */
    fun paiseToRupeesString(paise: Long): String {
        val isNegative = paise < 0
        val absPaise = kotlin.math.abs(paise)
        val rupees = absPaise / 100
        val remainingPaise = absPaise % 100
        val sign = if (isNegative) "-" else ""
        return String.format(Locale.US, "%s%d.%02d", sign, rupees, remainingPaise)
    }

    /**
     * Formats integer paise into Indian currency format (e.g. 1525000L -> "₹15,250.00").
     */
    fun formatPaiseToCurrency(paise: Long, showSymbol: Boolean = true): String {
        val isNegative = paise < 0
        val absPaise = kotlin.math.abs(paise)
        val rupees = absPaise / 100
        val remainingPaise = absPaise % 100

        val formatter = NumberFormat.getNumberInstance(indianLocale)
        val formattedRupees = formatter.format(rupees)
        val sign = if (isNegative) "-" else ""
        val symbol = if (showSymbol) "₹" else ""

        return String.format(Locale.US, "%s%s%s.%02d", sign, symbol, formattedRupees, remainingPaise)
    }

    /**
     * Validates that an amount in paise is strictly positive.
     */
    fun isValidAmount(amountPaise: Long): Boolean {
        return amountPaise > 0L
    }
}

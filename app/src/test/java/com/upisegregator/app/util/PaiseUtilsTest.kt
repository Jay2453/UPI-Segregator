package com.upisegregator.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests verifying financial calculations and monetary precision.
 */
class PaiseUtilsTest {

    @Test
    fun rupeesToPaise_standardValidAmounts_returnsCorrectPaise() {
        assertEquals(100L, PaiseUtils.rupeesToPaise("1"))
        assertEquals(100L, PaiseUtils.rupeesToPaise("1.00"))
        assertEquals(150L, PaiseUtils.rupeesToPaise("1.50"))
        assertEquals(150075L, PaiseUtils.rupeesToPaise("1500.75"))
        assertEquals(4000L, PaiseUtils.rupeesToPaise("40"))
        assertEquals(249900L, PaiseUtils.rupeesToPaise("2499.00"))
    }

    @Test
    fun rupeesToPaise_withCommasAndRupeeSymbol_parsesCorrectly() {
        assertEquals(1525000L, PaiseUtils.rupeesToPaise("₹15,250.00"))
        assertEquals(10000000L, PaiseUtils.rupeesToPaise("1,00,000.00"))
    }

    @Test
    fun rupeesToPaise_subPaise_throwsIllegalArgumentException() {
        // Sub-paise like 10.555 is not allowed in Indian financial systems
        assertThrows(IllegalArgumentException::class.java) {
            PaiseUtils.rupeesToPaise("10.555")
        }
    }

    @Test
    fun rupeesToPaise_zeroOrNegative_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException::class.java) {
            PaiseUtils.rupeesToPaise("0")
        }
        assertThrows(IllegalArgumentException::class.java) {
            PaiseUtils.rupeesToPaise("-50.00")
        }
    }

    @Test
    fun rupeesToPaiseOrNull_invalidInput_returnsNullSafely() {
        assertNull(PaiseUtils.rupeesToPaiseOrNull(""))
        assertNull(PaiseUtils.rupeesToPaiseOrNull("abc"))
        assertNull(PaiseUtils.rupeesToPaiseOrNull("-10"))
    }

    @Test
    fun paiseToRupeesString_formatsCorrectDecimals() {
        assertEquals("1.00", PaiseUtils.paiseToRupeesString(100L))
        assertEquals("1.50", PaiseUtils.paiseToRupeesString(150L))
        assertEquals("1500.75", PaiseUtils.paiseToRupeesString(150075L))
        assertEquals("0.05", PaiseUtils.paiseToRupeesString(5L))
    }

    @Test
    fun formatPaiseToCurrency_formatsIndianRupeeFormat() {
        assertEquals("₹15,250.00", PaiseUtils.formatPaiseToCurrency(1525000L))
        assertEquals("₹40.00", PaiseUtils.formatPaiseToCurrency(4000L))
        assertEquals("₹2,499.00", PaiseUtils.formatPaiseToCurrency(249900L))
    }

    @Test
    fun requiredAmounts_conversionAndFormatting_areExact() {
        // ₹1 = 100 paise -> "1.00"
        assertEquals(100L, PaiseUtils.rupeesToPaise("1"))
        assertEquals(100L, PaiseUtils.rupeesToPaise("1.00"))
        assertEquals("1.00", PaiseUtils.paiseToRupeesString(100L))
        assertEquals("₹1.00", PaiseUtils.formatPaiseToCurrency(100L))

        // ₹10 = 1000 paise -> "10.00"
        assertEquals(1000L, PaiseUtils.rupeesToPaise("10"))
        assertEquals(1000L, PaiseUtils.rupeesToPaise("10.00"))
        assertEquals("10.00", PaiseUtils.paiseToRupeesString(1000L))
        assertEquals("₹10.00", PaiseUtils.formatPaiseToCurrency(1000L))

        // ₹10.50 = 1050 paise -> "10.50"
        assertEquals(1050L, PaiseUtils.rupeesToPaise("10.50"))
        assertEquals("10.50", PaiseUtils.paiseToRupeesString(1050L))
        assertEquals("₹10.50", PaiseUtils.formatPaiseToCurrency(1050L))

        // ₹100 = 10000 paise -> "100.00"
        assertEquals(10000L, PaiseUtils.rupeesToPaise("100"))
        assertEquals(10000L, PaiseUtils.rupeesToPaise("100.00"))
        assertEquals("100.00", PaiseUtils.paiseToRupeesString(10000L))
        assertEquals("₹100.00", PaiseUtils.formatPaiseToCurrency(10000L))

        // ₹250 = 25000 paise -> "250.00"
        assertEquals(25000L, PaiseUtils.rupeesToPaise("250"))
        assertEquals(25000L, PaiseUtils.rupeesToPaise("250.00"))
        assertEquals("250.00", PaiseUtils.paiseToRupeesString(25000L))
        assertEquals("₹250.00", PaiseUtils.formatPaiseToCurrency(25000L))

        // ₹999 = 99900 paise -> "999.00"
        assertEquals(99900L, PaiseUtils.rupeesToPaise("999"))
        assertEquals(99900L, PaiseUtils.rupeesToPaise("999.00"))
        assertEquals("999.00", PaiseUtils.paiseToRupeesString(99900L))
        assertEquals("₹999.00", PaiseUtils.formatPaiseToCurrency(99900L))

        // ₹1,000 = 100000 paise -> "1000.00"
        assertEquals(100000L, PaiseUtils.rupeesToPaise("1000"))
        assertEquals(100000L, PaiseUtils.rupeesToPaise("1000.00"))
        assertEquals(100000L, PaiseUtils.rupeesToPaise("1,000"))
        assertEquals(100000L, PaiseUtils.rupeesToPaise("1,000.00"))
        assertEquals(100000L, PaiseUtils.rupeesToPaise("₹1,000.00"))
        assertEquals("1000.00", PaiseUtils.paiseToRupeesString(100000L))
        assertEquals("₹1,000.00", PaiseUtils.formatPaiseToCurrency(100000L))
    }

    @Test
    fun isValidAmount_checksStrictlyPositive() {
        assertTrue(PaiseUtils.isValidAmount(1L))
        assertTrue(PaiseUtils.isValidAmount(100000L))
        assertFalse(PaiseUtils.isValidAmount(0L))
        assertFalse(PaiseUtils.isValidAmount(-500L))
    }
}

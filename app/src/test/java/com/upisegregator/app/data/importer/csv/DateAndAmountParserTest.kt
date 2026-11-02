package com.upisegregator.app.data.importer.csv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class DateAndAmountParserTest {

    @Test
    fun parseDate_standardSlashFormat() {
        val parsed = DateFormats.parseDate("15/08/2026")
        assertNotNull(parsed)
        assertEquals("15 Aug 2026", parsed?.second)
    }

    @Test
    fun parseDate_hyphenFormat() {
        val parsed = DateFormats.parseDate("26-01-2026")
        assertNotNull(parsed)
        assertEquals("26 Jan 2026", parsed?.second)
    }

    @Test
    fun parseDate_isoFormat() {
        val parsed = DateFormats.parseDate("2026-10-02")
        assertNotNull(parsed)
        assertEquals("02 Oct 2026", parsed?.second)
    }

    @Test
    fun parseDate_withTimestamp() {
        val parsed = DateFormats.parseDate("12/05/2026 14:30:45")
        assertNotNull(parsed)
        assertEquals("12 May 2026", parsed?.second)
    }

    @Test
    fun parseDate_monthNameFormats() {
        val parsed1 = DateFormats.parseDate("05 Nov 2026")
        assertNotNull(parsed1)
        assertEquals("05 Nov 2026", parsed1?.second)

        val parsed2 = DateFormats.parseDate("15-Dec-2026")
        assertNotNull(parsed2)
        assertEquals("15 Dec 2026", parsed2?.second)
    }

    @Test
    fun parseDate_invalidReturnsNull() {
        assertNull(DateFormats.parseDate("invalid-date"))
        assertNull(DateFormats.parseDate(""))
        assertNull(DateFormats.parseDate("99/99/9999"))
    }

    @Test
    fun amountParser_standardNumbers() {
        assertEquals(10000L, AmountParser.parseToPaise("100"))
        assertEquals(2550L, AmountParser.parseToPaise("25.50"))
        assertEquals(9999900L, AmountParser.parseToPaise("99999"))
    }

    @Test
    fun amountParser_withCommasAndSymbols() {
        assertEquals(150050L, AmountParser.parseToPaise("₹1,500.50"))
        assertEquals(2500000L, AmountParser.parseToPaise("INR 25,000.00"))
        assertEquals(50000L, AmountParser.parseToPaise("Rs. 500.00"))
        assertEquals(50000L, AmountParser.parseToPaise("Rs 500"))
    }

    @Test
    fun amountParser_negativeValuesUseAbsPaise() {
        // AmountParser returns absolute paise; direction is handled by parser
        assertEquals(45000L, AmountParser.parseToPaise("-450.00"))
    }

    @Test
    fun amountParser_invalidReturnsNull() {
        assertNull(AmountParser.parseToPaise("abc"))
        assertNull(AmountParser.parseToPaise(""))
        assertNull(AmountParser.parseToPaise(null))
    }
}

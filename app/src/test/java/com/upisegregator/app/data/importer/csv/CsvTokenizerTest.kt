package com.upisegregator.app.data.importer.csv

import org.junit.Assert.assertEquals
import org.junit.Test

class CsvTokenizerTest {

    @Test
    fun parse_simpleCsv() {
        val csv = """
            Date,Narration,Debit,Credit
            01/01/2026,Coffee Shop,150.00,
            02/01/2026,Salary,,50000.00
        """.trimIndent()

        val rows = CsvTokenizer.parseString(csv)
        assertEquals(3, rows.size)
        assertEquals(listOf("Date", "Narration", "Debit", "Credit"), rows[0])
        assertEquals(listOf("01/01/2026", "Coffee Shop", "150.00", ""), rows[1])
        assertEquals(listOf("02/01/2026", "Salary", "", "50000.00"), rows[2])
    }

    @Test
    fun parse_quotedFieldsWithCommas() {
        val csv = """
            "Date","Narration","Debit","Credit"
            "05/02/2026","Swiggy, Indiranagar, Bangalore","1,250.50",""
        """.trimIndent()

        val rows = CsvTokenizer.parseString(csv)
        assertEquals(2, rows.size)
        assertEquals("Swiggy, Indiranagar, Bangalore", rows[1][1])
        assertEquals("1,250.50", rows[1][2])
    }

    @Test
    fun parse_escapedQuotes() {
        val csv = """
            "Date","Narration","Amount"
            "10/02/2026","Payment to ""Big Bazaar"" Retail","500.00"
        """.trimIndent()

        val rows = CsvTokenizer.parseString(csv)
        assertEquals(2, rows.size)
        assertEquals("Payment to \"Big Bazaar\" Retail", rows[1][1])
    }

    @Test
    fun parse_windowsLineEndings() {
        val csv = "Date,Description,Amount\r\n01/03/2026,Uber Ride,350.00\r\n02/03/2026,Metro,50.00\r\n"
        val rows = CsvTokenizer.parseString(csv)
        assertEquals(3, rows.size)
        assertEquals("Uber Ride", rows[1][1])
        assertEquals("Metro", rows[2][1])
    }

    @Test
    fun parse_emptyLinesIgnored() {
        val csv = """
            Date,Narration,Amount

            01/01/2026,Grocery,100.00

            02/01/2026,Snacks,50.00

        """.trimIndent()

        val rows = CsvTokenizer.parseString(csv)
        assertEquals(3, rows.size)
    }
}

package com.upisegregator.app.data.importer.csv

import com.upisegregator.app.data.importer.model.DuplicateStatus
import com.upisegregator.app.data.importer.model.RowValidationStatus
import com.upisegregator.app.data.importer.model.TransactionDirection
import com.upisegregator.app.data.local.entity.PaymentMethod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CsvStatementParserTest {

    @Test
    fun parse_hdfcBankFormat() {
        val csv = """
            Date,Narration,Chq/Ref No,Value Date,Withdrawal Amt.,Deposit Amt.,Closing Balance
            01/02/2026,UPI-SWIGGY-swiggy@axis-403212345678,403212345678,01/02/2026,450.00,,45200.00
            02/02/2026,NEFT CR-SALARY FOR JAN,NEFT12345678,02/02/2026,,75000.00,120200.00
            03/02/2026,POS 987654 SHELL PETROL PUMP,POS987654,03/02/2026,1200.00,,119000.00
        """.trimIndent()

        val result = CsvStatementParser.parseString(csv, "user_test")
        assertTrue(result.isSuccess)
        assertEquals(3, result.transactions.size)

        // Row 1: Swiggy UPI Debit
        val txn1 = result.transactions[0]
        assertEquals("01 Feb 2026", txn1.dateFormatted)
        assertEquals(45000L, txn1.amountPaise)
        assertEquals(TransactionDirection.DEBIT, txn1.direction)
        assertEquals("SWIGGY", txn1.recipientName)
        assertEquals("Food", txn1.category)
        assertEquals(PaymentMethod.UPI, txn1.paymentMethod)
        assertEquals("403212345678", txn1.externalReference)
        assertTrue(txn1.isSelected)

        // Row 2: Salary NEFT Credit
        val txn2 = result.transactions[1]
        assertEquals(7500000L, txn2.amountPaise)
        assertEquals(TransactionDirection.CREDIT, txn2.direction)
        assertFalse("Credits should not be auto-selected as expenses", txn2.isSelected)

        // Row 3: Petrol POS Card Debit
        val txn3 = result.transactions[2]
        assertEquals(120000L, txn3.amountPaise)
        assertEquals(TransactionDirection.DEBIT, txn3.direction)
        assertEquals("Transport", txn3.category)
        assertEquals(PaymentMethod.CARD, txn3.paymentMethod)
        assertTrue(txn3.isSelected)
    }

    @Test
    fun parse_sbiBankFormatWithMetadataHeader() {
        val csv = """
            Account Statement for 00000012345678
            Branch: Bangalore Main
            Generated on: 10/02/2026
            
            Txn Date,Description,Ref No./Cheque No.,Debit,Credit,Balance
            10/01/2026,UPI/DR/401234567890/Zomato/HDFC/Order,401234567890,320.00,,15000.00
            11/01/2026,INTEREST CREDIT,,,45.00,15045.00
        """.trimIndent()

        val result = CsvStatementParser.parseString(csv, "user_test")
        assertTrue("Parser should locate the table header even after metadata rows", result.isSuccess)
        assertEquals(2, result.transactions.size)

        val txn1 = result.transactions[0]
        assertEquals("Zomato", txn1.recipientName)
        assertEquals(32000L, txn1.amountPaise)
        assertEquals(TransactionDirection.DEBIT, txn1.direction)
    }

    @Test
    fun parse_singleAmountColumnWithIndicator() {
        val csv = """
            Transaction Date,Particulars,Amount,Type
            15/02/2026,Netflix Subscription,649.00,DR
            16/02/2026,Cashback Received,50.00,CR
        """.trimIndent()

        val result = CsvStatementParser.parseString(csv, "user_test")
        assertTrue(result.isSuccess)
        assertEquals(2, result.transactions.size)

        assertEquals(TransactionDirection.DEBIT, result.transactions[0].direction)
        assertEquals(64900L, result.transactions[0].amountPaise)
        assertEquals("Entertainment", result.transactions[0].category)

        assertEquals(TransactionDirection.CREDIT, result.transactions[1].direction)
        assertEquals(5000L, result.transactions[1].amountPaise)
    }

    @Test
    fun parse_signedAmountColumn() {
        val csv = """
            Date,Description,Amount
            20/02/2026,Amazon India Shopping,-1599.00
            21/02/2026,Refund from Merchant,1599.00
        """.trimIndent()

        val result = CsvStatementParser.parseString(csv, "user_test")
        assertTrue(result.isSuccess)
        assertEquals(2, result.transactions.size)

        assertEquals(TransactionDirection.DEBIT, result.transactions[0].direction)
        assertEquals(159900L, result.transactions[0].amountPaise)
        assertEquals("Shopping", result.transactions[0].category)

        assertEquals(TransactionDirection.CREDIT, result.transactions[1].direction)
    }

    @Test
    fun parse_inFileDuplicateDetection() {
        val csv = """
            Date,Narration,Ref No,Debit,Credit
            01/01/2026,UPI-Uber-123456789012,123456789012,250.00,
            02/01/2026,Grocery Store,,500.00,
            01/01/2026,UPI-Uber-123456789012,123456789012,250.00,
        """.trimIndent()

        val result = CsvStatementParser.parseString(csv, "user_test")
        assertTrue(result.isSuccess)
        assertEquals(3, result.transactions.size)

        // Row 1: First occurrence is clean
        assertEquals(DuplicateStatus.NONE, result.transactions[0].duplicateStatus)
        assertTrue(result.transactions[0].isSelected)

        // Row 2: Distinct row
        assertEquals(DuplicateStatus.NONE, result.transactions[1].duplicateStatus)

        // Row 3: Duplicate within the same file!
        assertEquals(DuplicateStatus.DUPLICATE_IN_FILE, result.transactions[2].duplicateStatus)
        assertFalse("In-file duplicate must be deselected by default", result.transactions[2].isSelected)
        assertTrue(result.transactions[2].duplicateReason?.contains("within this statement") == true)
    }

    @Test
    fun parse_invalidRowHandledGracefully() {
        val csv = """
            Date,Description,Debit,Credit
            not-a-date,Some Merchant,100.00,
            05/01/2026,Zero Amount,0.00,
            06/01/2026,Valid Row,200.00,
        """.trimIndent()

        val result = CsvStatementParser.parseString(csv, "user_test")
        assertTrue(result.isSuccess)
        assertEquals(3, result.transactions.size)

        assertEquals(RowValidationStatus.INVALID, result.transactions[0].validationStatus)
        assertFalse(result.transactions[0].isSelected)

        assertEquals(RowValidationStatus.INVALID, result.transactions[1].validationStatus)
        assertFalse(result.transactions[1].isSelected)

        assertEquals(RowValidationStatus.VALID, result.transactions[2].validationStatus)
        assertTrue(result.transactions[2].isSelected)
    }

    @Test
    fun parse_unrecognizedHeaders_returnsClearError() {
        val csv = """
            Col1,Col2,Col3,Col4
            val1,val2,val3,val4
        """.trimIndent()

        val result = CsvStatementParser.parseString(csv, "user_test")
        assertFalse(result.isSuccess)
        assertNotNull(result.errorMessage)
        assertTrue(result.errorMessage?.contains("Could not identify standard bank statement") == true)
    }
}

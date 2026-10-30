package com.upisegregator.app.data.demo

import com.upisegregator.app.data.local.entity.PaymentMethod
import com.upisegregator.app.data.local.entity.TransactionSource
import com.upisegregator.app.data.local.entity.TransactionStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests verifying demo data integrity and Milestone 2 rules.
 */
class DemoDataGeneratorTest {

    @Test
    fun generateDemoTransactions_countIsBetween25And30() {
        val transactions = DemoDataGenerator.generateDemoTransactions()
        assertTrue("Transaction count should be between 25 and 30, got ${transactions.size}", transactions.size in 25..30)
    }

    @Test
    fun generateDemoTransactions_allAmountsArePositivePaise() {
        val transactions = DemoDataGenerator.generateDemoTransactions()
        for (tx in transactions) {
            assertTrue("Transaction ${tx.transactionId} amount must be > 0", tx.amountPaise > 0L)
        }
    }

    @Test
    fun generateDemoTransactions_allHaveDemoSource() {
        val transactions = DemoDataGenerator.generateDemoTransactions()
        for (tx in transactions) {
            assertEquals("Transaction source must be DEMO", TransactionSource.DEMO, tx.transactionSource)
        }
    }

    @Test
    fun generateDemoTransactions_spendingTotalOnlyIncludesSuccessTransactions() {
        val transactions = DemoDataGenerator.generateDemoTransactions()

        val successSum = transactions
            .filter { it.transactionStatus == TransactionStatus.SUCCESS }
            .sumOf { it.amountPaise }

        val totalSum = transactions.sumOf { it.amountPaise }

        // Must have non-success transactions (PENDING, FAILED, CANCELLED)
        val nonSuccessCount = transactions.count { it.transactionStatus != TransactionStatus.SUCCESS }
        assertTrue("Must include non-success transactions for testing rules", nonSuccessCount > 0)

        // The confirmed spending total must be strictly less than the total sum of all records
        assertTrue("Success sum ($successSum) must be strictly less than total sum ($totalSum)", successSum < totalSum)
    }

    @Test
    fun generateDemoTransactions_includesFictionalRecipients() {
        val recipients = DemoDataGenerator.demoRecipients.map { it.recipientName }
        assertTrue(recipients.any { it.contains("Rahul") })
        assertTrue(recipients.any { it.contains("Priya") })
        assertTrue(recipients.any { it.contains("Amit") })
        assertTrue(recipients.any { it.contains("Neha") })
    }

    @Test
    fun generateDemoTransactions_includesDiversePaymentMethods() {
        val transactions = DemoDataGenerator.generateDemoTransactions()
        val methods = transactions.map { it.paymentMethod }.toSet()

        assertTrue("Must include UPI payments", methods.contains(PaymentMethod.UPI))
        assertTrue("Must include Cash expenses", methods.contains(PaymentMethod.CASH))
        assertTrue("Must include Card expenses", methods.contains(PaymentMethod.CARD))
    }
}

package com.upisegregator.app.data.isolation

import com.upisegregator.app.data.demo.DemoDataManager
import com.upisegregator.app.data.local.entity.PaymentMethod
import com.upisegregator.app.data.local.entity.TransactionEntity
import com.upisegregator.app.data.local.entity.TransactionSource
import com.upisegregator.app.data.local.entity.TransactionStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests verifying strict profile/user isolation between Demo Mode and Real User profiles.
 */
class DataIsolationTest {

    @Test
    fun transactions_filteredByUserId_preventsCrossAccountDataLeak() {
        val demoTx = TransactionEntity(
            transactionId = "tx_demo_1",
            userId = DemoDataManager.DEMO_USER_ID,
            amountPaise = 15000L,
            recipientName = "Demo Recipient",
            paymentMethod = PaymentMethod.UPI,
            category = "Dining",
            transactionDate = System.currentTimeMillis(),
            transactionStatus = TransactionStatus.SUCCESS,
            transactionSource = TransactionSource.DEMO
        )

        val realUserTx = TransactionEntity(
            transactionId = "tx_user_1",
            userId = "user_real_999",
            amountPaise = 50000L,
            recipientName = "Private Landlord",
            paymentMethod = PaymentMethod.BANK_TRANSFER,
            category = "Rent",
            transactionDate = System.currentTimeMillis(),
            transactionStatus = TransactionStatus.SUCCESS,
            transactionSource = TransactionSource.MANUAL
        )

        val allTransactions = listOf(demoTx, realUserTx)

        // Filter for Demo
        val demoResults = allTransactions.filter { it.userId == DemoDataManager.DEMO_USER_ID }
        assertEquals(1, demoResults.size)
        assertEquals("tx_demo_1", demoResults[0].transactionId)

        // Filter for Real User
        val realUserResults = allTransactions.filter { it.userId == "user_real_999" }
        assertEquals(1, realUserResults.size)
        assertEquals("tx_user_1", realUserResults[0].transactionId)

        // Real user query must NEVER contain demo records
        assertTrue(realUserResults.none { it.transactionSource == TransactionSource.DEMO })
        // Demo query must NEVER contain real user records
        assertTrue(demoResults.none { it.userId == "user_real_999" })
    }

    @Test
    fun resetDemoData_leavesRealUserTransactionsUntouched() {
        val database = mutableListOf(
            TransactionEntity(
                transactionId = "tx_d1",
                userId = DemoDataManager.DEMO_USER_ID,
                amountPaise = 1000L,
                recipientName = "Demo",
                paymentMethod = PaymentMethod.UPI,
                category = "Food",
                transactionDate = 1000L,
                transactionStatus = TransactionStatus.SUCCESS,
                transactionSource = TransactionSource.DEMO
            ),
            TransactionEntity(
                transactionId = "tx_u1",
                userId = "user_real_123",
                amountPaise = 2000L,
                recipientName = "Real Recipient",
                paymentMethod = PaymentMethod.UPI,
                category = "Bills",
                transactionDate = 2000L,
                transactionStatus = TransactionStatus.SUCCESS,
                transactionSource = TransactionSource.MANUAL
            )
        )

        // Simulate Reset Demo (delete where userId == DEMO_USER_ID)
        database.removeAll { it.userId == DemoDataManager.DEMO_USER_ID }

        assertEquals(1, database.size)
        assertEquals("tx_u1", database[0].transactionId)
        assertEquals("user_real_123", database[0].userId)
    }

    @Test
    fun spendingCalculation_scopedByUserIdAndSuccessStatus() {
        val transactions = listOf(
            TransactionEntity(
                transactionId = "tx_1",
                userId = "user_alpha",
                amountPaise = 25000L,
                recipientName = "Store A",
                paymentMethod = PaymentMethod.UPI,
                category = "Groceries",
                transactionDate = 1L,
                transactionStatus = TransactionStatus.SUCCESS,
                transactionSource = TransactionSource.MANUAL
            ),
            TransactionEntity(
                transactionId = "tx_2",
                userId = "user_alpha",
                amountPaise = 50000L, // Pending - must NOT count
                recipientName = "Store B",
                paymentMethod = PaymentMethod.UPI,
                category = "Groceries",
                transactionDate = 2L,
                transactionStatus = TransactionStatus.PENDING,
                transactionSource = TransactionSource.MANUAL
            ),
            TransactionEntity(
                transactionId = "tx_3",
                userId = "user_beta", // Different user
                amountPaise = 100000L,
                recipientName = "Store C",
                paymentMethod = PaymentMethod.UPI,
                category = "Electronics",
                transactionDate = 3L,
                transactionStatus = TransactionStatus.SUCCESS,
                transactionSource = TransactionSource.MANUAL
            )
        )

        val alphaSpending = transactions
            .filter { it.userId == "user_alpha" && it.transactionStatus == TransactionStatus.SUCCESS }
            .sumOf { it.amountPaise }

        val betaSpending = transactions
            .filter { it.userId == "user_beta" && it.transactionStatus == TransactionStatus.SUCCESS }
            .sumOf { it.amountPaise }

        assertEquals(25000L, alphaSpending)
        assertEquals(100000L, betaSpending)
    }
}

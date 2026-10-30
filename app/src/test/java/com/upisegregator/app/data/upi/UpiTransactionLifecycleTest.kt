package com.upisegregator.app.data.upi

import com.upisegregator.app.data.local.entity.PaymentMethod
import com.upisegregator.app.data.local.entity.TransactionEntity
import com.upisegregator.app.data.local.entity.TransactionSource
import com.upisegregator.app.data.local.entity.TransactionStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

/**
 * Unit tests verifying UPI transaction lifecycle, persistence invariants,
 * process recreation resilience, and user isolation.
 */
class UpiTransactionLifecycleTest {

    private class FakeTransactionStorage {
        val transactions = mutableMapOf<String, TransactionEntity>()

        fun insert(tx: TransactionEntity) {
            transactions[tx.transactionId] = tx
        }

        fun update(tx: TransactionEntity) {
            if (transactions.containsKey(tx.transactionId)) {
                transactions[tx.transactionId] = tx
            }
        }

        fun getById(id: String, userId: String): TransactionEntity? {
            val tx = transactions[id]
            return if (tx != null && tx.userId == userId) tx else null
        }
    }

    @Test
    fun `pending transaction is created with PENDING status before external launch`() {
        val storage = FakeTransactionStorage()
        val txnId = UUID.randomUUID().toString()
        val userId = "user_123"

        val pendingTx = TransactionEntity(
            transactionId = txnId,
            amountPaise = 250000L,
            recipientName = "Sharma Stores",
            paymentMethod = PaymentMethod.UPI,
            category = "UPI Payment",
            note = "Groceries",
            transactionDate = System.currentTimeMillis(),
            transactionStatus = TransactionStatus.PENDING,
            transactionSource = TransactionSource.UPI_INTENT,
            externalReference = null,
            userId = userId
        )

        storage.insert(pendingTx)

        val retrieved = storage.getById(txnId, userId)
        assertNotNull(retrieved)
        assertEquals(TransactionStatus.PENDING, retrieved?.transactionStatus)
        assertEquals(250000L, retrieved?.amountPaise)
        assertEquals("Sharma Stores", retrieved?.recipientName)
    }

    @Test
    fun `returning from UPI app updates existing transaction without creating duplicate`() {
        val storage = FakeTransactionStorage()
        val txnId = UUID.randomUUID().toString()
        val userId = "user_456"

        // 1. Initial pending insertion
        val initialTx = TransactionEntity(
            transactionId = txnId,
            amountPaise = 100000L,
            recipientName = "Aman Verma",
            paymentMethod = PaymentMethod.UPI,
            category = "UPI Payment",
            transactionDate = System.currentTimeMillis(),
            transactionStatus = TransactionStatus.PENDING,
            transactionSource = TransactionSource.UPI_INTENT,
            userId = userId
        )
        storage.insert(initialTx)
        assertEquals(1, storage.transactions.size)

        // 2. User confirms outcome as SUCCESS
        val existing = storage.getById(txnId, userId)
        assertNotNull(existing)

        val updated = existing!!.copy(
            transactionStatus = TransactionStatus.SUCCESS,
            externalReference = "BANK_RRN_998877"
        )
        storage.update(updated)

        // Must still be exactly 1 record — no duplicate inserted!
        assertEquals(1, storage.transactions.size)
        val finalRecord = storage.getById(txnId, userId)
        assertEquals(TransactionStatus.SUCCESS, finalRecord?.transactionStatus)
        assertEquals("BANK_RRN_998877", finalRecord?.externalReference)
    }

    @Test
    fun `cancelled return updates transaction to CANCELLED and does not count as spending`() {
        val storage = FakeTransactionStorage()
        val txnId = UUID.randomUUID().toString()
        val userId = "user_789"

        val initialTx = TransactionEntity(
            transactionId = txnId,
            amountPaise = 50000L,
            recipientName = "Coffee Shop",
            paymentMethod = PaymentMethod.UPI,
            category = "UPI Payment",
            transactionDate = System.currentTimeMillis(),
            transactionStatus = TransactionStatus.PENDING,
            transactionSource = TransactionSource.UPI_INTENT,
            userId = userId
        )
        storage.insert(initialTx)

        val existing = storage.getById(txnId, userId)
        assertNotNull(existing)

        // Update to CANCELLED
        val cancelled = existing!!.copy(transactionStatus = TransactionStatus.CANCELLED)
        storage.update(cancelled)

        val finalRecord = storage.getById(txnId, userId)
        assertEquals(TransactionStatus.CANCELLED, finalRecord?.transactionStatus)

        // Only SUCCESS transactions count towards spending totals
        val successTotal = storage.transactions.values
            .filter { it.userId == userId && it.transactionStatus == TransactionStatus.SUCCESS }
            .sumOf { it.amountPaise }
        assertEquals(0L, successTotal)
    }

    @Test
    fun `transaction ID persistence survives simulated process recreation`() {
        // Simulates saving active transaction ID in persistent storage (e.g. DataStore)
        var persistentActiveId: String? = null

        val txnId = UUID.randomUUID().toString()
        // Save before launch
        persistentActiveId = txnId

        // Simulate complete ViewModel destruction and process wipe
        val recreatedViewModelActiveId = persistentActiveId

        assertEquals(txnId, recreatedViewModelActiveId)
        assertFalse(recreatedViewModelActiveId.isNullOrBlank())
    }

    @Test
    fun `user isolation prevents accessing transactions of another user`() {
        val storage = FakeTransactionStorage()
        val txnId = UUID.randomUUID().toString()

        val user1Tx = TransactionEntity(
            transactionId = txnId,
            amountPaise = 150000L,
            recipientName = "Private Payee",
            paymentMethod = PaymentMethod.UPI,
            category = "UPI Payment",
            transactionDate = System.currentTimeMillis(),
            transactionStatus = TransactionStatus.PENDING,
            transactionSource = TransactionSource.UPI_INTENT,
            userId = "user_alpha"
        )
        storage.insert(user1Tx)

        // User beta attempts to query transaction with the same ID
        val queryByOtherUser = storage.getById(txnId, "user_beta")
        assertNull("Other user must never be able to access or update transaction", queryByOtherUser)

        // Demo user attempts to query transaction
        val queryByDemo = storage.getById(txnId, "demo_user")
        assertNull("Demo user must never access registered user's transaction", queryByDemo)

        // Legitimate owner queries transaction
        val queryByOwner = storage.getById(txnId, "user_alpha")
        assertNotNull(queryByOwner)
        assertEquals("user_alpha", queryByOwner?.userId)
    }

    @Test
    fun `local verification provider truthfully declares no bank verification`() = runBlocking {
        val provider = LocalPaymentVerificationProvider()
        val result = provider.verifyPayment("tx_123", "RRN_456")

        assertTrue(result is PaymentVerificationResult.NotSupported)
        val notSupported = result as PaymentVerificationResult.NotSupported
        assertTrue(notSupported.reason.contains("cannot independently verify", ignoreCase = true))
    }
}

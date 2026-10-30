package com.upisegregator.app.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.upisegregator.app.data.local.dao.CategoryDao
import com.upisegregator.app.data.local.dao.RecipientDao
import com.upisegregator.app.data.local.dao.TransactionDao
import com.upisegregator.app.data.local.database.AppDatabase
import com.upisegregator.app.data.local.entity.CategoryEntity
import com.upisegregator.app.data.local.entity.PaymentMethod
import com.upisegregator.app.data.local.entity.RecipientEntity
import com.upisegregator.app.data.local.entity.TransactionEntity
import com.upisegregator.app.data.local.entity.TransactionSource
import com.upisegregator.app.data.local.entity.TransactionStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class RoomDatabaseTest {

    private lateinit var database: AppDatabase
    private lateinit var transactionDao: TransactionDao
    private lateinit var recipientDao: RecipientDao
    private lateinit var categoryDao: CategoryDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        transactionDao = database.transactionDao()
        recipientDao = database.recipientDao()
        categoryDao = database.categoryDao()
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        database.close()
    }

    @Test
    fun insertAndRetrieveRecipient() = runBlocking {
        val recipient = RecipientEntity(
            recipientId = "rec_test_1",
            recipientName = "Rahul Sharma",
            upiId = "rahul@upi",
            phoneNumber = "+919876543210"
        )
        recipientDao.insertRecipient(recipient)

        val retrieved = recipientDao.getRecipientById("rec_test_1")
        assertNotNull(retrieved)
        assertEquals("Rahul Sharma", retrieved?.recipientName)
        assertEquals("rahul@upi", retrieved?.upiId)
    }

    @Test
    fun insertAndRetrieveTransactionInPaise() = runBlocking {
        val tx = TransactionEntity(
            transactionId = "tx_001",
            amountPaise = 25050L, // ₹250.50
            recipientName = "Chai Stall",
            paymentMethod = PaymentMethod.UPI,
            category = "Food & Dining",
            note = "Chai and snacks",
            transactionDate = System.currentTimeMillis(),
            transactionStatus = TransactionStatus.SUCCESS,
            transactionSource = TransactionSource.MANUAL
        )
        transactionDao.insertTransaction(tx)

        val retrieved = transactionDao.getTransactionById("tx_001")
        assertNotNull(retrieved)
        assertEquals(25050L, retrieved?.amountPaise)
        assertEquals(TransactionStatus.SUCCESS, retrieved?.transactionStatus)
        assertEquals(TransactionSource.MANUAL, retrieved?.transactionSource)
    }

    @Test
    fun updateTransaction() = runBlocking {
        val tx = TransactionEntity(
            transactionId = "tx_upd",
            amountPaise = 10000L,
            recipientName = "Priya",
            paymentMethod = PaymentMethod.UPI,
            category = "General",
            transactionDate = System.currentTimeMillis(),
            transactionStatus = TransactionStatus.PENDING,
            transactionSource = TransactionSource.UPI_INTENT
        )
        transactionDao.insertTransaction(tx)

        val updated = tx.copy(
            transactionStatus = TransactionStatus.SUCCESS,
            note = "Confirmed by user"
        )
        transactionDao.updateTransaction(updated)

        val retrieved = transactionDao.getTransactionById("tx_upd")
        assertEquals(TransactionStatus.SUCCESS, retrieved?.transactionStatus)
        assertEquals("Confirmed by user", retrieved?.note)
    }

    @Test
    fun deleteTransaction() = runBlocking {
        val tx = TransactionEntity(
            transactionId = "tx_del",
            amountPaise = 5000L,
            recipientName = "Amit",
            paymentMethod = PaymentMethod.CASH,
            category = "Snacks",
            transactionDate = System.currentTimeMillis(),
            transactionStatus = TransactionStatus.SUCCESS,
            transactionSource = TransactionSource.MANUAL
        )
        transactionDao.insertTransaction(tx)
        transactionDao.deleteTransactionById("tx_del")

        val retrieved = transactionDao.getTransactionById("tx_del")
        assertNull(retrieved)
    }

    @Test
    fun onlySuccessTransactionsContributeToTotalSpending() = runBlocking {
        val txSuccess1 = TransactionEntity(
            transactionId = "tx_s1",
            amountPaise = 10000L, // ₹100
            recipientName = "Store A",
            paymentMethod = PaymentMethod.UPI,
            category = "Shopping",
            transactionDate = System.currentTimeMillis(),
            transactionStatus = TransactionStatus.SUCCESS,
            transactionSource = TransactionSource.DEMO
        )
        val txSuccess2 = TransactionEntity(
            transactionId = "tx_s2",
            amountPaise = 25000L, // ₹250
            recipientName = "Store B",
            paymentMethod = PaymentMethod.UPI,
            category = "Shopping",
            transactionDate = System.currentTimeMillis(),
            transactionStatus = TransactionStatus.SUCCESS,
            transactionSource = TransactionSource.DEMO
        )
        val txPending = TransactionEntity(
            transactionId = "tx_pending",
            amountPaise = 50000L, // ₹500 (Must NOT count!)
            recipientName = "Store C",
            paymentMethod = PaymentMethod.UPI,
            category = "Shopping",
            transactionDate = System.currentTimeMillis(),
            transactionStatus = TransactionStatus.PENDING,
            transactionSource = TransactionSource.DEMO
        )
        val txFailed = TransactionEntity(
            transactionId = "tx_failed",
            amountPaise = 90000L, // ₹900 (Must NOT count!)
            recipientName = "Store D",
            paymentMethod = PaymentMethod.UPI,
            category = "Shopping",
            transactionDate = System.currentTimeMillis(),
            transactionStatus = TransactionStatus.FAILED,
            transactionSource = TransactionSource.DEMO
        )

        transactionDao.insertTransactions(listOf(txSuccess1, txSuccess2, txPending, txFailed))

        val totalSpending = transactionDao.getTotalSpendingFlow().first()
        // Only s1 + s2 = 10000 + 25000 = 35000 paise (₹350)
        assertEquals(35000L, totalSpending)
    }

    @Test
    fun deleteTransactionsBySource_leavesOtherSourcesIntact() = runBlocking {
        val demoTx = TransactionEntity(
            transactionId = "tx_demo",
            amountPaise = 1000L,
            recipientName = "Demo Recipient",
            paymentMethod = PaymentMethod.UPI,
            category = "Test",
            transactionDate = System.currentTimeMillis(),
            transactionStatus = TransactionStatus.SUCCESS,
            transactionSource = TransactionSource.DEMO
        )
        val userTx = TransactionEntity(
            transactionId = "tx_user",
            amountPaise = 2000L,
            recipientName = "Real Recipient",
            paymentMethod = PaymentMethod.UPI,
            category = "Test",
            transactionDate = System.currentTimeMillis(),
            transactionStatus = TransactionStatus.SUCCESS,
            transactionSource = TransactionSource.MANUAL
        )

        transactionDao.insertTransactions(listOf(demoTx, userTx))
        transactionDao.deleteTransactionsBySource(TransactionSource.DEMO)

        val remaining = transactionDao.getAllTransactionsFlow().first()
        assertEquals(1, remaining.size)
        assertEquals("tx_user", remaining[0].transactionId)
    }
}

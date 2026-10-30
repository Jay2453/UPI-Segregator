package com.upisegregator.app.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.upisegregator.app.data.local.dao.CategoryDao
import com.upisegregator.app.data.local.dao.RecipientDao
import com.upisegregator.app.data.local.dao.TransactionDao
import com.upisegregator.app.data.local.dao.UserDao
import com.upisegregator.app.data.local.database.AppDatabase
import com.upisegregator.app.data.local.entity.CategoryEntity
import com.upisegregator.app.data.local.entity.PaymentMethod
import com.upisegregator.app.data.local.entity.RecipientEntity
import com.upisegregator.app.data.local.entity.TransactionEntity
import com.upisegregator.app.data.local.entity.TransactionSource
import com.upisegregator.app.data.local.entity.TransactionStatus
import com.upisegregator.app.data.local.entity.UserEntity
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
    private lateinit var userDao: UserDao
    private lateinit var transactionDao: TransactionDao
    private lateinit var recipientDao: RecipientDao
    private lateinit var categoryDao: CategoryDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        userDao = database.userDao()
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
    fun insertAndRetrieveUser() = runBlocking {
        val user = UserEntity(
            userId = "usr_001",
            fullName = "Dinesh Patel",
            email = "dinesh@example.com",
            passwordHash = "hash123456",
            passwordSalt = "salt123456"
        )
        userDao.insertUser(user)

        val retrieved = userDao.getUserByEmail("dinesh@example.com")
        assertNotNull(retrieved)
        assertEquals("Dinesh Patel", retrieved?.fullName)
        assertEquals("dinesh@example.com", retrieved?.email)
    }

    @Test
    fun insertAndRetrieveRecipient() = runBlocking {
        val recipient = RecipientEntity(
            recipientId = "rec_test_1",
            userId = "user_abc",
            recipientName = "Rahul Sharma",
            upiId = "rahul@upi",
            phoneNumber = "+919876543210"
        )
        recipientDao.insertRecipient(recipient)

        val retrieved = recipientDao.getRecipientById("rec_test_1", "user_abc")
        assertNotNull(retrieved)
        assertEquals("Rahul Sharma", retrieved?.recipientName)
        assertEquals("rahul@upi", retrieved?.upiId)

        // Querying with different userId must return null (isolation)
        val wrongUserRetrieved = recipientDao.getRecipientById("rec_test_1", "other_user")
        assertNull(wrongUserRetrieved)
    }

    @Test
    fun insertAndRetrieveTransactionInPaise() = runBlocking {
        val tx = TransactionEntity(
            transactionId = "tx_001",
            userId = "user_test",
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

        val retrieved = transactionDao.getTransactionById("tx_001", "user_test")
        assertNotNull(retrieved)
        assertEquals(25050L, retrieved?.amountPaise)
        assertEquals(TransactionStatus.SUCCESS, retrieved?.transactionStatus)
        assertEquals(TransactionSource.MANUAL, retrieved?.transactionSource)
    }

    @Test
    fun transactions_scopedToUser_guaranteesDemoAndUserIsolation() = runBlocking {
        val demoTx = TransactionEntity(
            transactionId = "demo_1",
            userId = "demo_user",
            amountPaise = 10000L,
            recipientName = "Demo Recipient",
            paymentMethod = PaymentMethod.UPI,
            category = "Demo",
            transactionDate = System.currentTimeMillis(),
            transactionStatus = TransactionStatus.SUCCESS,
            transactionSource = TransactionSource.DEMO
        )
        val realTx = TransactionEntity(
            transactionId = "real_1",
            userId = "user_dinesh",
            amountPaise = 95000L,
            recipientName = "Real Landlord",
            paymentMethod = PaymentMethod.BANK_TRANSFER,
            category = "Rent",
            transactionDate = System.currentTimeMillis(),
            transactionStatus = TransactionStatus.SUCCESS,
            transactionSource = TransactionSource.MANUAL
        )

        transactionDao.insertTransactions(listOf(demoTx, realTx))

        val demoList = transactionDao.getTransactionsByUserFlow("demo_user").first()
        val realList = transactionDao.getTransactionsByUserFlow("user_dinesh").first()

        assertEquals(1, demoList.size)
        assertEquals("demo_1", demoList[0].transactionId)

        assertEquals(1, realList.size)
        assertEquals("real_1", realList[0].transactionId)
    }

    @Test
    fun deleteTransactionsByUser_deletesOnlyTargetUserRecords() = runBlocking {
        val demoTx = TransactionEntity(
            transactionId = "tx_d",
            userId = "demo_user",
            amountPaise = 5000L,
            recipientName = "Demo",
            paymentMethod = PaymentMethod.UPI,
            category = "Test",
            transactionDate = System.currentTimeMillis(),
            transactionStatus = TransactionStatus.SUCCESS,
            transactionSource = TransactionSource.DEMO
        )
        val userTx = TransactionEntity(
            transactionId = "tx_u",
            userId = "user_real",
            amountPaise = 15000L,
            recipientName = "User Real",
            paymentMethod = PaymentMethod.UPI,
            category = "Test",
            transactionDate = System.currentTimeMillis(),
            transactionStatus = TransactionStatus.SUCCESS,
            transactionSource = TransactionSource.MANUAL
        )

        transactionDao.insertTransactions(listOf(demoTx, userTx))

        // Reset demo by deleting demo_user records
        transactionDao.deleteTransactionsByUser("demo_user")

        val remainingReal = transactionDao.getTransactionsByUserFlow("user_real").first()
        assertEquals(1, remainingReal.size)
        assertEquals("tx_u", remainingReal[0].transactionId)

        val remainingDemo = transactionDao.getTransactionsByUserFlow("demo_user").first()
        assertEquals(0, remainingDemo.size)
    }

    @Test
    fun onlySuccessTransactionsContributeToTotalSpending() = runBlocking {
        val txSuccess = TransactionEntity(
            transactionId = "tx_s1",
            userId = "user_spend",
            amountPaise = 15000L,
            recipientName = "Store A",
            paymentMethod = PaymentMethod.UPI,
            category = "Shopping",
            transactionDate = System.currentTimeMillis(),
            transactionStatus = TransactionStatus.SUCCESS,
            transactionSource = TransactionSource.MANUAL
        )
        val txPending = TransactionEntity(
            transactionId = "tx_p1",
            userId = "user_spend",
            amountPaise = 30000L, // Must NOT count
            recipientName = "Store B",
            paymentMethod = PaymentMethod.UPI,
            category = "Shopping",
            transactionDate = System.currentTimeMillis(),
            transactionStatus = TransactionStatus.PENDING,
            transactionSource = TransactionSource.MANUAL
        )

        transactionDao.insertTransactions(listOf(txSuccess, txPending))

        val totalSpending = transactionDao.getTotalSpendingByUserFlow("user_spend").first()
        assertEquals(15000L, totalSpending)
    }
}

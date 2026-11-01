package com.upisegregator.app.data.repository

import com.upisegregator.app.data.local.dao.ManualExpenseDao
import com.upisegregator.app.data.local.entity.ManualExpenseEntity
import com.upisegregator.app.data.local.entity.PaymentMethod
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Calendar
import java.util.UUID

class ManualExpenseRepositoryTest {

    private lateinit var fakeDao: FakeManualExpenseDao
    private lateinit var repository: ManualExpenseRepository

    private class FakeManualExpenseDao : ManualExpenseDao {
        val expenses = mutableListOf<ManualExpenseEntity>()

        override suspend fun insertExpense(expense: ManualExpenseEntity) {
            expenses.removeAll { it.id == expense.id }
            expenses.add(expense)
        }

        override suspend fun insertExpenses(expenses: List<ManualExpenseEntity>) {
            this.expenses.addAll(expenses)
        }

        override suspend fun updateExpense(expense: ManualExpenseEntity) {
            val index = expenses.indexOfFirst { it.id == expense.id }
            if (index != -1) {
                expenses[index] = expense
            }
        }

        override suspend fun deleteExpense(expense: ManualExpenseEntity) {
            expenses.removeAll { it.id == expense.id }
        }

        override suspend fun deleteExpenseById(id: String, userId: String) {
            expenses.removeAll { it.id == id && it.userId == userId }
        }

        override suspend fun getExpenseById(id: String, userId: String): ManualExpenseEntity? {
            return expenses.find { it.id == id && it.userId == userId }
        }

        override fun getExpensesByUserFlow(userId: String): Flow<List<ManualExpenseEntity>> {
            return flowOf(expenses.filter { it.userId == userId }.sortedByDescending { it.expenseDate })
        }

        override fun getTotalSpendingByUserFlow(userId: String): Flow<Long> {
            val sum = expenses.filter { it.userId == userId }.sumOf { it.amountPaise }
            return flowOf(sum)
        }

        override fun getSpendingBetweenByUserFlow(
            userId: String,
            startTime: Long,
            endTime: Long
        ): Flow<Long> {
            val sum = expenses
                .filter { it.userId == userId && it.expenseDate in startTime..endTime }
                .sumOf { it.amountPaise }
            return flowOf(sum)
        }

        override suspend fun deleteExpensesByUser(userId: String) {
            expenses.removeAll { it.userId == userId }
        }
    }

    @Before
    fun setUp() {
        fakeDao = FakeManualExpenseDao()
        repository = ManualExpenseRepositoryImpl(fakeDao)
    }

    @Test
    fun insertExpense_savesCorrectlyWithSeparateDates() = runBlocking {
        val now = System.currentTimeMillis()
        val expenseDate = now - 86400000L // Yesterday
        val expenseId = UUID.randomUUID().toString()

        val expense = ManualExpenseEntity(
            id = expenseId,
            userId = "user_100",
            amountPaise = 25000L, // Rs 250.00
            recipientName = "Local Grocery Store",
            category = "Groceries",
            description = "Milk and snacks",
            paymentMethod = PaymentMethod.CASH,
            expenseDate = expenseDate,
            createdAt = now
        )

        repository.insertExpense(expense)

        val saved = repository.getExpenseById(expenseId, "user_100")
        assertNotNull(saved)
        assertEquals(25000L, saved?.amountPaise)
        assertEquals("Local Grocery Store", saved?.recipientName)
        assertEquals("Groceries", saved?.category)
        assertEquals(PaymentMethod.CASH, saved?.paymentMethod)
        assertEquals(expenseDate, saved?.expenseDate)
        assertEquals(now, saved?.createdAt)
        // Verify expenseDate and createdAt are distinct
        assertTrue(saved?.expenseDate != saved?.createdAt)
    }

    @Test
    fun updateExpense_modifiesExistingRecord() = runBlocking {
        val expenseId = UUID.randomUUID().toString()
        val expense = ManualExpenseEntity(
            id = expenseId,
            userId = "user_100",
            amountPaise = 15000L,
            recipientName = "Cafe Coffee",
            category = "Food",
            paymentMethod = PaymentMethod.CASH,
            expenseDate = System.currentTimeMillis()
        )
        repository.insertExpense(expense)

        val saved = repository.getExpenseById(expenseId, "user_100")!!
        val updated = saved.copy(
            amountPaise = 20000L,
            recipientName = "Cafe Coffee Day",
            description = "Cold brew with cake"
        )
        repository.updateExpense(updated)

        val result = repository.getExpenseById(expenseId, "user_100")
        assertNotNull(result)
        assertEquals(20000L, result?.amountPaise)
        assertEquals("Cafe Coffee Day", result?.recipientName)
        assertEquals("Cold brew with cake", result?.description)
    }

    @Test
    fun deleteExpense_removesRecord() = runBlocking {
        val expenseId = UUID.randomUUID().toString()
        val expense = ManualExpenseEntity(
            id = expenseId,
            userId = "user_100",
            amountPaise = 5000L,
            recipientName = "Stationery Shop",
            category = "Education",
            paymentMethod = PaymentMethod.CASH,
            expenseDate = System.currentTimeMillis()
        )
        repository.insertExpense(expense)
        assertNotNull(repository.getExpenseById(expenseId, "user_100"))

        repository.deleteExpenseById(expenseId, "user_100")
        assertNull(repository.getExpenseById(expenseId, "user_100"))
    }

    @Test
    fun userIsolation_onlyReturnsExpensesForSpecificUser() = runBlocking {
        repository.insertExpense(
            ManualExpenseEntity(
                id = "id_1",
                userId = "user_1",
                amountPaise = 10000L,
                recipientName = "User 1 Shop",
                category = "Food",
                paymentMethod = PaymentMethod.CASH,
                expenseDate = System.currentTimeMillis()
            )
        )
        repository.insertExpense(
            ManualExpenseEntity(
                id = "id_2",
                userId = "user_2",
                amountPaise = 20000L,
                recipientName = "User 2 Shop",
                category = "Shopping",
                paymentMethod = PaymentMethod.OTHER,
                expenseDate = System.currentTimeMillis()
            )
        )

        val user1Expenses = repository.getExpensesByUserFlow("user_1").first()
        val user2Expenses = repository.getExpensesByUserFlow("user_2").first()

        assertEquals(1, user1Expenses.size)
        assertEquals("User 1 Shop", user1Expenses[0].recipientName)

        assertEquals(1, user2Expenses.size)
        assertEquals("User 2 Shop", user2Expenses[0].recipientName)

        assertEquals(10000L, repository.getTotalSpendingByUserFlow("user_1").first())
        assertEquals(20000L, repository.getTotalSpendingByUserFlow("user_2").first())
    }

    @Test
    fun dailyAndMonthlySpending_correctlyCalculatesTotals() = runBlocking {
        val now = System.currentTimeMillis()
        val (dayStart, dayEnd) = ManualExpenseRepositoryImpl.getDayRange(now)
        val (monthStart, monthEnd) = ManualExpenseRepositoryImpl.getMonthRange(now)

        // Today's expense
        val todayTime = (dayStart + dayEnd) / 2
        repository.insertExpense(
            ManualExpenseEntity(
                id = "today_1",
                userId = "user_10",
                amountPaise = 5000L,
                recipientName = "Today Expense",
                category = "Food",
                paymentMethod = PaymentMethod.CASH,
                expenseDate = todayTime
            )
        )

        val cal = Calendar.getInstance().apply { timeInMillis = dayStart }
        cal.add(Calendar.DAY_OF_MONTH, -2)
        val previousDay = cal.timeInMillis

        val calMonth = Calendar.getInstance().apply { timeInMillis = monthStart }
        val prevCal = Calendar.getInstance().apply { timeInMillis = previousDay }

        if (calMonth.get(Calendar.MONTH) == prevCal.get(Calendar.MONTH)) {
            repository.insertExpense(
                ManualExpenseEntity(
                    id = "month_1",
                    userId = "user_10",
                    amountPaise = 7000L,
                    recipientName = "Earlier This Month",
                    category = "Transport",
                    paymentMethod = PaymentMethod.CASH,
                    expenseDate = previousDay
                )
            )

            val todayTotal = repository.getDailySpendingByUserFlow("user_10", now).first()
            val monthTotal = repository.getMonthlySpendingByUserFlow("user_10", now).first()

            assertEquals(5000L, todayTotal)
            assertEquals(12000L, monthTotal)
        } else {
            val todayTotal = repository.getDailySpendingByUserFlow("user_10", now).first()
            assertEquals(5000L, todayTotal)
        }
    }
}

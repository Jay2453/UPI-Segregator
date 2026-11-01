package com.upisegregator.app.data.repository

import com.upisegregator.app.data.local.dao.ManualExpenseDao
import com.upisegregator.app.data.local.entity.ManualExpenseEntity
import kotlinx.coroutines.flow.Flow
import java.util.Calendar

/**
 * Repository interface for managing manual expenses.
 */
interface ManualExpenseRepository {
    fun getExpensesByUserFlow(userId: String): Flow<List<ManualExpenseEntity>>
    suspend fun getExpenseById(id: String, userId: String): ManualExpenseEntity?
    suspend fun insertExpense(expense: ManualExpenseEntity)
    suspend fun updateExpense(expense: ManualExpenseEntity)
    suspend fun deleteExpense(expense: ManualExpenseEntity)
    suspend fun deleteExpenseById(id: String, userId: String)
    fun getTotalSpendingByUserFlow(userId: String): Flow<Long>
    fun getDailySpendingByUserFlow(userId: String, timestamp: Long = System.currentTimeMillis()): Flow<Long>
    fun getMonthlySpendingByUserFlow(userId: String, timestamp: Long = System.currentTimeMillis()): Flow<Long>
    suspend fun deleteExpensesByUser(userId: String)
}

/**
 * Production implementation of [ManualExpenseRepository].
 */
class ManualExpenseRepositoryImpl(
    private val dao: ManualExpenseDao
) : ManualExpenseRepository {

    override fun getExpensesByUserFlow(userId: String): Flow<List<ManualExpenseEntity>> {
        return dao.getExpensesByUserFlow(userId)
    }

    override suspend fun getExpenseById(id: String, userId: String): ManualExpenseEntity? {
        return dao.getExpenseById(id, userId)
    }

    override suspend fun insertExpense(expense: ManualExpenseEntity) {
        dao.insertExpense(expense)
    }

    override suspend fun updateExpense(expense: ManualExpenseEntity) {
        dao.updateExpense(expense)
    }

    override suspend fun deleteExpense(expense: ManualExpenseEntity) {
        dao.deleteExpense(expense)
    }

    override suspend fun deleteExpenseById(id: String, userId: String) {
        dao.deleteExpenseById(id, userId)
    }

    override fun getTotalSpendingByUserFlow(userId: String): Flow<Long> {
        return dao.getTotalSpendingByUserFlow(userId)
    }

    override fun getDailySpendingByUserFlow(userId: String, timestamp: Long): Flow<Long> {
        val (startOfDay, endOfDay) = getDayRange(timestamp)
        return dao.getSpendingBetweenByUserFlow(userId, startOfDay, endOfDay)
    }

    override fun getMonthlySpendingByUserFlow(userId: String, timestamp: Long): Flow<Long> {
        val (startOfMonth, endOfMonth) = getMonthRange(timestamp)
        return dao.getSpendingBetweenByUserFlow(userId, startOfMonth, endOfMonth)
    }

    override suspend fun deleteExpensesByUser(userId: String) {
        dao.deleteExpensesByUser(userId)
    }

    companion object {
        fun getDayRange(timestamp: Long): Pair<Long, Long> {
            val cal = Calendar.getInstance().apply {
                timeInMillis = timestamp
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val start = cal.timeInMillis
            cal.set(Calendar.HOUR_OF_DAY, 23)
            cal.set(Calendar.MINUTE, 59)
            cal.set(Calendar.SECOND, 59)
            cal.set(Calendar.MILLISECOND, 999)
            val end = cal.timeInMillis
            return Pair(start, end)
        }

        fun getMonthRange(timestamp: Long): Pair<Long, Long> {
            val cal = Calendar.getInstance().apply {
                timeInMillis = timestamp
                set(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val start = cal.timeInMillis
            cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
            cal.set(Calendar.HOUR_OF_DAY, 23)
            cal.set(Calendar.MINUTE, 59)
            cal.set(Calendar.SECOND, 59)
            cal.set(Calendar.MILLISECOND, 999)
            val end = cal.timeInMillis
            return Pair(start, end)
        }
    }
}

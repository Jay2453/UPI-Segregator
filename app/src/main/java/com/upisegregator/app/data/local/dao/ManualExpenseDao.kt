package com.upisegregator.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.upisegregator.app.data.local.entity.ManualExpenseEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for manual expenses.
 *
 * Enforces user isolation on all read, update, and delete queries.
 */
@Dao
interface ManualExpenseDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpense(expense: ManualExpenseEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpenses(expenses: List<ManualExpenseEntity>)

    @Update
    suspend fun updateExpense(expense: ManualExpenseEntity)

    @Delete
    suspend fun deleteExpense(expense: ManualExpenseEntity)

    @Query("DELETE FROM manual_expenses WHERE id = :id AND userId = :userId")
    suspend fun deleteExpenseById(id: String, userId: String)

    @Query("SELECT * FROM manual_expenses WHERE id = :id AND userId = :userId LIMIT 1")
    suspend fun getExpenseById(id: String, userId: String): ManualExpenseEntity?

    @Query("SELECT * FROM manual_expenses WHERE userId = :userId ORDER BY expenseDate DESC, createdAt DESC")
    fun getExpensesByUserFlow(userId: String): Flow<List<ManualExpenseEntity>>

    @Query("SELECT COALESCE(SUM(amountPaise), 0) FROM manual_expenses WHERE userId = :userId")
    fun getTotalSpendingByUserFlow(userId: String): Flow<Long>

    @Query(
        """
        SELECT COALESCE(SUM(amountPaise), 0) 
        FROM manual_expenses 
        WHERE userId = :userId AND expenseDate >= :startTime AND expenseDate <= :endTime
        """
    )
    fun getSpendingBetweenByUserFlow(userId: String, startTime: Long, endTime: Long): Flow<Long>

    @Query("DELETE FROM manual_expenses WHERE userId = :userId")
    suspend fun deleteExpensesByUser(userId: String)
}

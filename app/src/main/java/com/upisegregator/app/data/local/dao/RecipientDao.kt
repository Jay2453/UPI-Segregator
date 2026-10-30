package com.upisegregator.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.upisegregator.app.data.local.entity.RecipientEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecipientDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecipient(recipient: RecipientEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecipients(recipients: List<RecipientEntity>)

    @Update
    suspend fun updateRecipient(recipient: RecipientEntity)

    @Delete
    suspend fun deleteRecipient(recipient: RecipientEntity)

    @Query("SELECT * FROM recipients WHERE userId = :userId ORDER BY recipientName ASC")
    fun getRecipientsByUserFlow(userId: String): Flow<List<RecipientEntity>>

    @Query("SELECT * FROM recipients WHERE recipientId = :recipientId AND userId = :userId LIMIT 1")
    suspend fun getRecipientById(recipientId: String, userId: String): RecipientEntity?

    @Query("SELECT COUNT(*) FROM recipients WHERE userId = :userId")
    suspend fun getRecipientCountByUser(userId: String): Int

    @Query("DELETE FROM recipients WHERE userId = :userId")
    suspend fun deleteRecipientsByUser(userId: String)

    // Legacy helpers
    @Query("SELECT * FROM recipients ORDER BY recipientName ASC")
    fun getAllRecipientsFlow(): Flow<List<RecipientEntity>>

    @Query("SELECT * FROM recipients WHERE recipientId = :recipientId LIMIT 1")
    suspend fun getRecipientById(recipientId: String): RecipientEntity?

    @Query("SELECT COUNT(*) FROM recipients")
    suspend fun getRecipientCount(): Int

    @Query("DELETE FROM recipients")
    suspend fun deleteAllRecipients()
}

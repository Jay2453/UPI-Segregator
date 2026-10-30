package com.upisegregator.app.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.upisegregator.app.data.local.converters.Converters
import com.upisegregator.app.data.local.dao.CategoryDao
import com.upisegregator.app.data.local.dao.RecipientDao
import com.upisegregator.app.data.local.dao.TransactionDao
import com.upisegregator.app.data.local.entity.CategoryEntity
import com.upisegregator.app.data.local.entity.RecipientEntity
import com.upisegregator.app.data.local.entity.TransactionEntity

@Database(
    entities = [
        TransactionEntity::class,
        RecipientEntity::class,
        CategoryEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun transactionDao(): TransactionDao
    abstract fun recipientDao(): RecipientDao
    abstract fun categoryDao(): CategoryDao

    companion object {
        private const val DATABASE_NAME = "upi_segregator.db"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}

package com.upisegregator.app.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.upisegregator.app.data.local.converters.Converters
import com.upisegregator.app.data.local.dao.CategoryDao
import com.upisegregator.app.data.local.dao.ManualExpenseDao
import com.upisegregator.app.data.local.dao.RecipientDao
import com.upisegregator.app.data.local.dao.TransactionDao
import com.upisegregator.app.data.local.dao.UserDao
import com.upisegregator.app.data.local.entity.CategoryEntity
import com.upisegregator.app.data.local.entity.ManualExpenseEntity
import com.upisegregator.app.data.local.entity.RecipientEntity
import com.upisegregator.app.data.local.entity.TransactionEntity
import com.upisegregator.app.data.local.entity.UserEntity

@Database(
    entities = [
        UserEntity::class,
        TransactionEntity::class,
        RecipientEntity::class,
        CategoryEntity::class,
        ManualExpenseEntity::class
    ],
    version = 4,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun userDao(): UserDao
    abstract fun transactionDao(): TransactionDao
    abstract fun recipientDao(): RecipientDao
    abstract fun categoryDao(): CategoryDao
    abstract fun manualExpenseDao(): ManualExpenseDao

    companion object {
        private const val DATABASE_NAME = "upi_segregator.db"

        /**
         * Migration from Schema 1 (Milestone 2) to Schema 2 (Milestone 3).
         * - Creates `users` table for local authentication profiles.
         * - Adds `userId` column to `transactions` (defaulting to 'demo_user' to preserve existing records).
         * - Adds `userId` column to `recipients` (defaulting to 'demo_user' to preserve existing records).
         * - Creates indexes for high-performance userId filtering.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. Create users table
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `users` (
                        `userId` TEXT NOT NULL,
                        `fullName` TEXT NOT NULL,
                        `email` TEXT NOT NULL,
                        `passwordHash` TEXT NOT NULL,
                        `passwordSalt` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        PRIMARY KEY(`userId`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_users_email` ON `users` (`email`)")

                // 2. Add userId to transactions and index
                db.execSQL("ALTER TABLE `transactions` ADD COLUMN `userId` TEXT NOT NULL DEFAULT 'demo_user'")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_userId` ON `transactions` (`userId`)")

                // 3. Add userId to recipients and index
                db.execSQL("ALTER TABLE `recipients` ADD COLUMN `userId` TEXT NOT NULL DEFAULT 'demo_user'")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_recipients_userId` ON `recipients` (`userId`)")
            }
        }

        /**
         * Migration from Schema 2 (Milestone 3) to Schema 3 (Milestone 5 - Manual Expense Logging).
         * - Creates `manual_expenses` table for recording cash and offline spending.
         * - Creates index on `userId` for user isolation.
         * - Creates index on `expenseDate` for high-performance date-range calculations.
         * - Preserves all existing tables and data (transactions, users, recipients, categories).
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `manual_expenses` (
                        `id` TEXT NOT NULL,
                        `amountPaise` INTEGER NOT NULL,
                        `recipientName` TEXT NOT NULL,
                        `category` TEXT NOT NULL,
                        `description` TEXT NOT NULL,
                        `paymentMethod` TEXT NOT NULL,
                        `expenseDate` INTEGER NOT NULL,
                        `userId` TEXT NOT NULL DEFAULT 'demo_user',
                        `createdAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_manual_expenses_userId` ON `manual_expenses` (`userId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_manual_expenses_expenseDate` ON `manual_expenses` (`expenseDate`)")
            }
        }

        /**
         * Migration from Schema 3 (Milestone 5) to Schema 4 (Milestone 6 - Transaction Direction & Concurrency Safety).
         * - Adds `direction` column (TEXT, NOT NULL, DEFAULT 'DEBIT') to `transactions`.
         * - Creates index on `direction`.
         * - Backfills existing `[CREDIT]` records as CREDIT and SUCCESS, preserving note contents.
         * - Preserves all other PENDING and DEBIT records untouched.
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. Add direction column with default 'DEBIT'
                db.execSQL("ALTER TABLE `transactions` ADD COLUMN `direction` TEXT NOT NULL DEFAULT 'DEBIT'")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_direction` ON `transactions` (`direction`)")

                // 2. Backfill existing [CREDIT] records as CREDIT and SUCCESS
                db.execSQL(
                    """
                    UPDATE `transactions`
                    SET `direction` = 'CREDIT',
                        `transactionStatus` = 'SUCCESS'
                    WHERE `note` LIKE '[CREDIT]%'
                    """.trimIndent()
                )
            }
        }

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .fallbackToDestructiveMigrationOnDowngrade()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}

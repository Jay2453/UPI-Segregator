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
import com.upisegregator.app.data.local.dao.RecipientDao
import com.upisegregator.app.data.local.dao.TransactionDao
import com.upisegregator.app.data.local.dao.UserDao
import com.upisegregator.app.data.local.entity.CategoryEntity
import com.upisegregator.app.data.local.entity.RecipientEntity
import com.upisegregator.app.data.local.entity.TransactionEntity
import com.upisegregator.app.data.local.entity.UserEntity

@Database(
    entities = [
        UserEntity::class,
        TransactionEntity::class,
        RecipientEntity::class,
        CategoryEntity::class
    ],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun userDao(): UserDao
    abstract fun transactionDao(): TransactionDao
    abstract fun recipientDao(): RecipientDao
    abstract fun categoryDao(): CategoryDao

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

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME
                )
                    .addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigrationOnDowngrade()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}

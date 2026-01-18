package com.example.finwise_lab.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.finwise_lab.CurrencyDetails
import com.example.finwise_lab.database.TransactionEntity
import com.example.finwise_lab.database.BudgetEntity
import com.example.finwise_lab.database.BudgetDao
import com.example.finwise_lab.database.CurrencyDao
import com.example.finwise_lab.database.TransactionDao
import kotlin.jvm.Volatile

@Database(entities = [CurrencyDetails::class, TransactionEntity::class, BudgetEntity::class], version = 3, exportSchema = false)
abstract class CurrencyDatabase : RoomDatabase() {
    abstract fun currencyDao(): CurrencyDao
    abstract fun transactionDao(): TransactionDao
    abstract fun budgetDao(): BudgetDao

    companion object {
        @Volatile
        private var INSTANCE: CurrencyDatabase? = null

        fun getDatabase(context: Context): CurrencyDatabase {
            return INSTANCE ?: synchronized(this) {

                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    CurrencyDatabase::class.java,
                    "currency_database"
                )
                .fallbackToDestructiveMigration()
                .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

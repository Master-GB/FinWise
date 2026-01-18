package com.example.finwise_lab.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.finwise_lab.database.BudgetEntity

@Dao
interface BudgetDao {
    @Query("SELECT monthlyBudget FROM budget WHERE id = 0")
    suspend fun getBudget(): Double?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBudget(budget: BudgetEntity)
}

package com.example.finwise_lab.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "budget")
 data class BudgetEntity(
    @PrimaryKey val id: Int = 0,
    val monthlyBudget: Double
)

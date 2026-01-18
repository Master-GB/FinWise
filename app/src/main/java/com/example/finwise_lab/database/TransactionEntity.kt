package com.example.finwise_lab.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.finwise_lab.TransactionType
import java.util.*

@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey val id: String,
    val title: String,
    val amount: Double,
    val date: Long,
    val type: String,
    val category: String
)

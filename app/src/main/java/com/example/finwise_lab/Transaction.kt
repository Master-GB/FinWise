package com.example.finwise_lab

import java.util.Date

data class Transaction(
    val id: String = "",
    val title: String,
    val amount: Double,
    val category: String,
    val date: Date,
    val type: TransactionType
)

enum class TransactionType {
    INCOME,
    EXPENSE
} 
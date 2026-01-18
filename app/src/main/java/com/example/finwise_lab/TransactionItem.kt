package com.example.finwise_lab

import java.util.Date

sealed class TransactionItem {
    data class MonthHeader(val date: Date) : TransactionItem()
    data class Transaction(val transaction: com.example.finwise_lab.Transaction) : TransactionItem()
} 
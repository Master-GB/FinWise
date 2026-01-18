package com.example.finwise_lab

data class Category(
    val name: String,
    val iconResourceId: Int,
    val type: CategoryType
)

enum class CategoryType {
    INCOME, EXPENSE
} 
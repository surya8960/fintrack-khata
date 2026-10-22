package com.example.fintrack.data.model

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
enum class TransactionType(val label: String) {
    INCOME("Income"),
    EXPENSE("Expense"),
    TRANSFER("Transfer")
}

@Serializable
data class Transaction(
    val id: String = UUID.randomUUID().toString(),
    val accountId: String,
    val targetAccountId: String? = null,
    val title: String,
    val amount: Double,
    val type: TransactionType,
    val category: String,
    val note: String = "",
    val dateTimestamp: Long = System.currentTimeMillis()
)

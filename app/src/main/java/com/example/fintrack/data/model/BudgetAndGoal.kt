package com.example.fintrack.data.model

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class Budget(
    val id: String = UUID.randomUUID().toString(),
    val category: String,
    val monthlyLimit: Double,
    val monthYear: String = "" // e.g. "2026-10"
)

@Serializable
data class Goal(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val targetAmount: Double,
    val currentAmount: Double = 0.0,
    val deadlineDays: Int = 90,
    val colorHex: String = "#4F46E5"
) {
    val progress: Float
        get() = if (targetAmount > 0) (currentAmount / targetAmount).toFloat().coerceIn(0f, 1f) else 0f
}

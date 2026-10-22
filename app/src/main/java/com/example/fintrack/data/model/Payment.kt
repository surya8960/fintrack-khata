package com.example.fintrack.data.model

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class Payment(
    val id: String = UUID.randomUUID().toString(),
    val customerId: String,
    val paymentType: PaymentType,
    val amount: Double,
    val paymentDate: Long = System.currentTimeMillis(),
    val paymentMode: String = "CASH", // CASH, UPI, BANK
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

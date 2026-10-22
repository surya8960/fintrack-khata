package com.example.fintrack.data.model

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class InterestAccrual(
    val id: String = UUID.randomUUID().toString(),
    val customerId: String,
    val startDate: Long, // Period start timestamp ms
    val endDate: Long, // Period end timestamp ms
    val interestRate: Double, // Monthly % applied
    val principalBasis: Double, // Fixed principal amount used for simple interest
    val completedMonths: Int, // Number of whole monthly periods
    val partialDays: Int, // Remaining days in incomplete month
    val daysInPartialMonth: Int, // Total days in the partial month
    val monthlyInterestAmount: Double, // Principal * rate / 100
    val calculatedInterest: Double, // Total simple interest for this period
    val calculationBreakdown: String, // Human-readable explanation of the math
    val createdAt: Long = System.currentTimeMillis()
)

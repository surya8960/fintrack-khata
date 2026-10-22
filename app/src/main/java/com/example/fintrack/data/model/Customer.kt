package com.example.fintrack.data.model

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class Customer(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val phone: String = "",
    val address: String = "",
    val principalAmount: Double, // Initial / fixed loan principal
    val monthlyInterestRate: Double = 5.0, // Default 5%, editable per borrower/loan
    val loanStartDate: Long, // Epoch timestamp in ms
    val dueDate: Long? = null,
    val totalAccruedInterest: Double = 0.0,
    val totalInterestPaid: Double = 0.0,
    val totalPrincipalRepaid: Double = 0.0,
    val lastCalculationDate: Long? = null,
    val status: String = "ACTIVE",
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis()
) {
    // Current outstanding principal (Principal only reduces with Principal Repayment)
    val remainingPrincipal: Double
        get() = (principalAmount - totalPrincipalRepaid).coerceAtLeast(0.0)

    // Current outstanding interest (Interest only reduces with Interest Payment)
    val outstandingInterest: Double
        get() = (totalAccruedInterest - totalInterestPaid).coerceAtLeast(0.0)

    // Total due is remaining principal + outstanding interest
    val totalDue: Double
        get() = remainingPrincipal + outstandingInterest

    // Monthly interest amount on the principal
    val monthlyInterestAmount: Double
        get() = (remainingPrincipal * monthlyInterestRate) / 100.0
}

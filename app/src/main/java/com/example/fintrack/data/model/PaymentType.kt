package com.example.fintrack.data.model

import kotlinx.serialization.Serializable

@Serializable
enum class PaymentType(val title: String, val description: String) {
    INTEREST_PAYMENT("Interest Payment", "Reduces accrued interest only"),
    PRINCIPAL_REPAYMENT("Principal Repayment", "Reduces loan principal only")
}
